package forgepreheat.mixin.compat;

import forgepreheat.optimize.PureOreRecipeIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times ElementalCraft's pure-ore generation around the recipe index. */
@Pseudo
@Mixin(targets = "sirttas.elementalcraft.pureore.PureOreManager", remap = false)
public abstract class ElementalCraftPureOreManagerMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private void forgepreheat$start(CallbackInfo ci) {
        PureOreRecipeIndex.INSTANCE.onReloadStart();
    }

    @Inject(method = "reload", at = @At("RETURN"))
    private void forgepreheat$end(CallbackInfo ci) {
        PureOreRecipeIndex.INSTANCE.onReloadEnd();
    }
}
