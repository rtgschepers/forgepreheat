package forgepreheat.optimize;

import com.mojang.logging.LogUtils;
import forgepreheat.core.FeatureToggle;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * On every recipe sync (during "Loading terrain"), Powah's wiki asks the recipe manager for all crafting recipes twice
 * per Powah item and checks every recipe's result against the item: ~2.2 s of the join frame. During that update this
 * hands both calls only the crafting recipes whose result is the item, in the original order, so Powah's own check
 * keeps the same recipes. A recipe whose result can't be read stays in every list, so Powah still meets it.
 */
public final class PowahWikiRecipes {
    public static final PowahWikiRecipes INSTANCE = new PowahWikiRecipes();

    private static final Logger LOGGER = LogUtils.getLogger();

    private final boolean enabled = FeatureToggle.isEnabled("powahWikiIndex", true);
    private boolean updating;
    private Map<Item, List<CraftingRecipe>> byResult;
    private List<CraftingRecipe> unreadable;
    private long start;
    private int lookups, recipes;

    private PowahWikiRecipes() {}

    public void onUpdateStart() {
        updating = enabled;
        byResult = null;
        lookups = recipes = 0;
        start = System.nanoTime();
    }

    public void onUpdateEnd() {
        if (updating && lookups > 0) {
            LOGGER.info("[ForgePreheat] Powah wiki update took {} s; {} recipe lookups served from an index of {} "
                    + "crafting recipes", String.format("%.2f", (System.nanoTime() - start) / 1e9), lookups, recipes);
        }
        updating = false;
        byResult = null;
        unreadable = null;
    }

    public List<CraftingRecipe> craftingRecipesFor(RecipeManager manager, RecipeType<CraftingRecipe> type,
                                                   RegistryAccess registries, Item item) {
        if (!updating) {
            return manager.getAllRecipesFor(type);
        }
        if (byResult == null) {
            build(manager.getAllRecipesFor(type), registries);
        }
        lookups++;
        List<CraftingRecipe> matching = byResult.getOrDefault(item, List.of());
        if (unreadable.isEmpty()) {
            return new ArrayList<>(matching);
        }
        List<CraftingRecipe> all = manager.getAllRecipesFor(type);
        List<CraftingRecipe> out = new ArrayList<>();
        for (CraftingRecipe recipe : all) {
            if (unreadable.contains(recipe) || matching.contains(recipe)) {
                out.add(recipe);
            }
        }
        return out;
    }

    private void build(List<CraftingRecipe> all, RegistryAccess registries) {
        byResult = new IdentityHashMap<>();
        unreadable = new ArrayList<>();
        for (CraftingRecipe recipe : all) {
            try {
                byResult.computeIfAbsent(recipe.getResultItem(registries).getItem(), k -> new ArrayList<>()).add(recipe);
            } catch (RuntimeException e) {
                unreadable.add(recipe);
            }
        }
        recipes = all.size();
    }
}
