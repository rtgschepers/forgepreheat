package forgepreheat.mixin;

import forgepreheat.optimize.FilterListenerNotifier;
import forgepreheat.optimize.BackgroundSearchIndex;
import forgepreheat.optimize.FilterUpdateBatching;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.IElementSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = IngredientFilter.class, remap = false)
public abstract class IngredientFilterMixin implements FilterListenerNotifier {
    private static final String TARGET = "createElementSearch(Lmezz/jei/common/config/IClientConfig;"
            + "Lmezz/jei/gui/search/ElementPrefixParser;Ljava/util/List;Lmezz/jei/api/runtime/IIngredientManager;)"
            + "Lmezz/jei/gui/search/IElementSearch;";

    @Inject(method = TARGET, at = @At("HEAD"), cancellable = true)
    private static void forgepreheat$buildInBackground(IClientConfig config, ElementPrefixParser parser,
                                                       List<IListElementInfo<?>> infos, IIngredientManager manager,
                                                       CallbackInfoReturnable<IElementSearch> cir) {
        IElementSearch search = BackgroundSearchIndex.INSTANCE.create(
                config.isLowMemorySlowSearchEnabled(), parser, infos, manager);
        if (search != null) {
            cir.setReturnValue(search);
        }
    }

    @Shadow
    private void notifyListenersOfChange() {
        throw new AssertionError();
    }

    @Inject(method = "notifyListenersOfChange()V", at = @At("HEAD"), cancellable = true)
    private void forgepreheat$batchNotify(CallbackInfo ci) {
        if (FilterUpdateBatching.INSTANCE.holdBack(this)) {
            ci.cancel();
        }
    }

    @Override
    public void forgepreheat$notifyListeners() {
        notifyListenersOfChange();
    }
}
