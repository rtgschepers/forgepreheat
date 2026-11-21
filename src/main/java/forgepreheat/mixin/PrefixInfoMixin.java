package forgepreheat.mixin;

import forgepreheat.optimize.BackgroundSearchIndex;
import mezz.jei.common.search.PrefixInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PrefixInfo.class, remap = false)
public abstract class PrefixInfoMixin {
    @Inject(method = "getStrings(Ljava/lang/Object;)Ljava/util/Collection;", at = @At("HEAD"))
    private void forgepreheat$stopAbandonedBuild(CallbackInfoReturnable<?> cir) {
        BackgroundSearchIndex.INSTANCE.checkBuildCancelled();
    }
}
