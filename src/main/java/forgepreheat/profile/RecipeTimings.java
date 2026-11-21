package forgepreheat.profile;

import forgepreheat.core.ScopedFeature;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Collects how long JEI's addRecipe takes per category, per source mod and for the slowest single recipes, split into
 * building the recipe's ingredient lists (setRecipe) and indexing them, so a slow category shows which half is slow.
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

    private RecipeTimings() {
        super("recipe profiler", "profile", true);
    }

    @Override
    protected void onBegin() {
        byCategory.clear();
        byCategoryAndSource.clear();
        slowest.clear();
        recipes = 0;
        pendingSetup = pendingIndex = 0;
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

    public void addIndexNanos(long nanos) {
        if (isActive()) {
            pendingIndex += nanos;
        }
    }

    public void record(ResourceLocation categoryUid, Object recipe, long nanos) {
        if (!isActive()) {
            return;
        }
        recipes++;
        long setup = pendingSetup;
        long index = pendingIndex;
        pendingSetup = pendingIndex = 0;
        String category = String.valueOf(categoryUid);
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
