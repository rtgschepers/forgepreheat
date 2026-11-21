package forgepreheat.mixin;

import forgepreheat.optimize.ObjectHolderIndex;
import forgepreheat.profile.StartupTimings;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ObjectHolderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Mixin(value = ObjectHolderRegistry.class, remap = false)
public abstract class ObjectHolderRegistryMixin {
    private static final String APPLY = "applyObjectHolders(Ljava/util/function/Predicate;)V";

    // perHolder is Forge's own lambda (accept + collect exceptions), so error handling stays Forge's.
    @Redirect(method = APPLY, at = @At(value = "INVOKE", target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V"))
    private static void forgepreheat$indexedPass(Set<Consumer<Predicate<ResourceLocation>>> holders,
                                                 Consumer<Consumer<Predicate<ResourceLocation>>> perHolder,
                                                 Predicate<ResourceLocation> filter) {
        long start = System.nanoTime();
        ObjectHolderIndex.INSTANCE.forEach(holders, filter, perHolder);
        StartupTimings.INSTANCE.add("object holder passes", System.nanoTime() - start);
    }

    @Inject(method = "addHandler", at = @At("HEAD"))
    private static void forgepreheat$added(Consumer<Predicate<ResourceLocation>> handler, CallbackInfo ci) {
        ObjectHolderIndex.INSTANCE.invalidate();
    }

    @Inject(method = "removeHandler", at = @At("HEAD"))
    private static void forgepreheat$removed(Consumer<Predicate<ResourceLocation>> handler,
                                             CallbackInfoReturnable<Boolean> cir) {
        ObjectHolderIndex.INSTANCE.invalidate();
    }
}
