package forgepreheat.optimize;

import forgepreheat.core.ScopedFeature;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.library.ingredients.IIngredientSupplier;
import mezz.jei.library.recipes.collect.RecipeMap;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI indexes every recipe into its INPUT, OUTPUT and CATALYST recipe maps (one per role) inline, on the thread running
 * its start: 13.0 s of the start in this pack (INPUT 10.4 s for 5.0M entries). Each map only depends on its own calls,
 * so this feeds each map's calls, in JEI's order, to that map's own thread while the start goes on; JEI's compact at the
 * end of recipe registration and every read wait until the map has caught up. The maps end up as JEI builds them.
 * One difference: when indexing a recipe throws (an ingredient's uid fails), JEI drops that recipe from its category;
 * here it stays listed. The summary counts such failures (expected 0). Render-only is handled by LazyRenderOnlyIndex.
 */
public final class ParallelRecipeIndex extends ScopedFeature {
    public static final ParallelRecipeIndex INSTANCE = new ParallelRecipeIndex();

    private final List<PipelinedRecipeIndex> indexes = new ArrayList<>();

    private ParallelRecipeIndex() {
        super("parallel recipe index", "parallelRecipeIndex", true);
    }

    @Override
    protected void onBegin() {
        indexes.clear();
    }

    @Override
    protected void onEnd() {
        synchronized (indexes) {
            indexes.forEach(PipelinedRecipeIndex::close);
            indexes.clear();
        }
    }

    /** Returns the map's index when this call was queued (the caller keeps it on the map), else null. */
    public PipelinedRecipeIndex queue(RecipeMap map, PipelinedRecipeIndex existing, RecipeIngredientRole role,
                                      RecipeType<?> type, Object recipe, IIngredientSupplier supplier) {
        if (!isActive() || role == RecipeIngredientRole.RENDER_ONLY) {
            return null;
        }
        PipelinedRecipeIndex index = existing;
        if (index == null) {
            index = new PipelinedRecipeIndex(map, role);
            synchronized (indexes) {
                indexes.add(index);
            }
        }
        index.add(type, recipe, supplier);
        return index;
    }

    @Override
    protected String describeStats() {
        StringBuilder out = new StringBuilder();
        synchronized (indexes) {
            for (PipelinedRecipeIndex index : indexes) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(String.format("%s %d calls in %.2f s on its own thread (JEI waited %.2f s, %d failures)",
                        index.role(), index.added(), index.busySeconds(), index.waitedSeconds(), index.failures()));
            }
        }
        return out.length() == 0 ? "not used" : out.toString();
    }
}
