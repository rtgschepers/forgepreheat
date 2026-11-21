package forgepreheat.mixin.compat;

import forgepreheat.optimize.UtilitiXTabItems;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.IForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;

/** Targets UtilitiX 0.8.x; the optional compat config skips it if the handler changes. */
@Pseudo
@Mixin(targets = "de.melanx.utilitix.registration.ModCreativeTab", remap = false)
public abstract class UtilitiXCreativeTabMixin {
    @Redirect(method = "onCreateTabs",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/registries/IForgeRegistry;getValues()Ljava/util/Collection;"))
    private static Collection<Item> forgepreheat$ownItems(IForgeRegistry<Item> registry) {
        return UtilitiXTabItems.INSTANCE.values(registry);
    }
}
