package forgepreheat.mixin.compat;

import forgepreheat.optimize.KubeJSHideLists;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/** Targets KubeJS 2001.6.x; the optional compat config skips it if the field changes. */
@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.integration.forge.jei.HideJEIEventJS", remap = false)
public abstract class KubeJSHideEventMixin {
    @Shadow
    @Final
    @Mutable
    private Collection<Object> allIngredients;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void forgepreheat$shareItemList(CallbackInfo ci) {
        allIngredients = KubeJSHideLists.INSTANCE.prepare(allIngredients);
    }
}
