package forgepreheat.mixin.compat;

import forgepreheat.optimize.PureOreRecipeIndex;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.stream.Stream;

/** Targets ElementalCraft 6.0.x; the optional compat config skips it if getRecipe changes. */
@Pseudo
@Mixin(targets = "sirttas.elementalcraft.api.pureore.injector.AbstractPureOreRecipeInjector", remap = false)
public abstract class ElementalCraftPureOreInjectorMixin {
    @Unique
    private PureOreRecipeIndex.Candidates forgepreheat$index;

    @Redirect(method = "getRecipe(Lnet/minecraft/world/item/Item;)Ljava/util/Optional;",
            at = @At(value = "INVOKE", target = "Ljava/util/Collection;stream()Ljava/util/stream/Stream;"))
    private <T> Stream<T> forgepreheat$candidates(Collection<T> recipes, Item item) {
        if (!PureOreRecipeIndex.INSTANCE.appliesTo(this)) {
            return recipes.stream();
        }
        forgepreheat$index = PureOreRecipeIndex.INSTANCE.indexFor(forgepreheat$index, recipes);
        return PureOreRecipeIndex.INSTANCE.candidates(forgepreheat$index, item);
    }
}
