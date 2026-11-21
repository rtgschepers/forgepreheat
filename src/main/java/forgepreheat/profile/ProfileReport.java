package forgepreheat.profile;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Writes logs/forgepreheat-jei-&lt;timestamp&gt;.txt and a one-line summary to the game log. */
public final class ProfileReport {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ProfileReport() {}

    public static void write(RecipeTimings timings, List<String> featureSummaries) {
        long wall = timings.elapsedNanos();
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("logs");
            Files.createDirectories(dir);
            Path file = dir.resolve("forgepreheat-jei-" + LocalDateTime.now().format(FILE_STAMP) + ".txt");
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
                writeBody(out, timings, wall, featureSummaries);
            }
            LOGGER.info("[ForgePreheat] JEI start took {} s, {} s of it adding recipes; {}. Report: {}",
                    secs(wall), secs(timings.addRecipeNanos()), String.join("; ", featureSummaries), file);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[ForgePreheat] Failed to write report", e);
        }
    }

    private static void writeBody(PrintWriter out, RecipeTimings timings, long wall, List<String> featureSummaries) {
        out.printf("JEI start wall time: %s s, of which addRecipe: %s s%n", secs(wall), secs(timings.addRecipeNanos()));
        featureSummaries.forEach(out::println);
        out.println();
        table(out, "Per category", timings.byCategory(), 60);
        table(out, "Per category and recipe source (namespace)", timings.byCategoryAndSource(), 80);

        out.println("== Slowest individual recipes ==");
        for (RecipeTimings.SlowRecipe s : timings.slowest()) {
            out.printf("%9.2f ms  %-40s %s%n", s.nanos() / 1e6, s.category(), s.id());
            out.println("              " + RecipeInspector.describeInputs(s.recipe()));
        }
        out.println();

        // Every category, sorted by id, so two reports can be diffed to spot recipes that went missing.
        out.println("== Recipe count per category (all) ==");
        timings.byCategory().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.printf("%8d  %s%n", e.getValue().count(), e.getKey()));
    }

    private static void table(PrintWriter out, String title, Map<String, RecipeStat> rows, int limit) {
        long sum = Math.max(1, rows.values().stream().mapToLong(RecipeStat::totalNanos).sum());
        out.println("== " + title + " ==");
        out.printf("%10s %7s %8s %9s %10s  %s%n", "total s", "share", "recipes", "avg ms", "max ms", "key (slowest recipe)");
        rows.entrySet().stream()
                .sorted(Map.Entry.<String, RecipeStat>comparingByValue(Comparator.comparingLong(RecipeStat::totalNanos)).reversed())
                .limit(limit)
                .forEach(e -> {
                    RecipeStat s = e.getValue();
                    out.printf("%10s %6.1f%% %8d %9.3f %10.2f  %s (%s)%n", secs(s.totalNanos()), 100.0 * s.totalNanos() / sum,
                            s.count(), s.totalNanos() / 1e6 / s.count(), s.maxNanos() / 1e6, e.getKey(), s.slowestId());
                });
        out.println();
    }

    private static String secs(long nanos) {
        return String.format("%.2f", nanos / 1e9);
    }
}
