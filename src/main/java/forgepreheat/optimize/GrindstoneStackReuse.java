package forgepreheat.optimize;

import forgepreheat.core.ScopedFeature;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * JEI's grindstone recipe maker creates a fresh ItemStack for every (enchantment, item) pair just to test whether the
 * item takes the enchantment; in this pack that is millions of stacks, each firing capability events and other mods'
 * constructor code. This hands out one stack per item instead. A pristine twin is kept, and when mod code has changed
 * the shared stack it is replaced by a new one, as JEI would have created.
 * Off by default: measured in 0.6.0, making the grindstone recipes took 4.81 s with it vs 4.75 s without.
 */
public final class GrindstoneStackReuse extends ScopedFeature {
    public static final GrindstoneStackReuse INSTANCE = new GrindstoneStackReuse();

    private static final class Entry {
        final ItemStack pristine;
        ItemStack shared;

        Entry(ItemStack pristine, ItemStack shared) {
            this.pristine = pristine;
            this.shared = shared;
        }
    }

    private final Map<Item, Entry> stacks = new IdentityHashMap<>();
    private long reused;
    private long refreshed;

    private GrindstoneStackReuse() {
        super("grindstone stack reuse", "grindstoneStacks", false);
    }

    @Override
    protected void onBegin() {
        stacks.clear();
        reused = refreshed = 0;
    }

    @Override
    protected void onEnd() {
        stacks.clear();
    }

    /** Returns a stack equal to new ItemStack(item). */
    public ItemStack stackFor(ItemLike item) {
        if (!isActive()) {
            return new ItemStack(item);
        }
        Entry entry = stacks.get(item.asItem());
        if (entry == null) {
            entry = new Entry(new ItemStack(item), new ItemStack(item));
            stacks.put(item.asItem(), entry);
        } else if (ItemStack.matches(entry.shared, entry.pristine)) {
            reused++;
        } else {
            entry.shared = new ItemStack(item);
            refreshed++;
        }
        return entry.shared;
    }

    @Override
    protected String describeStats() {
        return String.format("%d stacks reused, %d replaced after mod code changed them, %d items",
                reused, refreshed, stacks.size());
    }
}
