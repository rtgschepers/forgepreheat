package forgepreheat.mixin;

import forgepreheat.profile.StepTimings;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.library.plugins.vanilla.VanillaPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = VanillaPlugin.class, remap = false)
public abstract class VanillaPluginMixin {
    private static final String TOTAL = "jei:minecraft recipes";
    private static final String ADD_RECIPES =
            "Lmezz/jei/api/registration/IRecipeRegistration;addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V";

    @Inject(method = "registerRecipes", at = @At("HEAD"))
    private void forgepreheat$enter(IRecipeRegistration registration, CallbackInfo ci) {
        StepTimings.INSTANCE.enter(TOTAL);
        StepTimings.INSTANCE.startLaps();
    }

    // Time since the previous add is the work that made this recipe type's list.
    @Redirect(method = "registerRecipes", at = @At(value = "INVOKE", target = ADD_RECIPES))
    private <T> void forgepreheat$timeAdd(IRecipeRegistration registration, RecipeType<T> type, List<T> recipes) {
        StepTimings.INSTANCE.lap("jei:minecraft > make ", type.getUid());
        registration.addRecipes(type, recipes);
        StepTimings.INSTANCE.lap("jei:minecraft > add ", type.getUid());
    }

    @Inject(method = "registerRecipes", at = @At("RETURN"))
    private void forgepreheat$exit(IRecipeRegistration registration, CallbackInfo ci) {
        StepTimings.INSTANCE.lap("jei:minecraft > ", "after last add");
        StepTimings.INSTANCE.exit(TOTAL);
    }
}
