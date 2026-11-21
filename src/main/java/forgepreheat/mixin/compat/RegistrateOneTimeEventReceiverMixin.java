package forgepreheat.mixin.compat;

import forgepreheat.optimize.RegistrateCleanupSkip;
import forgepreheat.profile.StartupTimings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Consumer;

/** Targets Registrate 1.3.x; the task is the lambda onLoadComplete enqueues. The compat config skips it if it changes. */
@Pseudo
@Mixin(targets = "com.tterrag.registrate.util.OneTimeEventReceiver", remap = false)
public abstract class RegistrateOneTimeEventReceiverMixin {
    // Registrate clears the list right after this forEach, so skipping it leaves the same end state.
    @Redirect(method = "lambda$onLoadComplete$1()V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void forgepreheat$unregisterAll(List pending, Consumer unregister) {
        if (RegistrateCleanupSkip.INSTANCE.skip(pending.size())) {
            return;
        }
        long start = System.nanoTime();
        pending.forEach(unregister);
        StartupTimings.INSTANCE.add("Registrate cleanup of " + pending.size() + " listeners", System.nanoTime() - start);
    }
}
