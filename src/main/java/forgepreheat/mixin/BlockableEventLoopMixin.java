package forgepreheat.mixin;

import forgepreheat.optimize.AsyncJeiStart;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {
    @Inject(method = "managedBlock", at = @At("HEAD"), cancellable = true)
    private void forgepreheat$waitWithoutDraining(BooleanSupplier done, CallbackInfo ci) {
        if (AsyncJeiStart.INSTANCE.waitWithoutDraining(this, done)) {
            ci.cancel();
        }
    }
}
