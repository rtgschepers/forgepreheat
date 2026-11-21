package forgepreheat.optimize;

import com.mojang.logging.LogUtils;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.library.ingredients.IIngredientSupplier;
import mezz.jei.library.recipes.collect.RecipeMap;
import org.slf4j.Logger;

import java.util.ArrayDeque;

/**
 * Feeds one JEI RecipeMap's addRecipe calls to its own thread in the order JEI made them, so the map is built exactly as
 * JEI would build it while JEI's start goes on. Every read and the compact at the end of the start wait until the
 * thread has caught up, so nothing ever sees a half-built map.
 */
public final class PipelinedRecipeIndex {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_LOGGED_FAILURES = 5;

    private record Pending(RecipeType<?> type, Object recipe, IIngredientSupplier supplier) {}

    private final RecipeMap map;
    private final RecipeIngredientRole role;
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private final Thread consumer;
    private boolean busy;
    private volatile boolean closed;
    private long added, busyNanos, waitedNanos;
    private int failures;

    PipelinedRecipeIndex(RecipeMap map, RecipeIngredientRole role) {
        this.map = map;
        this.role = role;
        this.consumer = new Thread(this::consume, "ForgePreheat recipe index " + role);
        consumer.setDaemon(true);
        consumer.start();
    }

    /** True on this index's own thread, whose calls must reach JEI's code. */
    public boolean isConsumer() {
        return Thread.currentThread() == consumer;
    }

    synchronized void add(RecipeType<?> type, Object recipe, IIngredientSupplier supplier) {
        queue.addLast(new Pending(type, recipe, supplier));
        added++;
        notifyAll();
    }

    /** Waits until every call queued so far has reached the map. */
    public synchronized void awaitDrained() {
        long start = System.nanoTime();
        boolean interrupted = false;
        while (!queue.isEmpty() || busy) {
            try {
                wait();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        waitedNanos += System.nanoTime() - start;
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** No more calls will come; the thread ends once the queue is empty. */
    synchronized void close() {
        closed = true;
        notifyAll();
    }

    RecipeIngredientRole role() {
        return role;
    }

    synchronized long added() {
        return added;
    }

    synchronized double busySeconds() {
        return busyNanos / 1e9;
    }

    synchronized double waitedSeconds() {
        return waitedNanos / 1e9;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void consume() {
        while (true) {
            Pending next;
            synchronized (this) {
                // Leaving with entries still queued would make every awaitDrained() wait forever, so only "closed" ends it.
                while (queue.isEmpty() && !closed) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // Nothing here may be cut short; keep waiting.
                    }
                }
                if (queue.isEmpty()) {
                    return;
                }
                next = queue.pollFirst();
                busy = true;
            }
            long start = System.nanoTime();
            try {
                map.addRecipe((RecipeType) next.type(), next.recipe(), next.supplier());
            } catch (Throwable t) {
                // JEI drops such a recipe from its category, here it stays listed; the count shows whether that happens.
                int failure;
                synchronized (this) {
                    failure = failures++;
                }
                if (failure < MAX_LOGGED_FAILURES) {
                    LOGGER.warn("[ForgePreheat] {} recipe index entry failed for {}", role, next.type().getUid(), t);
                }
            } finally {
                synchronized (this) {
                    busy = false;
                    busyNanos += System.nanoTime() - start;
                    notifyAll();
                }
            }
        }
    }

    synchronized int failures() {
        return failures;
    }
}
