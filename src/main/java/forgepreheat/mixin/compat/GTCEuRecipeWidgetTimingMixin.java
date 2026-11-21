package forgepreheat.mixin.compat;

import forgepreheat.profile.StepTimings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Targets GTCEu 7.5.x; the optional compat config skips each method that changes. */
@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget", remap = false)
public abstract class GTCEuRecipeWidgetTimingMixin {
    private static final String SLOTS = "gtceu recipes > widget slots";
    private static final String TEXT = "gtceu recipes > widget text";
    private static final String BUTTONS = "gtceu recipes > widget buttons";

    @Inject(method = "setRecipeWidget()V", at = @At("HEAD"))
    private void forgepreheat$enterSlots(CallbackInfo ci) {
        StepTimings.INSTANCE.enter(SLOTS);
    }

    @Inject(method = "setRecipeWidget()V", at = @At("RETURN"))
    private void forgepreheat$exitSlots(CallbackInfo ci) {
        StepTimings.INSTANCE.exit(SLOTS);
    }

    @Inject(method = "initializeRecipeTextWidget()V", at = @At("HEAD"))
    private void forgepreheat$enterText(CallbackInfo ci) {
        StepTimings.INSTANCE.enter(TEXT);
    }

    @Inject(method = "initializeRecipeTextWidget()V", at = @At("RETURN"))
    private void forgepreheat$exitText(CallbackInfo ci) {
        StepTimings.INSTANCE.exit(TEXT);
    }

    @Inject(method = "addButtons()V", at = @At("HEAD"))
    private void forgepreheat$enterButtons(CallbackInfo ci) {
        StepTimings.INSTANCE.enter(BUTTONS);
    }

    @Inject(method = "addButtons()V", at = @At("RETURN"))
    private void forgepreheat$exitButtons(CallbackInfo ci) {
        StepTimings.INSTANCE.exit(BUTTONS);
    }
}
