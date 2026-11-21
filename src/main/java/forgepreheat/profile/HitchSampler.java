package forgepreheat.profile;

import com.mojang.logging.LogUtils;
import forgepreheat.core.FeatureToggle;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds what the render thread does during in-world hitches (a frame over 300 ms), such as the one on the first
 * inventory open. JFR gets only ~10 main-thread samples a second in this pack, so a watchdog thread samples the render
 * thread's stack every 5 ms once a frame has run 100 ms, and the frame's end logs where the samples landed.
 * Profiling only; -Dforgepreheat.hitchSampler=false turns it off.
 */
public final class HitchSampler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long WATCH_NANOS = 100_000_000L;
    private static final long REPORT_NANOS = 300_000_000L;
    private static final long SAMPLE_MILLIS = 5;
    private static final int MAX_REPORTS = 30;
    private static final int STACK_DEPTH = 14;
    private static final String[] GENERIC = {"java.", "jdk.", "sun.", "com.sun.", "org.lwjgl.", "it.unimi."};

    private static volatile Thread renderThread;
    private static volatile long frameStart;
    private static volatile boolean inWorld;
    // Written by the sampler, read and cleared by the render thread at the end of the frame.
    private static final Map<String, Integer> stacks = new HashMap<>();
    private static final Map<String, Integer> methods = new HashMap<>();
    private static int samples;
    private static int reports;

    private HitchSampler() {}

    public static void install() {
        if (!FeatureToggle.isEnabled("hitchSampler", true)) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(HitchSampler::onRenderTick);
        Thread sampler = new Thread(HitchSampler::sampleLoop, "ForgePreheat hitch sampler");
        sampler.setDaemon(true);
        sampler.setPriority(Thread.MAX_PRIORITY);
        sampler.start();
    }

    // Frame boundaries are the ends of consecutive frames, so ticks, queued tasks and screen opens are inside a frame.
    private static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long now = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        if (renderThread == null) {
            renderThread = Thread.currentThread();
        }
        long start = frameStart;
        frameStart = now;
        boolean wasInWorld = inWorld;
        inWorld = mc.level != null && mc.player != null;
        synchronized (stacks) {
            if (wasInWorld && start != 0 && now - start >= REPORT_NANOS && samples > 0 && reports < MAX_REPORTS) {
                reports++;
                report((now - start) / 1_000_000, mc);
            }
            stacks.clear();
            methods.clear();
            samples = 0;
        }
    }

    private static void sampleLoop() {
        while (true) {
            try {
                Thread.sleep(SAMPLE_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            Thread thread = renderThread;
            long start = frameStart;
            if (thread == null || !inWorld || start == 0 || System.nanoTime() - start < WATCH_NANOS) {
                continue;
            }
            StackTraceElement[] trace = thread.getStackTrace();
            synchronized (stacks) {
                if (frameStart != start) {
                    continue; // The frame ended while we sampled.
                }
                samples++;
                stacks.merge(stackKey(trace), 1, Integer::sum);
                for (String method : distinctMethods(trace)) {
                    methods.merge(method, 1, Integer::sum);
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

    private static List<String> distinctMethods(StackTraceElement[] trace) {
        List<String> out = new ArrayList<>();
        for (StackTraceElement e : trace) {
            String method = e.getClassName() + "." + e.getMethodName();
            if (!isGeneric(e.getClassName()) && !out.contains(method)) {
                out.add(method);
            }
        }
        return out;
    }

    private static boolean isGeneric(String className) {
        for (String prefix : GENERIC) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void report(long frameMillis, Minecraft mc) {
        StringBuilder out = new StringBuilder();
        out.append(String.format("[ForgePreheat] Hitch: frame took %d ms (screen %s), %d samples of the render thread "
                + "every %d ms after the first %d ms.", frameMillis,
                mc.screen == null ? "none" : mc.screen.getClass().getName(), samples, SAMPLE_MILLIS,
                WATCH_NANOS / 1_000_000));
        out.append("\n    Methods on the stack (share of samples):");
        methods.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(40)
                .forEach(e -> out.append(String.format("%n      %3d%%  %s", 100 * e.getValue() / samples, e.getKey())));
        out.append("\n    Most common stacks:");
        stacks.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(4)
                .forEach(e -> out.append(String.format("%n      %d samples:%s", e.getValue(), e.getKey())));
        LOGGER.info(out.toString());
    }
}
