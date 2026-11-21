package forgepreheat.mixin.compat;

import forgepreheat.optimize.JerLootPrefetch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Targets Just Enough Resources 1.4.x; the optional compat config skips it if the method changes. */
@Pseudo
@Mixin(targets = "jeresources.util.LootTableHelper", remap = false)
public abstract class JerLootTableHelperMixin {
    @Inject(method = "getLootDataManager()Lnet/minecraft/world/level/storage/loot/LootDataManager;", at = @At("HEAD"))
    private static void forgepreheat$awaitPrefetch(CallbackInfoReturnable<Object> cir) {
        JerLootPrefetch.INSTANCE.awaitIfRunning();
    }
}
