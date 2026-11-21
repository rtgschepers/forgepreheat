package forgepreheat.profile;

import forgepreheat.core.ScopedFeature;

/**
 * Times named steps inside other mods' JEI plugins, where the per-category recipe timings can't see. Added because
 * JFR barely sampled GTCEu's ~6 s of recipe registration, so the split between its steps was a guess.
 */
public final class StepTimings extends ScopedFeature {
    public static final StepTimings INSTANCE = new StepTimings();

    private final StepClock clock = new StepClock();

    private StepTimings() {
        super("step timings", "stepTimings", true);
    }

    @Override
    protected void onBegin() {
        clock.clear();
    }

    @Override
    protected void onEnd() {
        clock.clear();
    }

    public void enter(String name) {
        if (isActive()) {
            clock.enter(name);
        }
    }

    public void exit(String name) {
        if (isActive()) {
            clock.exit(name);
        }
    }

    /** Starts a run of laps; each lap() books the time since the previous one, so the laps cover the run without gaps. */
    public void startLaps() {
        if (isActive()) {
            clock.startLaps();
        }
    }

    public void lap(String prefix, Object key) {
        if (isActive()) {
            clock.lap(prefix + key);
        }
    }

    @Override
    protected String describeStats() {
        return clock.describe();
    }
}
