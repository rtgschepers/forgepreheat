package forgepreheat.optimize;

import forgepreheat.core.ListSnapshot;
import forgepreheat.core.ScopedFeature;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replaces Create's pairwise "is this recipe excluded" scan (about 4,000 x 3,700 Ingredient tests in this pack) with
 * an item index. Every excluded recipe the index cannot rule out still gets the original Ingredient.test, so the
 * outcome matches Create's loop exactly.
 */
public final class CreateExclusionIndex extends ScopedFeature {
    public static final CreateExclusionIndex INSTANCE = new CreateExclusionIndex();

    private static final class Index {
        final ListSnapshot snapshot;
        final Map<Item, List<Ingredient>> byItem = new HashMap<>();
        // Ingredients whose test() may accept items outside getItems(): custom types and empty ingredients.
        final List<Ingredient> alwaysTest = new ArrayList<>();

        Index(List<?> excluded) {
            snapshot = ListSnapshot.of(excluded);
            for (Object o : excluded) {
                Recipe<?> recipe = (Recipe<?>) o;
                if (recipe.getIngredients().isEmpty()) {
                    continue; // Create's doInputsMatch returns false for these.
                }
                Ingredient first = recipe.getIngredients().get(0);
                if (!first.isSimple() || first.isEmpty()) {
                    alwaysTest.add(first);
                    continue;
                }
                Set<Item> items = new LinkedHashSet<>();
                for (ItemStack s : first.getItems()) {
                    items.add(s.getItem());
                }
                for (Item item : items) {
                    byItem.computeIfAbsent(item, k -> new ArrayList<>()).add(first);
                }
            }
        }

        boolean accepts(ItemStack probe) {
            for (Ingredient ing : byItem.getOrDefault(probe.getItem(), List.of())) {
                if (ing.test(probe)) {
                    return true;
                }
            }
            for (Ingredient ing : alwaysTest) {
                if (ing.test(probe)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Map<List<?>, Index> indexes = new IdentityHashMap<>();
    private long decided;
    private long indexesBuilt;

    private CreateExclusionIndex() {
        super("Create exclusion index", "createIndex", true);
    }

    @Override
    protected void onBegin() {
        indexes.clear();
        decided = indexesBuilt = 0;
    }

    @Override
    protected void onEnd() {
        indexes.clear();
    }

    /** Whether Create would drop the recipe, or null to let Create's own loop run. */
    public Boolean isExcluded(List<?> excluded, Recipe<?> recipe) {
        if (!isActive()) {
            return null;
        }
        if (recipe.getIngredients().isEmpty()) {
            return Boolean.FALSE;
        }
        ItemStack[] stacks = recipe.getIngredients().get(0).getItems();
        if (stacks.length == 0) {
            return Boolean.FALSE;
        }
        decided++;
        return indexFor(excluded).accepts(stacks[0]);
    }

    private Index indexFor(List<?> excluded) {
        Index index = indexes.get(excluded);
        if (index == null || !index.snapshot.matches(excluded)) {
            index = new Index(excluded);
            indexes.put(excluded, index);
            indexesBuilt++;
        }
        return index;
    }

    @Override
    protected String describeStats() {
        return String.format("%d recipes decided, %d indexes built", decided, indexesBuilt);
    }
}
