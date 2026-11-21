package forgepreheat.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;

/** Turns JEI's recipe objects into the names and input details the report shows. */
public final class RecipeInspector {
    private static final String CLASS_PREFIX = "class:";

    private RecipeInspector() {}

    /** The recipe id, or the class name for JEI-only wrapper recipes that have none. */
    public static String recipeId(Object recipe) {
        if (recipe instanceof Recipe<?> r) {
            try {
                return String.valueOf(r.getId());
            } catch (RuntimeException ignored) {
                // Some generated recipes throw from getId; the class name still identifies them.
            }
        }
        return CLASS_PREFIX + recipe.getClass().getName();
    }

    /** The mod a recipe comes from: its id namespace, or its package root for wrapper recipes. */
    public static String source(String recipeId) {
        if (recipeId.startsWith(CLASS_PREFIX)) {
            String[] parts = recipeId.substring(CLASS_PREFIX.length()).split("\\.");
            return "class " + String.join(".", Arrays.copyOf(parts, Math.min(3, parts.length - 1)));
        }
        int colon = recipeId.indexOf(':');
        return colon > 0 ? recipeId.substring(0, colon) : recipeId;
    }

    /** Input stack count and NBT size; only called for the slowest recipes since getItems() is not free. */
    public static String describeInputs(Object recipe) {
        if (!(recipe instanceof Recipe<?> r)) {
            return "inputs: n/a (" + recipe.getClass().getName() + ")";
        }
        try {
            int stacks = 0;
            int maxDepth = 0;
            int maxNbtChars = 0;
            String heaviest = "-";
            for (Ingredient ing : r.getIngredients()) {
                for (ItemStack stack : ing.getItems()) {
                    stacks++;
                    CompoundTag tag = stack.getTag();
                    if (tag == null) {
                        continue;
                    }
                    maxDepth = Math.max(maxDepth, nbtDepth(tag));
                    int chars = tag.toString().length();
                    if (chars > maxNbtChars) {
                        maxNbtChars = chars;
                        heaviest = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
                    }
                }
            }
            return String.format("inputs: %d stacks, max NBT depth %d, max NBT %d chars (%s)", stacks, maxDepth, maxNbtChars, heaviest);
        } catch (RuntimeException e) {
            return "inputs: error " + e;
        }
    }

    static int nbtDepth(Tag tag) {
        Iterable<? extends Tag> children;
        if (tag instanceof CompoundTag compound) {
            children = compound.getAllKeys().stream().map(compound::get).toList();
        } else if (tag instanceof ListTag list) {
            children = list;
        } else {
            return 0;
        }
        int deepest = 0;
        for (Tag child : children) {
            deepest = Math.max(deepest, nbtDepth(child));
        }
        return deepest + 1;
    }
}
