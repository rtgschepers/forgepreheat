package forgepreheat.optimize;

import com.mojang.logging.LogUtils;
import forgepreheat.core.FeatureToggle;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * With JEI starting on a worker, its ingredient registration builds every creative tab's contents while the player can
 * already open the creative inventory, which rebuilds the same tabs on the main thread (seen overlapping in a 4.5 s
 * first-open hitch). Two threads filling the same tabs, and running mods' tab event handlers at once, can corrupt both
 * results. This lets only one of them build at a time. The main thread waits at most 30 s, then builds anyway, so a
 * worker that somehow waits on the main thread can't freeze the game.
 */
public final class CreativeTabBuildLock {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = FeatureToggle.isEnabled("creativeTabLock", true);
    private static final long MAIN_WAIT_SECONDS = 30;
    private static final long LOG_WAIT_NANOS = 50_000_000L;
    private static final ReentrantLock LOCK = new ReentrantLock();

    private CreativeTabBuildLock() {}

    /** JEI's side: builds its ingredient list's tabs while holding the lock. */
    public static void runLocked(Runnable build) {
        if (!ENABLED) {
            build.run();
            return;
        }
        LOCK.lock();
        try {
            build.run();
        } finally {
            LOCK.unlock();
        }
    }

    /** The creative screen's side: rebuilds the tabs while holding the lock. */
    public static boolean rebuildLocked(BooleanSupplier rebuild) {
        if (!ENABLED) {
            return rebuild.getAsBoolean();
        }
        long start = System.nanoTime();
        boolean locked;
        try {
            locked = LOCK.tryLock(MAIN_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            locked = false;
        }
        long waited = System.nanoTime() - start;
        if (!locked) {
            LOGGER.warn("[ForgePreheat] Creative tabs still being built by JEI after {} s; building them anyway",
                    MAIN_WAIT_SECONDS);
        } else if (waited > LOG_WAIT_NANOS) {
            LOGGER.info("[ForgePreheat] Creative inventory waited {} s for JEI's creative tab build",
                    String.format("%.2f", waited / 1e9));
        }
        try {
            return rebuild.getAsBoolean();
        } finally {
            if (locked) {
                LOCK.unlock();
            }
        }
    }
}
