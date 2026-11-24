package forgepreheat.optimize;

import com.mojang.logging.LogUtils;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.library.ingredients.IIngredientSupplier;
import mezz.jei.library.recipes.collect.RecipeMap;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The addRecipe calls one JEI RecipeMap skipped during JEI's start, replayed in their original order so the map ends up
 * exactly as JEI would have built it. Until JEI stops, every read of the map builds it first; the lock makes a read wait
 * for a build that is already running.
 */
public final class DeferredRecipeIndex {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_LOGGED_FAILURES = 5;

    private record Pending(RecipeType<?> type, Object recipe, IIngredientSupplier supplier) {}

    private final RecipeMap map;
    private List<Pending> pending = new ArrayList<>();
    private int next;
    private boolean compactRequested;
    private volatile boolean built;
    private volatile boolean abandoned;
    private volatile Thread replayer;
    private int failures;

    DeferredRecipeIndex(RecipeMap map) {
        this.map = map;
    }

    synchronized void add(RecipeType<?> type, Object recipe, IIngredientSupplier supplier) {
        pending.add(new Pending(type, recipe, supplier));
    }

    /** True while this thread replays the deferred calls, which must reach JEI's own code. */
    public boolean isReplaying() {
        return replayer == Thread.currentThread();
    }

    /** JEI compacts every map at the end of its start; for a map not built yet that waits until it is. */
    public synchronized boolean deferCompact() {
        if (built) {
            return false;
        }
        compactRequested = true;
        return true;
    }

    /** Called before every read of the map. */
    public void ensureBuilt() {
        if (!built && !abandoned) {
            LazyRenderOnlyIndex.INSTANCE.onReadBeforeBuilt();
            build();
        }
    }

    /** Called before an addRecipe that reaches the map directly, after the start; keeps the original order. */
    public void buildBeforeDirectAdd() {
        if (!built) {
            build();
        }
    }

    synchronized int size() {
        return pending == null ? 0 : pending.size();
    }

    boolean isBuilt() {
        return built;
    }

    /**
     * JEI stopped, so this map belongs to a discarded runtime; mods that keep that runtime would otherwise pin the pending
     * calls (1.2 GB in this pack). A read after this sees the map as far as it was replayed.
     */
    void abandon() {
        abandoned = true; // Set before the lock, so a running build leaves it after its current entry.
        synchronized (this) {
            if (!built) {
                pending = null;
            }
        }
    }

    /** Replays the remaining calls; stops early when JEI stops. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    synchronized void build() {
        if (built || abandoned) {
            return;
        }
        replayer = Thread.currentThread();
        try {
            while (next < pending.size()) {
                if (abandoned) {
                    return;
                }
                Pending p = pending.get(next++);
                try {
                    map.addRecipe((RecipeType) p.type(), p.recipe(), p.supplier());
                } catch (RuntimeException e) {
                    // In a normal start this would fail the recipe inside JEI's loop; keep indexing the others.
                    if (failures++ < MAX_LOGGED_FAILURES) {
                        LOGGER.warn("[ForgePreheat] Deferred render-only index entry failed for {}", p.type().getUid(), e);
                    }
                }
            }
            pending = null;
            if (compactRequested) {
                map.compact();
            }
            built = true;
        } finally {
            replayer = null;
        }
    }
}
