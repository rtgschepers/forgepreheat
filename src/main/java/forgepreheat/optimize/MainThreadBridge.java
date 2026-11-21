package forgepreheat.optimize;

import forgepreheat.core.StartupScope;
import net.minecraft.client.Minecraft;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Runs pieces of an async JEI start on the main thread while the worker waits, and can hold the main thread so the
 * last steps of the start run back to back without frames in between, as they would in a synchronous start.
 * Only the worker calls this class, so holding and releasing never race. The main thread never waits on the worker
 * except inside a hold, which the worker always ends.
 */
final class MainThreadBridge {
    private static final Runnable RELEASE = () -> {};

    private final BlockingQueue<Runnable> heldTasks = new LinkedBlockingQueue<>();
    private boolean holding;
    private volatile long mainThreadNanos;
    private int tasks;

    /** Runs the task on the main thread and waits for it. */
    void run(Runnable task) {
        if (holding) {
            await(enqueue(task));
            return;
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> runAsOwner(task, done));
        await(done);
    }

    /** Runs the task on the main thread, then keeps the main thread for later tasks until {@link #release()}. */
    void runAndHold(Runnable task) {
        if (holding) {
            await(enqueue(task));
            return;
        }
        holding = true;
        CompletableFuture<Void> first = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            runAsOwner(task, first);
            serveHeldTasks();
        });
        await(first);
    }

    boolean isHolding() {
        return holding;
    }

    void release() {
        if (holding) {
            holding = false;
            heldTasks.add(RELEASE);
        }
    }

    double mainThreadSeconds() {
        return mainThreadNanos / 1e9;
    }

    int tasks() {
        return tasks;
    }

    private CompletableFuture<Void> enqueue(Runnable task) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        heldTasks.add(() -> runAsOwner(task, done));
        return done;
    }

    private void serveHeldTasks() {
        try {
            for (Runnable task = heldTasks.take(); task != RELEASE; task = heldTasks.take()) {
                task.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // The worker is blocked meanwhile, so handing the scope to this thread never lets two threads see it.
    private void runAsOwner(Runnable task, CompletableFuture<Void> done) {
        tasks++;
        Thread previous = StartupScope.owner();
        StartupScope.setOwner(Thread.currentThread());
        long start = System.nanoTime();
        try {
            task.run();
            done.complete(null);
        } catch (Throwable t) {
            done.completeExceptionally(t);
        } finally {
            mainThreadNanos += System.nanoTime() - start;
            StartupScope.setOwner(previous);
        }
    }

    private static void await(CompletableFuture<Void> done) {
        try {
            done.join();
        } catch (CompletionException e) {
            // Rethrows the step's own exception so JEI's per-plugin error handling and logs see it unchanged.
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
