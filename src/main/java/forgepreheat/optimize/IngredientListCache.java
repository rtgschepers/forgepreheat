package forgepreheat.optimize;

import forgepreheat.core.ListSnapshot;
import forgepreheat.core.ScopedFeature;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shares JEI's converted ingredient list when the same list object is added to many recipes. The main case is the
 * furnace fuel list (~1,050 stacks) that JEI puts on every one of ~8,000 cooking recipes.
 */
public final class IngredientListCache extends ScopedFeature {
    public static final IngredientListCache INSTANCE = new IngredientListCache();

    // Small lists are cheap to convert and mostly unique per recipe, so caching them would only cost memory.
    private static final int MIN_SIZE = 16;

    private record Entry(Object type, boolean normalize, ListSnapshot input, List<?> result) {}

    private final Map<List<?>, Entry> cache = new IdentityHashMap<>();
    private long hits;
    private long misses;
    private long stacksSkipped;

    private IngredientListCache() {
        super("fuel-list cache", "fuelCache", true);
    }

    @Override
    protected void onBegin() {
        cache.clear();
        hits = misses = stacksSkipped = 0;
    }

    @Override
    protected void onEnd() {
        cache.clear();
    }

    /** Returns a copy of the earlier conversion, or null when the list is new or its contents changed. */
    public List<?> lookup(Object type, List<?> input, boolean normalize) {
        if (!isActive() || input.size() < MIN_SIZE) {
            return null;
        }
        Entry e = cache.get(input);
        if (e == null || e.type() != type || e.normalize() != normalize || !e.input().matches(input)) {
            misses++;
            return null;
        }
        hits++;
        stacksSkipped += input.size();
        // Callers may add to the returned list, so each gets its own.
        return new ArrayList<>(e.result());
    }

    public void store(Object type, List<?> input, boolean normalize, List<?> result) {
        if (isActive() && input.size() >= MIN_SIZE) {
            cache.put(input, new Entry(type, normalize, ListSnapshot.of(input), new ArrayList<>(result)));
        }
    }

    @Override
    protected String describeStats() {
        return String.format("%d hits, %d misses, %d stack conversions skipped, %d distinct lists cached",
                hits, misses, stacksSkipped, cache.size());
    }
}
