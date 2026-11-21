package forgepreheat.core;

/**
 * Base for features that act during a whole loading phase, such as client startup, on any thread: mod loading and
 * resource reloading run on worker threads, so a single owner thread doesn't fit. Thread safety is up to the subclass.
 */
public abstract class PhaseFeature extends ToggledFeature {
    private volatile boolean active;

    protected PhaseFeature(String name, String toggleKey, boolean enabledByDefault) {
        super(name, toggleKey, enabledByDefault);
    }

    @Override
    protected final void open() {
        active = true;
    }

    @Override
    protected final void close() {
        active = false;
    }

    protected final boolean isActive() {
        return active;
    }
}
