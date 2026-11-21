package forgepreheat.core;

/**
 * Base for features that only act during JEI's start, on JEI's thread, and can be switched with a JVM argument.
 * Subclasses supply their state handling and statistics; the lifecycle and the toggle live here once.
 */
public abstract class ScopedFeature implements Feature {
    private final String name;
    private final String toggleKey;
    private final boolean enabled;
    private final StartupScope scope = new StartupScope();

    protected ScopedFeature(String name, String toggleKey, boolean enabledByDefault) {
        this.name = name;
        this.toggleKey = toggleKey;
        this.enabled = FeatureToggle.isEnabled(toggleKey, enabledByDefault);
    }

    @Override
    public final void begin() {
        onBegin();
        if (enabled) {
            scope.open();
        }
    }

    @Override
    public final void end() {
        scope.close();
        onEnd();
    }

    @Override
    public final String summary() {
        if (!enabled) {
            return name + ": off (enable with -D" + FeatureToggle.property(toggleKey) + "=true)";
        }
        return name + ": " + describeStats();
    }

    public final boolean isEnabled() {
        return enabled;
    }

    /** True only on JEI's thread while JEI is starting and the feature is enabled. */
    protected final boolean isActive() {
        return scope.isActive();
    }

    /** Resets statistics and state for a new JEI start. */
    protected abstract void onBegin();

    /** Drops state that must not outlive the JEI start, such as caches. */
    protected void onEnd() {}

    protected abstract String describeStats();
}
