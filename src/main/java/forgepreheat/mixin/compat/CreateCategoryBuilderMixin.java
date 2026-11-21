package forgepreheat.mixin.compat;

import forgepreheat.optimize.CreateExclusionIndex;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Targets Create 6.0.x; the optional compat config skips it if Create renames this lambda. */
@Pseudo
@Mixin(targets = "com.simibubi.create.compat.jei.CreateJEI$CategoryBuilder", remap = false)
public abstract class CreateCategoryBuilderMixin {
    // Create's body: for each excluded recipe, if doInputsMatch(recipe, excluded) return; then add the recipe.
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "lambda$addTypedRecipesExcluding$12", at = @At("HEAD"), cancellable = true)
    private static void forgepreheat$indexedExclusion(List excluded, List result, Recipe recipe, CallbackInfo ci) {
        Boolean isExcluded = CreateExclusionIndex.INSTANCE.isExcluded(excluded, recipe);
        if (isExcluded == null) {
            return;
        }
        if (!isExcluded) {
            result.add(recipe);
        }
        ci.cancel();
    }
}
