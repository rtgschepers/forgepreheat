package forgepreheat.mixin;

import forgepreheat.profile.RecipeTimings;
import mezz.jei.library.util.IngredientSupplierHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IngredientSupplierHelper.class, remap = false)
public abstract class IngredientSupplierHelperMixin {
    private static final String TARGET = "getIngredientSupplier(Ljava/lang/Object;Lmezz/jei/api/recipe/category/IRecipeCategory;"
            + "Lmezz/jei/api/runtime/IIngredientManager;)Lmezz/jei/library/ingredients/IIngredientSupplier;";

    @Unique
    private static long forgepreheat$start;

    @Inject(method = TARGET, at = @At("HEAD"))
    private static void forgepreheat$before(CallbackInfoReturnable<?> cir) {
        forgepreheat$start = System.nanoTime();
    }

    @Inject(method = TARGET, at = @At("RETURN"))
    private static void forgepreheat$after(CallbackInfoReturnable<?> cir) {
        RecipeTimings.INSTANCE.addSetupNanos(System.nanoTime() - forgepreheat$start);
    }
}
