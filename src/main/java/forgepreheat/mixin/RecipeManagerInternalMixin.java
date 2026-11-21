package forgepreheat.mixin;

import forgepreheat.profile.RecipeIdDump;
import forgepreheat.profile.RecipeTimings;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.library.recipes.RecipeManagerInternal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(value = RecipeManagerInternal.class, remap = false)
public abstract class RecipeManagerInternalMixin {
    @Unique
    private long forgepreheat$start;

    @Inject(method = "addRecipe", at = @At("HEAD"))
    private void forgepreheat$before(IRecipeCategory<?> category, Object recipe, Set<?> hidden, CallbackInfoReturnable<Boolean> cir) {
        forgepreheat$start = System.nanoTime();
    }

    @Inject(method = "addRecipe", at = @At("RETURN"))
    private void forgepreheat$after(IRecipeCategory<?> category, Object recipe, Set<?> hidden, CallbackInfoReturnable<Boolean> cir) {
        RecipeTimings.INSTANCE.record(category.getRecipeType().getUid(), recipe, System.nanoTime() - forgepreheat$start);
        RecipeIdDump.INSTANCE.record(category, recipe, cir.getReturnValue());
    }
}
