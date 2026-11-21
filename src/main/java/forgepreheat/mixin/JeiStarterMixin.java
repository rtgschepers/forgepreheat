package forgepreheat.mixin;

import forgepreheat.JeiStartSession;
import mezz.jei.library.startup.JeiStarter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JeiStarter.class, remap = false)
public abstract class JeiStarterMixin {
    @Inject(method = "start", at = @At("HEAD"))
    private void forgepreheat$begin(CallbackInfo ci) {
        JeiStartSession.begin();
    }

    @Inject(method = "start", at = @At("RETURN"))
    private void forgepreheat$end(CallbackInfo ci) {
        JeiStartSession.end();
    }
}
