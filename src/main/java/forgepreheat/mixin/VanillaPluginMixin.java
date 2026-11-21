package forgepreheat.mixin;

import forgepreheat.optimize.ParallelVanillaMakers;
import forgepreheat.profile.StepTimings;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.platform.IPlatformRecipeHelper;
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
    private static final String ANVIL_MAKER = "Lmezz/jei/library/plugins/vanilla/anvil/AnvilRecipeMaker;getAnvilRecipes("
            + "Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/List;";
    private static final String GRINDSTONE_MAKER = "Lmezz/jei/library/plugins/vanilla/grindstone/GrindstoneRecipeMaker;"
            + "getGrindstoneRecipes(Lmezz/jei/api/runtime/IIngredientManager;"
            + "Lmezz/jei/common/platform/IPlatformRecipeHelper;)Ljava/util/List;";

    @Inject(method = "registerRecipes", at = @At("HEAD"))
    private void forgepreheat$enter(IRecipeRegistration registration, CallbackInfo ci) {
        StepTimings.INSTANCE.enter(TOTAL);
        StepTimings.INSTANCE.startLaps();
        ParallelVanillaMakers.INSTANCE.start(registration.getVanillaRecipeFactory(), registration.getIngredientManager());
    }

    @Redirect(method = "registerRecipes", at = @At(value = "INVOKE", target = ANVIL_MAKER))
    private List<IJeiAnvilRecipe> forgepreheat$anvil(IVanillaRecipeFactory factory, IIngredientManager ingredients) {
        return ParallelVanillaMakers.INSTANCE.anvilRecipes(factory, ingredients);
    }

    @Redirect(method = "registerRecipes", at = @At(value = "INVOKE", target = GRINDSTONE_MAKER))
    private List<IJeiGrindstoneRecipe> forgepreheat$grindstone(IIngredientManager ingredients,
                                                               IPlatformRecipeHelper helper) {
        return ParallelVanillaMakers.INSTANCE.grindstoneRecipes(ingredients, helper);
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
