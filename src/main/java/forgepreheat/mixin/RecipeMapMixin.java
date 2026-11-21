package forgepreheat.mixin;

import forgepreheat.profile.RecipeTimings;
import mezz.jei.library.recipes.collect.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RecipeMap.class, remap = false)
public abstract class RecipeMapMixin {
    private static final String TARGET = "addRecipe(Lmezz/jei/api/recipe/RecipeType;Ljava/lang/Object;"
            + "Lmezz/jei/library/ingredients/IIngredientSupplier;)V";

    @Unique
    private long forgepreheat$start;

    @Inject(method = TARGET, at = @At("HEAD"))
    private void forgepreheat$before(CallbackInfo ci) {
        forgepreheat$start = System.nanoTime();
    }

    @Inject(method = TARGET, at = @At("RETURN"))
    private void forgepreheat$after(CallbackInfo ci) {
        RecipeTimings.INSTANCE.addIndexNanos(System.nanoTime() - forgepreheat$start);
    }
}
