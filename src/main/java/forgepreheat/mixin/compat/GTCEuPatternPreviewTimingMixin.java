package forgepreheat.mixin.compat;

import forgepreheat.profile.StepTimings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Targets GTCEu 7.5.x; the optional compat config skips it if the method changes. */
@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.api.gui.widget.PatternPreviewWidget", remap = false)
public abstract class GTCEuPatternPreviewTimingMixin {
    private static final String TARGET = "getPatternWidget(Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;)"
            + "Lcom/gregtechceu/gtceu/api/gui/widget/PatternPreviewWidget;";
    private static final String STEP = "gtceu multiblock > preview widgets";

    @Inject(method = TARGET, at = @At("HEAD"))
    private static void forgepreheat$enter(CallbackInfoReturnable<?> cir) {
        StepTimings.INSTANCE.enter(STEP);
    }

    @Inject(method = TARGET, at = @At("RETURN"))
    private static void forgepreheat$exit(CallbackInfoReturnable<?> cir) {
        StepTimings.INSTANCE.exit(STEP);
    }
}
