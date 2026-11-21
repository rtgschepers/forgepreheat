package forgepreheat.optimize;

import forgepreheat.core.ScopedFeature;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.IElementSearch;
import mezz.jei.api.runtime.IIngredientManager;

import java.util.List;

/**
 * JEI builds its search index (tooltip, tag and mod-name text for ~70,000 ingredients, plus a substring index) on the
 * render thread during its start: about 7 s of the 7.5 s "Building ingredient filter" step in this pack. This feature
 * runs JEI's own index constructor on a worker thread instead, so searches give the same results.
 * Opt-in because the worker reads item tooltips, which calls other mods' code off the render thread.
 */
public final class BackgroundSearchIndex extends ScopedFeature {
    public static final BackgroundSearchIndex INSTANCE = new BackgroundSearchIndex();

    private int ingredientsHandedOff;

    private BackgroundSearchIndex() {
        super("background search index", "asyncSearch", false);
    }

    @Override
    protected void onBegin() {
        ingredientsHandedOff = 0;
    }

    /** Returns a search that builds its index in the background, or null to let JEI build it now. */
    public IElementSearch create(boolean lowMemoryMode, ElementPrefixParser parser, List<IListElementInfo<?>> infos,
                                 IIngredientManager manager) {
        // The low-memory search has no index to build.
        if (!isActive() || lowMemoryMode) {
            return null;
        }
        ingredientsHandedOff += infos.size();
        return new BackgroundElementSearch(parser, infos, manager);
    }

    @Override
    protected String describeStats() {
        return ingredientsHandedOff == 0
                ? "not used"
                : ingredientsHandedOff + " ingredients indexed on a worker thread";
    }
}
