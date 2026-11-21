package forgepreheat.profile;

import forgepreheat.core.ScopedFeature;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.library.ingredients.IIngredientSupplier;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Collects how long JEI's addRecipe takes per category, per source mod and for the slowest single recipes, split into
 * building the recipe's ingredient lists (setRecipe) and indexing them, so a slow category shows which half is slow.
 * The index part is also split per ingredient role, because JEI keeps one index per role and only reads the
 * render-only one for explicit render-only lookups.
 */
public final class RecipeTimings extends ScopedFeature {
    public static final RecipeTimings INSTANCE = new RecipeTimings();

    private static final int TOP_N = 50;

    public record SlowRecipe(long nanos, String category, String id, Object recipe) {}

    private final Map<String, RecipeStat> byCategory = new HashMap<>();
    private final Map<String, RecipeStat> byCategoryAndSource = new HashMap<>();
    // Min-heap on time, so the fastest of the kept recipes is the one evicted.
    private final PriorityQueue<SlowRecipe> slowest = new PriorityQueue<>(Comparator.comparingLong(SlowRecipe::nanos));
    private long startNanos;
    private long recipes;
    // Parts of the addRecipe call in progress; booked on that recipe in record().
    private long pendingSetup;
    private long pendingIndex;
    private long pendingRenderOnly;
    private final Map<RecipeIngredientRole, long[]> byRole = new EnumMap<>(RecipeIngredientRole.class);
    private final Map<String, long[]> renderOnlyByCategory = new HashMap<>();

    private RecipeTimings() {
        super("recipe profiler", "profile", true);
    }

    @Override
    protected void onBegin() {
        byCategory.clear();
        byCategoryAndSource.clear();
        slowest.clear();
        recipes = 0;
        pendingSetup = pendingIndex = pendingRenderOnly = 0;
        byRole.clear();
        renderOnlyByCategory.clear();
        startNanos = System.nanoTime();
    }

    @Override
    protected void onEnd() {
        // Holding recipe objects past JEI's start would keep a stale recipe set alive.
        slowest.clear();
    }

    public void addSetupNanos(long nanos) {
        if (isActive()) {
            pendingSetup += nanos;
        }
    }

    /** One RecipeMap.addRecipe call; JEI makes one per role per recipe. */
    public void addIndexNanos(RecipeIngredientRole role, IIngredientSupplier supplier, long nanos) {
        if (!isActive()) {
            return;
        }
        pendingIndex += nanos;
        long[] stat = byRole.computeIfAbsent(role, k -> new long[3]);
        stat[0] += nanos;
        stat[1] += supplier.getIngredients(role).size();
        stat[2]++;
        if (role == RecipeIngredientRole.RENDER_ONLY) {
            pendingRenderOnly += nanos;
        }
    }

    public void record(ResourceLocation categoryUid, Object recipe, long nanos) {
        if (!isActive()) {
            return;
        }
        recipes++;
        long setup = pendingSetup;
        long index = pendingIndex;
        long renderOnly = pendingRenderOnly;
        pendingSetup = pendingIndex = pendingRenderOnly = 0;
        String category = String.valueOf(categoryUid);
        if (renderOnly > 0) {
            long[] stat = renderOnlyByCategory.computeIfAbsent(category, k -> new long[2]);
            stat[0] += renderOnly;
            stat[1]++;
        }
        String id = RecipeInspector.recipeId(recipe);
        byCategory.computeIfAbsent(category, k -> new RecipeStat()).add(id, nanos, setup, index);
        byCategoryAndSource.computeIfAbsent(category + "  <-  " + RecipeInspector.source(id), k -> new RecipeStat())
                .add(id, nanos, setup, index);
        if (slowest.size() < TOP_N || nanos > slowest.peek().nanos()) {
            slowest.add(new SlowRecipe(nanos, category, id, recipe));
            if (slowest.size() > TOP_N) {
                slowest.poll();
            }
        }
    }

    public long elapsedNanos() {
        return System.nanoTime() - startNanos;
    }

    public long addRecipeNanos() {
        return byCategory.values().stream().mapToLong(RecipeStat::totalNanos).sum();
    }

    public Map<String, RecipeStat> byCategory() {
        return Map.copyOf(byCategory);
    }

    public Map<String, RecipeStat> byCategoryAndSource() {
        return Map.copyOf(byCategoryAndSource);
    }

    /** Per role: {index nanos, ingredient entries, addRecipe calls}. */
    public Map<RecipeIngredientRole, long[]> byRole() {
        return new EnumMap<>(byRole);
    }

    /** Per category: {render-only index nanos, recipes with render-only work}. */
    public Map<String, long[]> renderOnlyByCategory() {
        return Map.copyOf(renderOnlyByCategory);
    }

    /** Slowest first. */
    public List<SlowRecipe> slowest() {
        List<SlowRecipe> list = new ArrayList<>(slowest);
        list.sort(Comparator.comparingLong(SlowRecipe::nanos).reversed());
        return list;
    }

    @Override
    protected String describeStats() {
        return recipes + " recipes timed";
    }
}
