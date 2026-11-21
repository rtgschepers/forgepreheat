package forgepreheat.mixin.compat;

import forgepreheat.optimize.PowahWikiRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Targets Powah 5.0.x (its wiki's RecipesUpdated lambdas); the optional compat config skips it if they change. */
@Pseudo
@Mixin(targets = "owmii.powah.lib.client.wiki.Wiki", remap = false)
public abstract class PowahWikiMixin {
    private static final String UPDATE = "lambda$static$5(Lnet/minecraft/world/item/crafting/RecipeManager;)V";

    @Inject(method = UPDATE, at = @At("HEAD"))
    private static void forgepreheat$start(RecipeManager manager, CallbackInfo ci) {
        PowahWikiRecipes.INSTANCE.onUpdateStart();
    }

    @Inject(method = UPDATE, at = @At("RETURN"))
    private static void forgepreheat$end(RecipeManager manager, CallbackInfo ci) {
        PowahWikiRecipes.INSTANCE.onUpdateEnd();
    }

    @Redirect(method = "lambda$static$3", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/item/crafting/RecipeManager;getAllRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;)Ljava/util/List;"))
    private static List<CraftingRecipe> forgepreheat$recipesFor(RecipeManager manager, RecipeType<CraftingRecipe> type,
                                                               RecipeManager sameManager, RegistryAccess registries,
                                                               @Coerce Object wiki, Item item) {
        return PowahWikiRecipes.INSTANCE.craftingRecipesFor(manager, type, registries, item);
    }
}
