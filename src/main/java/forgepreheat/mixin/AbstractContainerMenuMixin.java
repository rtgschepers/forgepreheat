package forgepreheat.mixin;

import forgepreheat.optimize.FakeMenuSyncSkip;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Inject(method = "broadcastChanges", at = @At("HEAD"), cancellable = true)
    private void forgepreheat$skipFakeMenuSync(CallbackInfo ci) {
        Object self = this;
        if ((self instanceof ItemCombinerMenu || self instanceof GrindstoneMenu) && FakeMenuSyncSkip.INSTANCE.shouldSkip()) {
            ci.cancel();
        }
    }
}
