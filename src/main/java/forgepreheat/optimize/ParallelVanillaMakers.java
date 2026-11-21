package forgepreheat.optimize;

import forgepreheat.core.ScopedFeature;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.platform.IPlatformRecipeHelper;
import mezz.jei.common.platform.Services;
import mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeMaker;
import mezz.jei.library.plugins.vanilla.grindstone.GrindstoneRecipeMaker;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * JEI's vanilla plugin makes the anvil (3.6 s) and grindstone (4.8 s) recipe lists one after the other, after adding
 * crafting, furnace and the other vanilla categories (~8 s). Both makers only read the player, the item registry and the
 * ingredient list, and each uses its own fake menu, so this starts both on their own threads when the plugin begins and
 * hands JEI their lists where it would have called them. A maker's exception is rethrown there, as JEI would see it.
 */
public final class ParallelVanillaMakers extends ScopedFeature {
    public static final ParallelVanillaMakers INSTANCE = new ParallelVanillaMakers();

    private CompletableFuture<List<IJeiAnvilRecipe>> anvil;
    private CompletableFuture<List<IJeiGrindstoneRecipe>> grindstone;
    private volatile long anvilNanos, grindstoneNanos;
    private long waitedNanos;
    private int runs;

    private ParallelVanillaMakers() {
        super("parallel anvil/grindstone recipes", "parallelVanillaRecipes", true);
    }

    @Override
    protected void onBegin() {
        anvil = null;
        grindstone = null;
        anvilNanos = grindstoneNanos = waitedNanos = 0;
        runs = 0;
    }

    @Override
    protected void onEnd() {
        anvil = null;
        grindstone = null;
    }

    /** Called when VanillaPlugin.registerRecipes begins. */
    public void start(IVanillaRecipeFactory factory, IIngredientManager ingredients) {
        if (!isActive()) {
            return;
        }
        IPlatformRecipeHelper recipeHelper = Services.PLATFORM.getRecipeHelper();
        anvil = run("ForgePreheat anvil recipes", () -> {
            long start = System.nanoTime();
            List<IJeiAnvilRecipe> list = AnvilRecipeMaker.getAnvilRecipes(factory, ingredients);
            anvilNanos = System.nanoTime() - start;
            return list;
        });
        grindstone = run("ForgePreheat grindstone recipes", () -> {
            long start = System.nanoTime();
            List<IJeiGrindstoneRecipe> list = GrindstoneRecipeMaker.getGrindstoneRecipes(ingredients, recipeHelper);
            grindstoneNanos = System.nanoTime() - start;
            return list;
        });
        runs++;
    }

    public List<IJeiAnvilRecipe> anvilRecipes(IVanillaRecipeFactory factory, IIngredientManager ingredients) {
        CompletableFuture<List<IJeiAnvilRecipe>> future = anvil;
        anvil = null;
        if (!isActive() || future == null) {
            return AnvilRecipeMaker.getAnvilRecipes(factory, ingredients);
        }
        return join(future);
    }

    public List<IJeiGrindstoneRecipe> grindstoneRecipes(IIngredientManager ingredients, IPlatformRecipeHelper helper) {
        CompletableFuture<List<IJeiGrindstoneRecipe>> future = grindstone;
        grindstone = null;
        if (!isActive() || future == null) {
            return GrindstoneRecipeMaker.getGrindstoneRecipes(ingredients, helper);
        }
        return join(future);
    }

    private static <T> CompletableFuture<T> run(String threadName, Supplier<T> maker) {
        return CompletableFuture.supplyAsync(maker, task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            thread.start();
        });
    }

    private <T> T join(CompletableFuture<T> future) {
        long start = System.nanoTime();
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        } finally {
            waitedNanos += System.nanoTime() - start;
        }
    }

    @Override
    protected String describeStats() {
        return String.format("%d runs, anvil %.2f s and grindstone %.2f s on their own threads, JEI waited %.2f s for them",
                runs, anvilNanos / 1e9, grindstoneNanos / 1e9, waitedNanos / 1e9);
    }
}
