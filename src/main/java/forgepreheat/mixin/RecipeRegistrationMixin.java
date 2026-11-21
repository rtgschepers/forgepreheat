package forgepreheat.mixin;

import forgepreheat.profile.PluginStepTimings;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.library.load.registration.RecipeRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = RecipeRegistration.class, remap = false)
public abstract class RecipeRegistrationMixin {
    private static final String ADD = "addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V";

    @Inject(method = ADD, at = @At("HEAD"))
    private void forgepreheat$before(RecipeType<?> type, List<?> recipes, CallbackInfo ci) {
        PluginStepTimings.INSTANCE.beforeAdd(type);
    }

    @Inject(method = ADD, at = @At("RETURN"))
    private void forgepreheat$after(RecipeType<?> type, List<?> recipes, CallbackInfo ci) {
        PluginStepTimings.INSTANCE.afterAdd(type);
    }
}
