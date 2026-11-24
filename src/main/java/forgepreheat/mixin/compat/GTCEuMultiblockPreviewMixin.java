package forgepreheat.mixin.compat;

import forgepreheat.optimize.AsyncJeiStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.stream.Stream;

/**
 * LDLib only builds a SceneWidget's renderer on the render thread, so previews made on the async JEI worker stay empty.
 * The stream is lazy, so its toList() is where GTCEu builds every preview; targets GTCEu 7.5.x.
 */
@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory", remap = false)
public abstract class GTCEuMultiblockPreviewMixin {
    @Redirect(method = "registerRecipes(Lmezz/jei/api/registration/IRecipeRegistration;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;toList()Ljava/util/List;"))
    private static List<?> forgepreheat$buildOnMainThread(Stream<?> wrappers) {
        return AsyncJeiStart.INSTANCE.callOnMainThread(wrappers::toList);
    }
}
