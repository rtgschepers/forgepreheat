package forgepreheat.optimize;

import com.mojang.logging.LogUtils;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.ElementSearch;
import mezz.jei.gui.search.IElementSearch;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Stands in for JEI's ElementSearch while a worker thread constructs the real one. Lookups that need no index are
 * answered from a uid map built the same way ElementSearch builds its own; the first text search waits for the worker.
 * Only the render thread calls this class; the worker touches nothing but its own ElementSearch.
 */
final class BackgroundElementSearch implements IElementSearch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String THREAD_NAME = "ForgePreheat search index";
    private static final ThreadLocal<BackgroundElementSearch> BUILDING = new ThreadLocal<>();

    /** Ends a build JEI no longer needs; without it the worker keeps reading tooltips while the game shuts down. */
    private static final class Cancelled extends RuntimeException {
        Cancelled() {
            super("search index build abandoned", null, false, false);
        }
    }

    private final IIngredientManager manager;
    private final CompletableFuture<ElementSearch> worker;
    // Same keys, values and insertion order as ElementSearch.allElements, so iteration order matches too.
    private final Map<Object, IListElement<?>> allElements = new HashMap<>();
    private final List<IListElementInfo<?>> addedMeanwhile = new ArrayList<>();
    private ElementPrefixParser parser;
    private List<IListElementInfo<?>> initial;
    private ElementSearch ready;
    private volatile boolean cancelled;

    BackgroundElementSearch(ElementPrefixParser parser, List<IListElementInfo<?>> infos, IIngredientManager manager,
                            Set<BackgroundElementSearch> building) {
        this.parser = parser;
        this.manager = manager;
        this.initial = List.copyOf(infos);
        for (IListElementInfo<?> info : initial) {
            allElements.put(uid(info.getTypedIngredient()), info.getElement());
        }
        List<IListElementInfo<?>> snapshot = initial;
        long start = System.nanoTime();
        building.add(this);
        worker = CompletableFuture.supplyAsync(() -> {
            BUILDING.set(this);
            try {
                ElementSearch search = new ElementSearch(parser, snapshot, manager);
                LOGGER.info("[ForgePreheat] Search index for {} ingredients built on a worker thread in {} s",
                        snapshot.size(), String.format("%.2f", (System.nanoTime() - start) / 1e9));
                return search;
            } catch (Cancelled e) {
                LOGGER.info("[ForgePreheat] Search index build stopped because JEI stopped");
                throw e;
            } finally {
                BUILDING.remove();
                building.remove(this);
            }
        }, BackgroundElementSearch::startWorker);
    }

    void cancel() {
        cancelled = true;
    }

    static void throwIfCancelled() {
        BackgroundElementSearch search = BUILDING.get();
        if (search != null && search.cancelled) {
            throw new Cancelled();
        }
    }

    private static void startWorker(Runnable task) {
        Thread thread = new Thread(task, THREAD_NAME);
        thread.setDaemon(true);
        // Leaves the render thread ahead when both compete for a core.
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    @Override
    public <T> void add(IListElementInfo<T> info, IIngredientManager ingredientManager) {
        if (ready != null) {
            ready.add(info, ingredientManager);
            return;
        }
        allElements.put(uid(info.getElement().getTypedIngredient()), info.getElement());
        addedMeanwhile.add(info);
    }

    @Override
    public Collection<IListElement<?>> getAllIngredients() {
        return ready != null ? ready.getAllIngredients() : Collections.unmodifiableCollection(allElements.values());
    }

    @Override
    public Set<IListElement<?>> getSearchResults(ElementPrefixParser.TokenInfo tokenInfo) {
        return index().getSearchResults(tokenInfo);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<IListElement<T>> findElement(ITypedIngredient<T> typedIngredient, IIngredientHelper<T> helper) {
        if (ready != null) {
            return ready.findElement(typedIngredient, helper);
        }
        // Mirrors ElementSearch.findElement.
        IListElement<?> element = allElements.get(helper.getUid(typedIngredient, UidContext.Ingredient));
        if (element != null && element.getTypedIngredient().getType().equals(typedIngredient.getType())) {
            return Optional.of((IListElement<T>) element);
        }
        return Optional.empty();
    }

    @Override
    public void logStatistics() {
        index().logStatistics();
    }

    private ElementSearch index() {
        if (ready == null) {
            ready = awaitWorker();
            // Replays later additions in order, as if they had reached the real index directly.
            for (IListElementInfo<?> info : addedMeanwhile) {
                ready.add(info, manager);
            }
            addedMeanwhile.clear();
            parser = null;
            initial = null;
        }
        return ready;
    }

    private ElementSearch awaitWorker() {
        long start = System.nanoTime();
        try {
            ElementSearch search = worker.join();
            double waited = (System.nanoTime() - start) / 1e9;
            if (waited >= 0.05) {
                LOGGER.info("[ForgePreheat] First search waited {} s for the search index",
                        String.format("%.2f", waited));
            }
            return search;
        } catch (CompletionException e) {
            if (!(e.getCause() instanceof Cancelled)) {
                LOGGER.warn("[ForgePreheat] Background search index failed; building it on the render thread instead",
                        e.getCause());
            }
            return new ElementSearch(parser, initial, manager);
        }
    }

    private <T> Object uid(ITypedIngredient<T> typedIngredient) {
        return manager.getIngredientHelper(typedIngredient.getType()).getUid(typedIngredient, UidContext.Ingredient);
    }
}
