package forgepreheat.mixin;

import forgepreheat.optimize.FakeMenuSyncSkip;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.library.plugins.vanilla.VanillaPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VanillaPlugin.class, remap = false)
public abstract class VanillaPluginMixin {
    @Inject(method = "registerRecipes", at = @At("HEAD"))
    private void forgepreheat$enter(IRecipeRegistration registration, CallbackInfo ci) {
        FakeMenuSyncSkip.INSTANCE.enterVanillaRecipes();
    }

    @Inject(method = "registerRecipes", at = @At("RETURN"))
    private void forgepreheat$exit(IRecipeRegistration registration, CallbackInfo ci) {
        FakeMenuSyncSkip.INSTANCE.exitVanillaRecipes();
    }
}
