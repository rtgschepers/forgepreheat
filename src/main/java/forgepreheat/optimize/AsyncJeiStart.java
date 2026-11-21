package forgepreheat.optimize;

import com.mojang.logging.LogUtils;
import forgepreheat.core.FeatureToggle;
import forgepreheat.core.ScopedFeature;
import mezz.jei.api.IModPlugin;
import mezz.jei.common.Internal;
import mezz.jei.library.startup.JeiStarter;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * JEI starts on the main thread when a world is joined and holds it for ~50 s in this pack, which is the "Loading
 * terrain" wait. This runs JeiStarter.start on a worker thread instead, so the player is in the world while JEI loads.
 * JEI's GUI startup and the runtime-available callbacks still run on the main thread, back to back, and plugins listed
 * in -Dforgepreheat.asyncStartMainThreadPlugins (default kubejs:jei, whose scripts are not thread-safe) run every step
 * there. A stop or restart that arrives mid-start makes the worker skip the remaining plugins and then runs after it.
 * The worker starts only once the server's tags have arrived (they follow the recipes that trigger JEI), so it never
 * reads tags while the main thread rebinds them. A synchronous JEI start always runs before those tags, so its fuel
 * list misses the tag-based fuels (logs, planks, ...): ~1,050 fuel stacks instead of ~4,070 in this pack.
 * Plugins that wait with Minecraft.managedBlock (Just Enough Resources loading loot tables) would make the worker run
 * the main thread's task queue; the worker waits without draining it instead, and the main thread runs those tasks.
 * On by default since 0.8.0 (the user's call after testing); switch off with -Dforgepreheat.asyncStart=false. Every
 * plugin's registration code runs off the main thread, and the fuel list differs from a normal JEI start as described.
 */
public final class AsyncJeiStart extends ScopedFeature {
    public static final AsyncJeiStart INSTANCE = new AsyncJeiStart();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> MAIN_THREAD_STEPS = Set.of("Registering Runtime", "Sending Runtime");
    // A restart after a resource reload gets no tag packet; it starts after this wait instead.
    private static final long TAG_WAIT_LIMIT_NANOS = 5_000_000_000L;

    private final Set<String> mainThreadPlugins;
    private final ExecutorService worker;
    private final AtomicInteger pendingStarts = new AtomicInteger();
    private volatile Thread workerThread;
    private volatile boolean cancelled;
    private volatile boolean runningDeferredStop;
    private volatile MainThreadBridge bridge;
    private long workerStartedAt;
    private int pluginStepsOnMain;
    private int managedWaits;
    private final Set<Thread> parkingThreads = ConcurrentHashMap.newKeySet();
    // Main thread only: a start request parked until the server's tags arrive.
    private volatile JeiStarter waitingForTags;
    private long tagWaitStartedAt;
    private volatile double lastTagWaitSeconds;
    private volatile String lastStartReason = "";

    private AsyncJeiStart() {
        super("async JEI start", "asyncStart", true);
        String plugins = System.getProperty(FeatureToggle.property("asyncStartMainThreadPlugins"), "kubejs:jei");
        mainThreadPlugins = Arrays.stream(plugins.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "ForgePreheat JEI start");
            thread.setDaemon(true);
            workerThread = thread;
            return thread;
        });
        if (isEnabled()) {
            MinecraftForge.EVENT_BUS.addListener(this::onTagsUpdated);
            MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        }
    }

    @Override
    protected void onBegin() {
        workerStartedAt = System.nanoTime();
        pluginStepsOnMain = 0;
        managedWaits = 0;
    }

    /**
     * Called at the head of JeiStarter.start, outside any start scope. Returns true when the start was handed to the
     * worker and the caller must return.
     */
    public boolean dispatch(JeiStarter starter) {
        // Without synced recipes JEI rewrites the client's recipe manager, which belongs to the main thread.
        if (!isEnabled() || onWorker() || !Internal.hasClientRecipes()) {
            return false;
        }
        if (waitingForTags == null) {
            tagWaitStartedAt = System.nanoTime();
        }
        waitingForTags = starter;
        JerLootPrefetch.INSTANCE.start();
        return true;
    }

    private void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) {
            startWaiting("after the server's tags arrived");
        }
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && waitingForTags != null
                && System.nanoTime() - tagWaitStartedAt > TAG_WAIT_LIMIT_NANOS) {
            startWaiting("without a tag update (none arrived within 5 s)");
        }
    }

    private void startWaiting(String reason) {
        JeiStarter starter = waitingForTags;
        if (starter == null) {
            return;
        }
        waitingForTags = null;
        lastTagWaitSeconds = (System.nanoTime() - tagWaitStartedAt) / 1e9;
        lastStartReason = reason;
        pendingStarts.incrementAndGet();
        worker.execute(() -> startOnWorker(starter));
    }

    /** True from the moment JEI's start was handed off until it returned (waiting for tags included). */
    public boolean isLoading() {
        return waitingForTags != null || pendingStarts.get() > 0;
    }

    public boolean isWaitingForTags() {
        return waitingForTags != null;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Called at the head of JeiStarter.stop. Returns true when the stop was queued behind a running start. */
    public boolean deferStop(JeiStarter starter) {
        if (runningDeferredStop || onWorker()) {
            return false;
        }
        // A start still waiting for tags never ran, so JEI's own stop has nothing of it to undo.
        waitingForTags = null;
        if (pendingStarts.get() == 0) {
            return false;
        }
        cancelled = true;
        worker.execute(() -> stopOnMainThread(starter));
        LOGGER.info("[ForgePreheat] JEI stop requested while JEI is starting on the worker; it will run afterwards");
        return true;
    }

    /** Returns true when the step was run on the main thread and the caller must skip it. */
    public boolean runStepOnMainThread(String title, Runnable step) {
        if (!onWorker() || !MAIN_THREAD_STEPS.contains(title)) {
            return false;
        }
        // After a logout the GUI step fails on the missing level (seen in 0.7.1) and JEI builds the runtime without it.
        if (!cancelled) {
            bridge.runAndHold(step);
        }
        return true;
    }

    public Consumer<IModPlugin> wrapPluginCalls(Consumer<IModPlugin> call) {
        if (!onWorker()) {
            return call;
        }
        return plugin -> {
            // Inside a main-thread step the call runs where it is; handing it over again would wait on itself.
            if (!onWorker()) {
                call.accept(plugin);
                return;
            }
            if (cancelled) {
                return;
            }
            if (mainThreadPlugins.contains(String.valueOf(plugin.getPluginUid()))) {
                pluginStepsOnMain++;
                bridge.run(() -> call.accept(plugin));
            } else {
                call.accept(plugin);
            }
        };
    }

    /**
     * Called at the head of BlockableEventLoop.managedBlock, on any thread. Returns true when the wait was done here and
     * the caller must return: on the worker, managedBlock would otherwise run the main thread's queued tasks itself.
     */
    public boolean waitWithoutDraining(Object loop, BooleanSupplier done) {
        if (loop != Minecraft.getInstance()) {
            return false;
        }
        if (parkingThreads.contains(Thread.currentThread())) {
            while (!done.getAsBoolean()) {
                LockSupport.parkNanos(1_000_000L);
            }
            return true;
        }
        if (!onWorker()) {
            return false;
        }
        managedWaits++;
        MainThreadBridge current = bridge;
        if (current != null && current.isHolding()) {
            // The main thread is held by this start and can't drain its queue, so the wait has to run there.
            current.run(() -> Minecraft.getInstance().managedBlock(done));
        } else {
            while (!done.getAsBoolean()) {
                LockSupport.parkNanos(1_000_000L);
            }
        }
        return true;
    }

    /** Called when JeiStarter.start returns on the worker, before the report is written. */
    public void onStartReturned() {
        MainThreadBridge current = bridge;
        if (onWorker() && current != null) {
            current.release();
        }
    }

    private void startOnWorker(JeiStarter starter) {
        cancelled = false;
        MainThreadBridge current = new MainThreadBridge();
        bridge = current;
        try {
            starter.start();
        } catch (Throwable t) {
            LOGGER.error("[ForgePreheat] JEI start on the worker thread failed", t);
        } finally {
            current.release();
            pendingStarts.decrementAndGet();
        }
    }

    private void stopOnMainThread(JeiStarter starter) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            runningDeferredStop = true;
            try {
                starter.stop();
            } finally {
                runningDeferredStop = false;
                done.complete(null);
            }
        });
        done.join();
    }

    /** Our own background threads that wait on the main thread the same way the JEI worker does. */
    void parkManagedWaits(Thread thread, boolean park) {
        if (park) {
            parkingThreads.add(thread);
        } else {
            parkingThreads.remove(thread);
        }
    }

    private boolean onWorker() {
        return Thread.currentThread() == workerThread;
    }

    @Override
    protected String describeStats() {
        MainThreadBridge current = bridge;
        if (!onWorker() || current == null) {
            return "ran on the main thread";
        }
        return String.format("ran on a worker thread for %.2f s, started %s after %.2f s; main thread used %.2f s for "
                        + "%d tasks (%d plugin steps from %s); %d managedBlock waits kept off the main queue",
                (System.nanoTime() - workerStartedAt) / 1e9, lastStartReason, lastTagWaitSeconds,
                current.mainThreadSeconds(), current.tasks(), pluginStepsOnMain, mainThreadPlugins, managedWaits);
    }
}
