package forgepreheat.profile;

import com.mojang.logging.LogUtils;
import forgepreheat.core.FeatureToggle;
import forgepreheat.core.ScopedFeature;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.ResourceLocation;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Writes the id of every recipe JEI adds in the categories listed in -Dforgepreheat.dumpCategories, sorted, so two
 * runs can be diffed recipe by recipe. Added because the per-category counts of a sync and an async start differ
 * (grindstone -51) and counts alone don't say which recipes.
 */
public final class RecipeIdDump extends ScopedFeature {
    public static final RecipeIdDump INSTANCE = new RecipeIdDump();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Set<String> categories;
    private final Map<String, List<String>> ids = new TreeMap<>();

    private RecipeIdDump() {
        super("recipe id dump", "dumpIds", true);
        String list = System.getProperty(FeatureToggle.property("dumpCategories"), "");
        categories = Arrays.stream(list.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void onBegin() {
        ids.clear();
    }

    @Override
    protected void onEnd() {
        if (!ids.isEmpty()) {
            write();
        }
        ids.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void record(IRecipeCategory<?> category, Object recipe, boolean added) {
        if (!isActive() || categories.isEmpty()) {
            return;
        }
        String uid = String.valueOf(category.getRecipeType().getUid());
        if (!categories.contains(uid)) {
            return;
        }
        ResourceLocation name = ((IRecipeCategory) category).getRegistryName(recipe);
        String id = name != null ? name.toString() : RecipeInspector.recipeId(recipe);
        ids.computeIfAbsent(uid, k -> new ArrayList<>()).add(added ? id : id + "  (rejected)");
    }

    private void write() {
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("logs");
            Files.createDirectories(dir);
            Path file = dir.resolve("forgepreheat-ids-" + LocalDateTime.now().format(FILE_STAMP) + ".txt");
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
                ids.forEach((category, list) -> {
                    out.println("== " + category + " (" + list.size() + ") ==");
                    list.stream().sorted().forEach(out::println);
                    out.println();
                });
            }
            LOGGER.info("[ForgePreheat] Recipe ids for {} written to {}", ids.keySet(), file);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[ForgePreheat] Failed to write recipe id dump", e);
        }
    }

    @Override
    protected String describeStats() {
        if (categories.isEmpty()) {
            return "no categories (set -D" + FeatureToggle.property("dumpCategories") + "=<uid>,...)";
        }
        return ids.entrySet().stream().map(e -> e.getKey() + " " + e.getValue().size())
                .collect(Collectors.joining(", ", "dumping ", ""));
    }
}
