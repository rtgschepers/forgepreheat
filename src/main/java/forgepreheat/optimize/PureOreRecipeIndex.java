package forgepreheat.optimize;

import com.mojang.logging.LogUtils;
import forgepreheat.core.FeatureToggle;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * ElementalCraft generates its pure-ore recipes on the main thread when the server's recipes arrive (during "Loading
 * terrain"): for every ore, AbstractPureOreRecipeInjector.getRecipe streams every recipe of a type, makes a new stack of
 * the ore for each and tests the recipe's first ingredient. That was ~8 s of a 14.8 s join frame. This indexes each
 * injector's recipes by the items their first ingredient accepts and streams only those that can match, in the original
 * order, so ElementalCraft's own filter and min() see the same matches. Recipes whose ingredient can't be indexed
 * safely (not "simple", empty, or failing to read) always stay in the stream. Only injectors whose filter requires the
 * first ingredient to match use the index. -Dforgepreheat.pureOreIndexVerify=true tests every skipped recipe.
 */
public final class PureOreRecipeIndex {
    public static final PureOreRecipeIndex INSTANCE = new PureOreRecipeIndex();

    private static final Logger LOGGER = LogUtils.getLogger();
    // Their filter is the base one (first ingredient test), optionally followed by more conditions.
    private static final Set<String> INDEXABLE_INJECTORS = Set.of(
            "sirttas.elementalcraft.pureore.injector.PureOreCookingRecipeInjector",
            "sirttas.elementalcraft.pureore.injector.PureOreGrindingRecipeInjector");

    private final boolean enabled = FeatureToggle.isEnabled("pureOreIndex", true);
    private final boolean verify = FeatureToggle.isEnabled("pureOreIndexVerify", false);
    private long reloadStart;
    private long lookups, streamed, total, indexes, mismatches;

    /** One injector's recipes, valid while ElementalCraft keeps the same recipe collection. */
    public static final class Candidates {
        private final Collection<?> source;
        private final List<Object> recipes;
        private final Map<Item, int[]> byItem;
        private final int[] always;

        private Candidates(Collection<?> source, List<Object> recipes, Map<Item, int[]> byItem, int[] always) {
            this.source = source;
            this.recipes = recipes;
            this.byItem = byItem;
            this.always = always;
        }
    }

    private PureOreRecipeIndex() {}

    public boolean appliesTo(Object injector) {
        return enabled && INDEXABLE_INJECTORS.contains(injector.getClass().getName());
    }

    public void onReloadStart() {
        reloadStart = System.nanoTime();
        lookups = streamed = total = indexes = mismatches = 0;
    }

    public void onReloadEnd() {
        if (!enabled || lookups == 0) {
            return;
        }
        LOGGER.info("[ForgePreheat] ElementalCraft pure-ore generation took {} s; {} recipe lookups tested {} of {} "
                + "recipes ({} indexes built){}", String.format("%.2f", (System.nanoTime() - reloadStart) / 1e9),
                lookups, streamed, total, indexes, verify ? ", verify: " + mismatches + " mismatches" : "");
    }

    /** Reuses the index while the collection is the same object, else builds a new one. */
    public Candidates indexFor(Candidates current, Collection<?> recipes) {
        if (current != null && current.source == recipes) {
            return current;
        }
        indexes++;
        List<Object> list = new ArrayList<>(recipes);
        Map<Item, List<Integer>> byItem = new IdentityHashMap<>();
        List<Integer> always = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Ingredient first = firstIngredient(list.get(i));
            if (first == null || !first.isSimple() || first.isEmpty()) {
                always.add(i);
                continue;
            }
            for (ItemStack stack : first.getItems()) {
                List<Integer> slots = byItem.computeIfAbsent(stack.getItem(), k -> new ArrayList<>());
                if (slots.isEmpty() || slots.get(slots.size() - 1) != i) {
                    slots.add(i);
                }
            }
        }
        Map<Item, int[]> packed = new IdentityHashMap<>();
        byItem.forEach((item, slots) -> packed.put(item, toArray(slots)));
        return new Candidates(recipes, list, packed, toArray(always));
    }

    /** The recipes getRecipe(item) has to test, in the collection's order. */
    @SuppressWarnings("unchecked")
    public <T> Stream<T> candidates(Candidates index, Item item) {
        int[] matching = index.byItem.getOrDefault(item, new int[0]);
        int[] merged = merge(matching, index.always);
        lookups++;
        streamed += merged.length;
        total += index.recipes.size();
        if (verify) {
            check(index, item, matching);
        }
        return (Stream<T>) IntStream.of(merged).mapToObj(index.recipes::get);
    }

    private static Ingredient firstIngredient(Object recipe) {
        try {
            if (recipe instanceof Recipe<?> r && !r.getIngredients().isEmpty()) {
                return r.getIngredients().get(0);
            }
        } catch (RuntimeException e) {
            // ElementalCraft's own filter reports this recipe; keep it in the stream so it still does.
        }
        return null;
    }

    // A skipped recipe must be one whose first ingredient rejects a fresh stack of the item.
    private void check(Candidates index, Item item, int[] matching) {
        ItemStack stack = new ItemStack(item);
        boolean[] kept = new boolean[index.recipes.size()];
        for (int i : matching) {
            kept[i] = true;
        }
        for (int i : index.always) {
            kept[i] = true;
        }
        for (int i = 0; i < kept.length; i++) {
            if (!kept[i]) {
                Ingredient first = firstIngredient(index.recipes.get(i));
                if (first != null && first.test(stack) && mismatches++ < 10) {
                    LOGGER.warn("[ForgePreheat] Pure-ore index skipped a matching recipe {} for {}",
                            index.recipes.get(i), item);
                }
            }
        }
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static int[] merge(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        int i = 0, j = 0, k = 0;
        while (i < a.length && j < b.length) {
            out[k++] = a[i] < b[j] ? a[i++] : b[j++];
        }
        while (i < a.length) {
            out[k++] = a[i++];
        }
        while (j < b.length) {
            out[k++] = b[j++];
        }
        return out;
    }
}
