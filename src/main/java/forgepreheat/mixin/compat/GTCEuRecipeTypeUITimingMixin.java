package forgepreheat.mixin.compat;

import forgepreheat.profile.StepTimings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Targets GTCEu 7.5.x; the optional compat config skips each method that changes. */
@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI", remap = false)
public abstract class GTCEuRecipeTypeUITimingMixin {
    // The four-argument overload delegates here, so timing this one covers both.
    private static final String TEMPLATE = "createUITemplate(Ljava/util/function/DoubleSupplier;"
            + "Lcom/google/common/collect/Table;Lnet/minecraft/nbt/CompoundTag;Ljava/util/List;ZZ)"
            + "Lcom/lowdragmc/lowdraglib/gui/widget/WidgetGroup;";
    private static final String EDITABLE = "createEditableUITemplate(ZZ)Lcom/gregtechceu/gtceu/api/gui/editor/IEditableUI;";
    private static final String TEMPLATE_STEP = "gtceu recipes > widget slots > UI template";
    private static final String EDITABLE_STEP = "gtceu recipes > widget slots > UI template > editable template";

    @Inject(method = TEMPLATE, at = @At("HEAD"))
    private void forgepreheat$enterTemplate(CallbackInfoReturnable<?> cir) {
        StepTimings.INSTANCE.enter(TEMPLATE_STEP);
    }

    @Inject(method = TEMPLATE, at = @At("RETURN"))
    private void forgepreheat$exitTemplate(CallbackInfoReturnable<?> cir) {
        StepTimings.INSTANCE.exit(TEMPLATE_STEP);
    }

    @Inject(method = EDITABLE, at = @At("HEAD"))
    private void forgepreheat$enterEditable(CallbackInfoReturnable<?> cir) {
        StepTimings.INSTANCE.enter(EDITABLE_STEP);
    }

    @Inject(method = EDITABLE, at = @At("RETURN"))
    private void forgepreheat$exitEditable(CallbackInfoReturnable<?> cir) {
        StepTimings.INSTANCE.exit(EDITABLE_STEP);
    }
}
