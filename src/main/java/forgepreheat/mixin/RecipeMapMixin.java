package forgepreheat.mixin;

import forgepreheat.optimize.DeferredRecipeIndex;
import forgepreheat.optimize.LazyRenderOnlyIndex;
import forgepreheat.optimize.ParallelRecipeIndex;
import forgepreheat.optimize.PipelinedRecipeIndex;
import forgepreheat.profile.RecipeTimings;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.library.ingredients.IIngredientSupplier;
import mezz.jei.library.recipes.collect.RecipeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecipeMap.class, remap = false)
public abstract class RecipeMapMixin {
    private static final String ADD = "addRecipe(Lmezz/jei/api/recipe/RecipeType;Ljava/lang/Object;"
            + "Lmezz/jei/library/ingredients/IIngredientSupplier;)V";

    @Shadow
    @Final
    private RecipeIngredientRole role;

    @Unique
    private long forgepreheat$start;

    @Unique
    private volatile DeferredRecipeIndex forgepreheat$deferred;

    @Unique
    private volatile PipelinedRecipeIndex forgepreheat$pipeline;

    @Inject(method = ADD, at = @At("HEAD"), cancellable = true)
    private void forgepreheat$before(@SuppressWarnings("rawtypes") RecipeType type, Object recipe,
                                     IIngredientSupplier supplier, CallbackInfo ci) {
        PipelinedRecipeIndex pipeline = forgepreheat$pipeline;
        if (pipeline != null && pipeline.isConsumer()) {
            forgepreheat$start = System.nanoTime();
            return;
        }
        PipelinedRecipeIndex queued = ParallelRecipeIndex.INSTANCE.queue((RecipeMap) (Object) this, pipeline, role,
                type, recipe, supplier);
        if (queued != null) {
            forgepreheat$pipeline = queued;
            ci.cancel();
            return;
        }
        if (pipeline != null) {
            pipeline.awaitDrained(); // A direct add after the start must not race the queue's last entries.
        }
        DeferredRecipeIndex existing = forgepreheat$deferred;
        if (existing == null || !existing.isReplaying()) {
            DeferredRecipeIndex index = LazyRenderOnlyIndex.INSTANCE.defer((RecipeMap) (Object) this, existing, role,
                    type, recipe, supplier);
            if (index != null) {
                forgepreheat$deferred = index;
                ci.cancel();
                return;
            }
        }
        forgepreheat$start = System.nanoTime();
    }

    @Inject(method = ADD, at = @At("RETURN"))
    private void forgepreheat$after(@SuppressWarnings("rawtypes") RecipeType type, Object recipe,
                                    IIngredientSupplier supplier, CallbackInfo ci) {
        RecipeTimings.INSTANCE.addIndexNanos(role, supplier, System.nanoTime() - forgepreheat$start);
    }

    @Inject(method = "compact()V", at = @At("HEAD"), cancellable = true)
    private void forgepreheat$compact(CallbackInfo ci) {
        PipelinedRecipeIndex pipeline = forgepreheat$pipeline;
        if (pipeline != null && !pipeline.isConsumer()) {
            pipeline.awaitDrained();
        }
        DeferredRecipeIndex index = forgepreheat$deferred;
        if (index != null && !index.isReplaying() && index.deferCompact()) {
            ci.cancel();
        }
    }

    @Inject(method = {
            "getRecipeTypes(Lmezz/jei/api/ingredients/ITypedIngredient;)Ljava/util/stream/Stream;",
            "getRecipes(Lmezz/jei/api/recipe/RecipeType;Lmezz/jei/api/ingredients/ITypedIngredient;)Ljava/util/List;",
            "isCatalystForRecipeCategory(Lmezz/jei/api/recipe/RecipeType;Lmezz/jei/api/ingredients/ITypedIngredient;)Z"
    }, at = @At("HEAD"))
    private void forgepreheat$read(CallbackInfoReturnable<?> cir) {
        forgepreheat$ensureBuilt();
    }

    @Inject(method = "addCatalystForCategory(Lmezz/jei/api/recipe/RecipeType;Lmezz/jei/api/ingredients/ITypedIngredient;)V",
            at = @At("HEAD"))
    private void forgepreheat$addCatalyst(CallbackInfo ci) {
        forgepreheat$ensureBuilt();
    }

    @Unique
    private void forgepreheat$ensureBuilt() {
        PipelinedRecipeIndex pipeline = forgepreheat$pipeline;
        if (pipeline != null && !pipeline.isConsumer()) {
            pipeline.awaitDrained();
        }
        DeferredRecipeIndex index = forgepreheat$deferred;
        if (index != null && !index.isReplaying()) {
            index.ensureBuilt();
        }
    }
}
