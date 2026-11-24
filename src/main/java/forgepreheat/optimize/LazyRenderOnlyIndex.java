package forgepreheat.optimize;

import com.mojang.logging.LogUtils;
import forgepreheat.core.FeatureToggle;
import forgepreheat.core.ScopedFeature;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.library.ingredients.IIngredientSupplier;
import mezz.jei.library.recipes.collect.RecipeMap;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * JEI keeps one recipe index per ingredient role and reads one only for a lookup of that role. Its own GUI never looks up
 * RENDER_ONLY, yet that index took 20.0 s of the 31.2 s of indexing in this pack (35.3M of 41.5M entries; furnace and
 * blasting put the ~4,070-stack fuel list in a render-only slot on every recipe). This skips it during JEI's start and
 * builds it afterwards on a low-priority thread by replaying JEI's own calls in order, so the result is identical.
 * A lookup that comes earlier (only possible through JEI's API) waits for the build.
 * -Dforgepreheat.renderOnlyIndexBackground=false leaves it unbuilt until the first lookup.
 */
public final class LazyRenderOnlyIndex extends ScopedFeature {
    public static final LazyRenderOnlyIndex INSTANCE = new LazyRenderOnlyIndex();

    private static final Logger LOGGER = LogUtils.getLogger();

    private final boolean background = FeatureToggle.isEnabled("renderOnlyIndexBackground", true);
    private final AtomicInteger earlyReads = new AtomicInteger();
    private volatile DeferredRecipeIndex current;
    private long deferred;

    private LazyRenderOnlyIndex() {
        super("lazy render-only index", "lazyRenderOnlyIndex", true);
    }

    @Override
    protected void onBegin() {
        deferred = 0;
        current = null;
        earlyReads.set(0);
    }

    @Override
    protected void onEnd() {
        DeferredRecipeIndex index = current;
        if (index != null && background) {
            Thread thread = new Thread(() -> buildInBackground(index), "ForgePreheat render-only index");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }
    }

    /** Returns the map's deferred index when this call was deferred (the caller keeps it on the map), else null. */
    public DeferredRecipeIndex defer(RecipeMap map, DeferredRecipeIndex existing, RecipeIngredientRole role,
                                     RecipeType<?> type, Object recipe, IIngredientSupplier supplier) {
        if (!isActive() || role != RecipeIngredientRole.RENDER_ONLY) {
            return null;
        }
        if (existing == null && current != null) {
            return null; // A second render-only map in one start is unexpected; leave it to JEI.
        }
        DeferredRecipeIndex index = existing != null ? existing : new DeferredRecipeIndex(map);
        current = index;
        index.add(type, recipe, supplier);
        deferred++;
        return index;
    }

    /** True while the last start's index still waits for its background build. */
    public boolean isBackgroundBuildPending() {
        DeferredRecipeIndex index = current;
        return background && index != null && !index.isBuilt();
    }

    void onReadBeforeBuilt() {
        earlyReads.incrementAndGet();
    }

    /** JEI stopped: the old runtime's index is no longer needed, so its build stops and its pending calls are dropped. */
    public void onJeiStopped() {
        DeferredRecipeIndex index = current;
        current = null;
        if (index != null) {
            index.abandon();
        }
    }

    private void buildInBackground(DeferredRecipeIndex index) {
        long start = System.nanoTime();
        int entries = index.size();
        try {
            index.build();
        } catch (RuntimeException | LinkageError e) {
            LOGGER.error("[ForgePreheat] Background render-only index build failed; lookups will retry it", e);
            return;
        }
        if (index.isBuilt()) {
            LOGGER.info("[ForgePreheat] Render-only recipe index built in the background in {} s ({} recipes, {} reads "
                    + "waited for it)", String.format("%.2f", (System.nanoTime() - start) / 1e9), entries,
                    earlyReads.get());
        } else {
            LOGGER.info("[ForgePreheat] Background render-only index build stopped because JEI stopped");
        }
    }

    @Override
    protected String describeStats() {
        return deferred + " recipes deferred"
                + (background ? " (index built in the background after the start)" : " (index built on first lookup)");
    }
}
