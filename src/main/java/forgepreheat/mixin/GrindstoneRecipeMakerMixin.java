package forgepreheat.mixin;

import forgepreheat.optimize.GrindstoneStackReuse;
import forgepreheat.profile.StepTimings;
import mezz.jei.library.plugins.vanilla.grindstone.GrindstoneRecipeMaker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GrindstoneRecipeMaker.class, remap = false)
public abstract class GrindstoneRecipeMakerMixin {
    private static final String DISENCHANT = "getDisenchantRecipes(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
            + "Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;";
    private static final String CAN_ENCHANT = "canEnchant(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/Enchantment;"
            + "Lnet/minecraft/resources/ResourceLocation;)Z";
    private static final String MENU_RESULT = "getGrindstoneRecipe(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
            + "Lnet/minecraft/world/inventory/GrindstoneMenu;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;)"
            + "Lmezz/jei/api/recipe/vanilla/IJeiGrindstoneRecipe;";

    private static final String LOOP_STEP = "grindstone > disenchant loop";
    private static final String STACK_STEP = "grindstone > disenchant loop > new stack";
    private static final String ENCHANTABLE_STEP = "grindstone > disenchant loop > isEnchantable";
    private static final String CAN_ENCHANT_STEP = "grindstone > canEnchant";
    private static final String MENU_STEP = "grindstone > menu result (disenchant + repair)";

    @Inject(method = DISENCHANT, at = @At("HEAD"))
    private static void forgepreheat$enterLoop(CallbackInfoReturnable<?> cir) {
        StepTimings.INSTANCE.enter(LOOP_STEP);
    }

    @Inject(method = DISENCHANT, at = @At("RETURN"))
    private static void forgepreheat$exitLoop(CallbackInfoReturnable<?> cir) {
        StepTimings.INSTANCE.exit(LOOP_STEP);
    }

    @Redirect(method = DISENCHANT, at = @At(value = "NEW",
            target = "(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack forgepreheat$stack(ItemLike item) {
        StepTimings.INSTANCE.enter(STACK_STEP);
        ItemStack stack = GrindstoneStackReuse.INSTANCE.stackFor(item);
        StepTimings.INSTANCE.exit(STACK_STEP);
        return stack;
    }

    @Redirect(method = DISENCHANT, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;isEnchantable()Z", remap = true))
    private static boolean forgepreheat$isEnchantable(ItemStack stack) {
        StepTimings.INSTANCE.enter(ENCHANTABLE_STEP);
        boolean result = stack.isEnchantable();
        StepTimings.INSTANCE.exit(ENCHANTABLE_STEP);
        return result;
    }

    @Inject(method = CAN_ENCHANT, at = @At("HEAD"))
    private static void forgepreheat$enterCanEnchant(CallbackInfoReturnable<Boolean> cir) {
        StepTimings.INSTANCE.enter(CAN_ENCHANT_STEP);
    }

    @Inject(method = CAN_ENCHANT, at = @At("RETURN"))
    private static void forgepreheat$exitCanEnchant(CallbackInfoReturnable<Boolean> cir) {
        StepTimings.INSTANCE.exit(CAN_ENCHANT_STEP);
    }

    @Inject(method = MENU_RESULT, at = @At("HEAD"))
    private static void forgepreheat$enterMenu(CallbackInfoReturnable<?> cir) {
        StepTimings.INSTANCE.enter(MENU_STEP);
    }

    @Inject(method = MENU_RESULT, at = @At("RETURN"))
    private static void forgepreheat$exitMenu(CallbackInfoReturnable<?> cir) {
        StepTimings.INSTANCE.exit(MENU_STEP);
    }
}
