package forgepreheat.core;

/** Base for features that only act during JEI's start, on the thread that owns the start. */
public abstract class ScopedFeature extends ToggledFeature {
    private final StartupScope scope = new StartupScope();

    protected ScopedFeature(String name, String toggleKey, boolean enabledByDefault) {
        super(name, toggleKey, enabledByDefault);
    }

    @Override
    protected final void open() {
        scope.open();
    }

    @Override
    protected final void close() {
        scope.close();
    }

    /** True only on JEI's thread while JEI is starting and the feature is enabled. */
    protected final boolean isActive() {
        return scope.isActive();
    }
}
