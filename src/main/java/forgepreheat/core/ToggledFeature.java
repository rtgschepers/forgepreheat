package forgepreheat.core;

/**
 * The parts every feature shares: a name, a -Dforgepreheat.&lt;key&gt; switch and a one-line summary. Subclasses decide
 * when the feature counts as active (one thread during JEI's start, or a whole loading phase).
 */
public abstract class ToggledFeature implements Feature {
    private final String name;
    private final String toggleKey;
    private final boolean enabled;

    protected ToggledFeature(String name, String toggleKey, boolean enabledByDefault) {
        this.name = name;
        this.toggleKey = toggleKey;
        this.enabled = FeatureToggle.isEnabled(toggleKey, enabledByDefault);
    }

    @Override
    public final void begin() {
        onBegin();
        if (enabled) {
            open();
        }
    }

    @Override
    public final void end() {
        close();
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

    protected abstract void open();

    protected abstract void close();

    /** Resets statistics and state for a new run of the phase. */
    protected abstract void onBegin();

    /** Drops state that must not outlive the phase, such as caches. */
    protected void onEnd() {}

    protected abstract String describeStats();
}
