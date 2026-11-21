package forgepreheat.core;

/**
 * Limits a feature to the thread running JEI's start, so other threads and later JEI calls run the original code.
 * The owner is shared by all features and can move: an async start hands it to the main thread while the worker waits.
 */
public final class StartupScope {
    private static volatile Thread owner;

    private volatile boolean open;

    /** Sets the thread that counts as running JEI's start; null when no start is running. */
    public static void setOwner(Thread thread) {
        owner = thread;
    }

    public static Thread owner() {
        return owner;
    }

    public void open() {
        open = true;
    }

    public void close() {
        open = false;
    }

    public boolean isActive() {
        return open && Thread.currentThread() == owner;
    }
}
