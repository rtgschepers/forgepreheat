package forgepreheat.mixin.compat;

import forgepreheat.profile.StepTimings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Targets GTCEu 7.5.x; the optional compat config skips it if the method changes. */
@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory", remap = false)
public abstract class GTCEuMultiblockCategoryTimingMixin {
    private static final String TARGET = "registerRecipes(Lmezz/jei/api/registration/IRecipeRegistration;)V";
    private static final String STEP = "gtceu multiblock";

    @Inject(method = TARGET, at = @At("HEAD"))
    private static void forgepreheat$enter(CallbackInfo ci) {
        StepTimings.INSTANCE.enter(STEP);
    }

    @Inject(method = TARGET, at = @At("RETURN"))
    private static void forgepreheat$exit(CallbackInfo ci) {
        StepTimings.INSTANCE.exit(STEP);
    }
}
