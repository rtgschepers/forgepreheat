package forgepreheat.mixin;

import forgepreheat.JeiStartSession;
import forgepreheat.optimize.AsyncJeiStart;
import forgepreheat.optimize.BackgroundSearchIndex;
import mezz.jei.library.startup.JeiStarter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JeiStarter.class, remap = false)
public abstract class JeiStarterMixin {
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void forgepreheat$begin(CallbackInfo ci) {
        if (AsyncJeiStart.INSTANCE.dispatch((JeiStarter) (Object) this)) {
            ci.cancel();
            return;
        }
        JeiStartSession.begin();
    }

    @Inject(method = "start", at = @At("RETURN"))
    private void forgepreheat$end(CallbackInfo ci) {
        AsyncJeiStart.INSTANCE.onStartReturned();
        JeiStartSession.end();
    }

    @Inject(method = "stop", at = @At("HEAD"), cancellable = true)
    private void forgepreheat$stop(CallbackInfo ci) {
        if (AsyncJeiStart.INSTANCE.deferStop((JeiStarter) (Object) this)) {
            ci.cancel();
            return;
        }
        BackgroundSearchIndex.INSTANCE.onJeiStopped();
    }
}
