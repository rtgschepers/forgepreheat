package forgepreheat.core;

/** Limits a feature to the thread running JEI's start, so other threads and later JEI calls run the original code. */
public final class StartupScope {
    private Thread owner;

    public void open() {
        owner = Thread.currentThread();
    }

    public void close() {
        owner = null;
    }

    public boolean isActive() {
        return owner != null && Thread.currentThread() == owner;
    }
}
