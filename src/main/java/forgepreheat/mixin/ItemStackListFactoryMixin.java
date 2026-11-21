package forgepreheat.mixin;

import forgepreheat.optimize.CreativeTabBuildLock;
import mezz.jei.library.plugins.vanilla.ingredients.ItemStackListFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;
import java.util.stream.Stream;

@Mixin(value = ItemStackListFactory.class, remap = false)
public abstract class ItemStackListFactoryMixin {
    // The forEach over the creative tab registry is where JEI builds every tab's contents.
    @Redirect(method = "create", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;forEach(Ljava/util/function/Consumer;)V"))
    private static <T> void forgepreheat$buildTabsLocked(Stream<T> tabs, Consumer<? super T> buildTab) {
        CreativeTabBuildLock.runLocked(() -> tabs.forEach(buildTab));
    }
}
