package forgepreheat.profile;

import forgepreheat.core.ScopedFeature;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Times named steps inside other mods' JEI plugins, where the per-category recipe timings can't see. Added because
 * JFR barely sampled GTCEu's ~6 s of recipe registration, so the split between its steps was a guess.
 * Recursive or nested calls of the same step count once, at the outermost call.
 */
public final class StepTimings extends ScopedFeature {
    public static final StepTimings INSTANCE = new StepTimings();

    private static final class Step {
        long calls;
        long nanos;
        int depth;
        long startedAt;
    }

    private final Map<String, Step> steps = new LinkedHashMap<>();
    private long lapMark;

    private StepTimings() {
        super("step timings", "stepTimings", true);
    }

    @Override
    protected void onBegin() {
        steps.clear();
    }

    @Override
    protected void onEnd() {
        steps.clear();
    }

    public void enter(String name) {
        if (!isActive()) {
            return;
        }
        Step step = steps.computeIfAbsent(name, k -> new Step());
        if (step.depth++ == 0) {
            step.startedAt = System.nanoTime();
        }
    }

    public void exit(String name) {
        if (!isActive()) {
            return;
        }
        Step step = steps.get(name);
        // A step whose enter ran before JEI's start began has no matching state.
        if (step == null || step.depth == 0) {
            return;
        }
        if (--step.depth == 0) {
            step.calls++;
            step.nanos += System.nanoTime() - step.startedAt;
        }
    }

    /** Starts a run of laps; each lap() books the time since the previous one, so the laps cover the run without gaps. */
    public void startLaps() {
        if (isActive()) {
            lapMark = System.nanoTime();
        }
    }

    public void lap(String prefix, Object key) {
        if (!isActive()) {
            return;
        }
        long now = System.nanoTime();
        Step step = steps.computeIfAbsent(prefix + key, k -> new Step());
        step.calls++;
        step.nanos += now - lapMark;
        lapMark = now;
    }

    @Override
    protected String describeStats() {
        if (steps.isEmpty()) {
            return "no timed steps ran";
        }
        return steps.entrySet().stream()
                .map(e -> String.format("%s %.2f s (%d calls)", e.getKey(), e.getValue().nanos / 1e9, e.getValue().calls))
                .collect(Collectors.joining(", "));
    }
}
