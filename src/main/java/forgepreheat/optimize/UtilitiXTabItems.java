package forgepreheat.optimize;

import com.mojang.logging.LogUtils;
import forgepreheat.core.FeatureToggle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.IForgeRegistry;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * UtilitiX's BuildCreativeModeTabContentsEvent handler runs once per creative tab and walks all ~43,000 registered items,
 * looking up each one's id to find its own: ~1.5 s of the first creative inventory open. This hands that loop only the
 * items in UtilitiX's namespace, in registry order, so it adds the same items to the same tabs.
 * Rebuilt whenever the registry's size changes; falls back to the full registry if UtilitiX's mod id can't be read.
 */
public final class UtilitiXTabItems {
    public static final UtilitiXTabItems INSTANCE = new UtilitiXTabItems();

    private static final Logger LOGGER = LogUtils.getLogger();

    private final boolean enabled = FeatureToggle.isEnabled("utilitixTabItems", true);
    private String modId;
    private boolean modIdFailed;
    private int registrySize = -1;
    private List<Item> own = List.of();

    private UtilitiXTabItems() {}

    public synchronized Collection<Item> values(IForgeRegistry<Item> registry) {
        Collection<Item> all = registry.getValues();
        if (!enabled || modId() == null) {
            return all;
        }
        if (all.size() != registrySize) {
            List<Item> items = new ArrayList<>();
            for (Item item : all) {
                ResourceLocation key = registry.getKey(item);
                if (key != null && modId.equals(key.getNamespace())) {
                    items.add(item);
                }
            }
            own = items;
            registrySize = all.size();
        }
        return own;
    }

    // The same value UtilitiX compares against (UtilitiX.getInstance().modid), read once.
    private String modId() {
        if (modId == null && !modIdFailed) {
            try {
                Class<?> mod = Class.forName("de.melanx.utilitix.UtilitiX");
                Object instance = mod.getMethod("getInstance").invoke(null);
                modId = (String) instance.getClass().getField("modid").get(instance);
            } catch (ReflectiveOperationException | RuntimeException e) {
                modIdFailed = true;
                LOGGER.warn("[ForgePreheat] Could not read UtilitiX's mod id; its creative tab handler stays as is", e);
            }
        }
        return modId;
    }
}
