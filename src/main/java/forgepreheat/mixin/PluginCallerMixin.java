package forgepreheat.mixin;

import forgepreheat.optimize.AsyncJeiStart;
import mezz.jei.api.IModPlugin;
import mezz.jei.library.load.PluginCaller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

@Mixin(value = PluginCaller.class, remap = false)
public abstract class PluginCallerMixin {
    private static final String TARGET = "callOnPlugins(Ljava/lang/String;Ljava/util/List;Ljava/util/function/Consumer;)V";

    @Inject(method = TARGET, at = @At("HEAD"), cancellable = true)
    private static void forgepreheat$mainThreadStep(String title, List<IModPlugin> plugins, Consumer<IModPlugin> func,
                                                    CallbackInfo ci) {
        if (AsyncJeiStart.INSTANCE.runStepOnMainThread(title, () -> PluginCaller.callOnPlugins(title, plugins, func))) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = TARGET, at = @At("HEAD"), argsOnly = true)
    private static Consumer<IModPlugin> forgepreheat$wrapPluginCalls(Consumer<IModPlugin> func) {
        return AsyncJeiStart.INSTANCE.wrapPluginCalls(func);
    }
}
