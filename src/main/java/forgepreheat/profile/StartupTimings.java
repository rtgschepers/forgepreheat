package forgepreheat.profile;

import forgepreheat.core.PhaseFeature;

/**
 * Times named steps during client startup, from mod construction to the title screen. Steps can run on mod-loading
 * and reload worker threads, so access is synchronized; timed steps are coarse, so the lock costs nothing measurable.
 */
public final class StartupTimings extends PhaseFeature {
    public static final StartupTimings INSTANCE = new StartupTimings();

    private final StepClock clock = new StepClock();

    private StartupTimings() {
        super("startup timings", "startupTimings", true);
    }

    @Override
    protected synchronized void onBegin() {
        clock.clear();
    }

    @Override
    protected synchronized void onEnd() {
        clock.clear();
    }

    public synchronized void add(String name, long nanos) {
        if (isActive()) {
            clock.add(name, nanos);
        }
    }

    @Override
    protected synchronized String describeStats() {
        return clock.describe();
    }
}
