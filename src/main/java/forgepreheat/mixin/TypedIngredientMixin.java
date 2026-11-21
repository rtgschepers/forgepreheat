package forgepreheat.mixin;

import forgepreheat.optimize.IngredientListCache;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.ingredients.TypedIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = TypedIngredient.class, remap = false)
public abstract class TypedIngredientMixin {
    private static final String TARGET =
            "createAndFilterInvalidList(Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/List;Z)Ljava/util/List;";

    @SuppressWarnings("unchecked")
    @Inject(method = TARGET, at = @At("HEAD"), cancellable = true)
    private static void forgepreheat$useCached(IIngredientManager manager, IIngredientType<?> type, List<?> ingredients,
                                                  boolean normalize, CallbackInfoReturnable<List<ITypedIngredient<?>>> cir) {
        List<?> cached = IngredientListCache.INSTANCE.lookup(type, ingredients, normalize);
        if (cached != null) {
            cir.setReturnValue((List<ITypedIngredient<?>>) cached);
        }
    }

    @Inject(method = TARGET, at = @At("RETURN"))
    private static void forgepreheat$store(IIngredientManager manager, IIngredientType<?> type, List<?> ingredients,
                                              boolean normalize, CallbackInfoReturnable<List<ITypedIngredient<?>>> cir) {
        IngredientListCache.INSTANCE.store(type, ingredients, normalize, cir.getReturnValue());
    }
}
