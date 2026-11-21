package forgepreheat.mixin;

import forgepreheat.optimize.CreativeTabBuildLock;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
    private static final String REBUILD = "Lnet/minecraft/world/item/CreativeModeTabs;tryRebuildTabContents("
            + "Lnet/minecraft/world/flag/FeatureFlagSet;ZLnet/minecraft/core/HolderLookup$Provider;)Z";

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = REBUILD))
    private boolean forgepreheat$rebuildOnOpen(FeatureFlagSet features, boolean operatorItems,
                                               HolderLookup.Provider registries) {
        return CreativeTabBuildLock.rebuildLocked(
                () -> CreativeModeTabs.tryRebuildTabContents(features, operatorItems, registries));
    }

    @Redirect(method = "tryRefreshInvalidatedTabs", at = @At(value = "INVOKE", target = REBUILD))
    private boolean forgepreheat$rebuildOnRefresh(FeatureFlagSet features, boolean operatorItems,
                                                  HolderLookup.Provider registries) {
        return CreativeTabBuildLock.rebuildLocked(
                () -> CreativeModeTabs.tryRebuildTabContents(features, operatorItems, registries));
    }
}
