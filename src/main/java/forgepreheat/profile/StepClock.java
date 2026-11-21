package forgepreheat.profile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Named step timer shared by the JEI-start and client-start timings. Nested or recursive calls of one step count once,
 * at the outermost call; laps book the time since the previous lap. Not thread-safe.
 */
final class StepClock {
    private static final class Step {
        long calls;
        long nanos;
        int depth;
        long startedAt;
    }

    private final Map<String, Step> steps = new LinkedHashMap<>();
    private long lapMark;

    void clear() {
        steps.clear();
    }

    void enter(String name) {
        Step step = steps.computeIfAbsent(name, k -> new Step());
        if (step.depth++ == 0) {
            step.startedAt = System.nanoTime();
        }
    }

    void exit(String name) {
        Step step = steps.get(name);
        // A step whose enter ran before the phase began has no matching state.
        if (step == null || step.depth == 0) {
            return;
        }
        if (--step.depth == 0) {
            step.calls++;
            step.nanos += System.nanoTime() - step.startedAt;
        }
    }

    void add(String name, long nanos) {
        Step step = steps.computeIfAbsent(name, k -> new Step());
        step.calls++;
        step.nanos += nanos;
    }

    void startLaps() {
        lapMark = System.nanoTime();
    }

    void lap(String name) {
        long now = System.nanoTime();
        add(name, now - lapMark);
        lapMark = now;
    }

    String describe() {
        if (steps.isEmpty()) {
            return "no timed steps ran";
        }
        return steps.entrySet().stream()
                .map(e -> String.format("%s %.2f s (%d calls)", e.getKey(), e.getValue().nanos / 1e9, e.getValue().calls))
                .collect(Collectors.joining(", "));
    }
}
