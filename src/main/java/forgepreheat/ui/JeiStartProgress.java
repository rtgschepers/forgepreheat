package forgepreheat.ui;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Tracks which phase JEI's start is in and estimates the time left from how long each phase took in the previous
 * start, which is saved in config/forgepreheat-jei-timing.properties. Without a previous start there is no estimate.
 */
public final class JeiStartProgress {
    public static final JeiStartProgress INSTANCE = new JeiStartProgress();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TOTAL = "total";

    private final Path file = FMLPaths.CONFIGDIR.get().resolve("forgepreheat-jei-timing.properties");
    private final Map<String, Long> current = new LinkedHashMap<>();
    private Map<String, Long> previous;
    private volatile long startedAt;
    private volatile long phaseStartedAt;
    private volatile String phase = "";

    private JeiStartProgress() {}

    public synchronized void onBegin() {
        if (previous == null) {
            previous = load();
        }
        current.clear();
        startedAt = phaseStartedAt = System.nanoTime();
        phase = "Starting";
    }

    public synchronized void onPhase(String title) {
        if (startedAt == 0) {
            return;
        }
        long now = System.nanoTime();
        phase = title;
        phaseStartedAt = now;
        current.putIfAbsent(title, (now - startedAt) / 1_000_000);
    }

    /** Saves this start's phase offsets for the next estimate; a cancelled start is not representative. */
    public synchronized void onEnd(boolean save) {
        if (startedAt == 0) {
            return;
        }
        if (save) {
            current.put(TOTAL, (System.nanoTime() - startedAt) / 1_000_000);
            previous = new LinkedHashMap<>(current);
            store(previous);
        }
        startedAt = 0;
        phase = "";
    }

    public boolean isRunning() {
        return startedAt != 0;
    }

    public String phase() {
        return phase;
    }

    /** Estimated seconds left, or -1 when there is nothing to estimate from. */
    public synchronized long secondsLeft() {
        if (startedAt == 0 || previous == null || !previous.containsKey(TOTAL)) {
            return -1;
        }
        long now = System.nanoTime();
        Long phaseOffset = previous.get(phase);
        long elapsedMillis = phaseOffset != null
                ? phaseOffset + (now - phaseStartedAt) / 1_000_000
                : (now - startedAt) / 1_000_000;
        return Math.max(0, (previous.get(TOTAL) - elapsedMillis + 999) / 1000);
    }

    private Map<String, Long> load() {
        Map<String, Long> out = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return out;
        }
        Properties props = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(in);
            props.forEach((k, v) -> out.put(k.toString(), Long.parseLong(v.toString().trim())));
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[ForgePreheat] Could not read {}; JEI loading estimate starts over", file, e);
            out.clear();
        }
        return out;
    }

    private void store(Map<String, Long> offsets) {
        Properties props = new Properties();
        offsets.forEach((k, v) -> props.setProperty(k, Long.toString(v)));
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            props.store(out, "ForgePreheat: milliseconds from the start of JEI's last start to each phase");
        } catch (IOException e) {
            LOGGER.warn("[ForgePreheat] Could not write {}", file, e);
        }
    }
}
