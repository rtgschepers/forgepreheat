package forgepreheat.profile;

import com.mojang.logging.LogUtils;
import forgepreheat.core.FeatureToggle;
import forgepreheat.core.StartupScope;
import forgepreheat.ui.JeiStartProgress;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Samples the thread running JEI's start every 5 ms and groups the stacks by phase and plugin, so a slow plugin step
 * shows where its time goes without a per-mod timing mixin (JFR samples that thread far too rarely in this pack).
 * Writes logs/forgepreheat-jei-samples-&lt;timestamp&gt;.txt. Opt-in: -Dforgepreheat.startSampler=true; it slows the start.
 */
public final class JeiStartSampler {
    public static final JeiStartSampler INSTANCE = new JeiStartSampler();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final long SAMPLE_MILLIS = 5;
    private static final int MIN_SAMPLES = 100;
    private static final int STACK_DEPTH = 18;
    private static final String[] GENERIC = {"java.", "jdk.", "sun.", "com.sun.", "it.unimi.", "com.google."};

    private static final class Step {
        int samples;
        final Map<String, Integer> methods = new HashMap<>();
        final Map<String, Integer> stacks = new HashMap<>();
    }

    private final boolean enabled = FeatureToggle.isEnabled("startSampler", false);
    private final Map<String, Step> steps = new HashMap<>();
    private volatile boolean running;
    private Thread sampler;

    private JeiStartSampler() {}

    public void begin() {
        if (!enabled) {
            return;
        }
        synchronized (steps) {
            steps.clear();
        }
        running = true;
        sampler = new Thread(this::sampleLoop, "ForgePreheat JEI start sampler");
        sampler.setDaemon(true);
        sampler.setPriority(Thread.MAX_PRIORITY);
        sampler.start();
    }

    public void end() {
        if (!enabled || !running) {
            return;
        }
        running = false;
        try {
            sampler.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        write();
    }

    private void sampleLoop() {
        while (running) {
            try {
                Thread.sleep(SAMPLE_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            Thread owner = StartupScope.owner();
            if (owner == null) {
                continue;
            }
            StackTraceElement[] trace = owner.getStackTrace();
            String plugin = PluginStepTimings.INSTANCE.currentPlugin();
            String key = JeiStartProgress.INSTANCE.phase() + (plugin.isEmpty() ? "" : " | " + plugin)
                    + (owner.getName().equals("Render thread") ? " (main thread)" : "");
            synchronized (steps) {
                Step step = steps.computeIfAbsent(key, k -> new Step());
                step.samples++;
                step.stacks.merge(stackKey(trace), 1, Integer::sum);
                List<String> seen = new ArrayList<>();
                for (StackTraceElement e : trace) {
                    String method = e.getClassName() + "." + e.getMethodName();
                    if (!isGeneric(e.getClassName()) && !seen.contains(method)) {
                        seen.add(method);
                        step.methods.merge(method, 1, Integer::sum);
                    }
                }
            }
        }
    }

    private static String stackKey(StackTraceElement[] trace) {
        StringBuilder key = new StringBuilder();
        int start = 0;
        while (start < trace.length - 1 && isGeneric(trace[start].getClassName())) {
            start++;
        }
        for (int i = start; i < Math.min(trace.length, start + STACK_DEPTH); i++) {
            key.append("\n        at ").append(trace[i]);
        }
        return key.toString();
    }

    private static boolean isGeneric(String className) {
        for (String prefix : GENERIC) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void write() {
        Path file = FMLPaths.GAMEDIR.get().resolve("logs")
                .resolve("forgepreheat-jei-samples-" + LocalDateTime.now().format(STAMP) + ".txt");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            synchronized (steps) {
                out.printf("JEI start samples, one every %d ms of the thread running the start%n%n", SAMPLE_MILLIS);
                steps.entrySet().stream()
                        .sorted((a, b) -> b.getValue().samples - a.getValue().samples)
                        .filter(e -> e.getValue().samples >= MIN_SAMPLES)
                        .forEach(e -> writeStep(out, e.getKey(), e.getValue()));
            }
            LOGGER.info("[ForgePreheat] JEI start samples written to {}", file);
        } catch (IOException e) {
            LOGGER.error("[ForgePreheat] Failed to write JEI start samples", e);
        }
    }

    private static void writeStep(PrintWriter out, String key, Step step) {
        out.printf("== %s: %d samples (~%.1f s) ==%n", key, step.samples, step.samples * SAMPLE_MILLIS / 1000.0);
        out.println("  Methods on the stack (share of samples; methods on every sample left out):");
        step.methods.entrySet().stream()
                .filter(e -> e.getValue() < step.samples * 0.98)
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(40)
                .forEach(e -> out.printf("    %3d%%  %s%n", 100 * e.getValue() / step.samples, e.getKey()));
        out.println("  Most common stacks:");
        step.stacks.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(6)
                .forEach(e -> out.printf("    %d samples:%s%n", e.getValue(), e.getKey()));
        out.println();
    }
}
