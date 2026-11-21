package forgepreheat.optimize;

import forgepreheat.core.ScopedFeature;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.IElementSearch;
import mezz.jei.api.runtime.IIngredientManager;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JEI builds its search index (tooltip, tag and mod-name text for ~70,000 ingredients, plus a substring index) on the
 * render thread during its start: about 7 s of the 7.5 s "Building ingredient filter" step in this pack. This feature
 * runs JEI's own index constructor on a worker thread instead, so searches give the same results.
 * On by default since 0.8.0; switch off with -Dforgepreheat.asyncSearch=false. The worker reads item tooltips, which
 * calls other mods' code off the render thread. Stopping JEI cancels a build that is still running.
 */
public final class BackgroundSearchIndex extends ScopedFeature {
    public static final BackgroundSearchIndex INSTANCE = new BackgroundSearchIndex();

    private final Set<BackgroundElementSearch> building = ConcurrentHashMap.newKeySet();
    private int ingredientsHandedOff;

    private BackgroundSearchIndex() {
        super("background search index", "asyncSearch", true);
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
        return new BackgroundElementSearch(parser, infos, manager, building);
    }

    /** Called when JEI stops, on any thread and outside a start: abandons index builds that are still running. */
    public void onJeiStopped() {
        building.forEach(BackgroundElementSearch::cancel);
    }

    /** Called from JEI's per-ingredient search text lookup; ends an abandoned build on its worker thread. */
    public void checkBuildCancelled() {
        BackgroundElementSearch.throwIfCancelled();
    }

    @Override
    protected String describeStats() {
        return ingredientsHandedOff == 0
                ? "not used"
                : ingredientsHandedOff + " ingredients indexed on a worker thread";
    }
}
