package forgepreheat.optimize;

import com.mojang.logging.LogUtils;
import forgepreheat.core.FeatureToggle;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Just Enough Resources' category registration first builds its own loot data manager: it reloads every mod's server-data
 * loot tables and waits for that (~2.9 s of JEI's start, 81% of JER's time there). The result is kept in a static field
 * for the session and doesn't depend on JEI, so this runs JER's own LootTableHelper.getLootDataManager() on a background
 * thread as soon as JEI's start is handed off, during the wait for the server's tags. JER's own call waits for that run
 * instead of starting a second one; if the run fails, JER's field is cleared so JER's call does the work itself.
 */
public final class JerLootPrefetch {
    public static final JerLootPrefetch INSTANCE = new JerLootPrefetch();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HELPER = "jeresources.util.LootTableHelper";

    private final boolean enabled = FeatureToggle.isEnabled("jerLootPrefetch", true);
    private volatile CompletableFuture<Void> running;
    private volatile Thread thread;
    private double waitedSeconds;

    private JerLootPrefetch() {}

    /** Called on the main thread when JEI's start is handed off. */
    public void start() {
        if (!enabled || running != null) {
            return;
        }
        Method load;
        try {
            load = Class.forName(HELPER).getMethod("getLootDataManager");
        } catch (ReflectiveOperationException | LinkageError e) {
            return; // JER not installed, or changed.
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        running = future;
        Thread worker = new Thread(() -> prefetch(load, future), "ForgePreheat JER loot prefetch");
        worker.setDaemon(true);
        thread = worker;
        worker.start();
    }

    /** Called at the head of JER's getLootDataManager: waits for a prefetch that is still loading. */
    public void awaitIfRunning() {
        CompletableFuture<Void> future = running;
        if (future == null || future.isDone() || Thread.currentThread() == thread) {
            return;
        }
        long start = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            // The prefetch needs the main thread to apply the loaded tables, so keep it running its tasks meanwhile.
            mc.managedBlock(future::isDone);
        } else {
            future.join();
        }
        waitedSeconds = (System.nanoTime() - start) / 1e9;
        LOGGER.info("[ForgePreheat] Just Enough Resources waited {} s for the loot table prefetch",
                String.format("%.2f", waitedSeconds));
    }

    private void prefetch(Method load, CompletableFuture<Void> future) {
        long start = System.nanoTime();
        AsyncJeiStart.INSTANCE.parkManagedWaits(Thread.currentThread(), true);
        try {
            load.invoke(null);
            LOGGER.info("[ForgePreheat] Prefetched Just Enough Resources' loot tables in {} s",
                    String.format("%.2f", (System.nanoTime() - start) / 1e9));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("[ForgePreheat] Prefetching Just Enough Resources' loot tables failed; JER will load them itself", e);
            clearJerField();
        } finally {
            AsyncJeiStart.INSTANCE.parkManagedWaits(Thread.currentThread(), false);
            future.complete(null);
        }
    }

    // JER sets its field before loading, so a failed load must not leave a half-built manager behind.
    private static void clearJerField() {
        try {
            Field field = Class.forName(HELPER).getDeclaredField("lootDataManager");
            field.setAccessible(true);
            field.set(null, null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[ForgePreheat] Could not reset JER's loot data manager", e);
        }
    }
}
