package forgepreheat.optimize;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Reads the state of Forge's per-RegistryObject object holder (the anonymous Consumer RegistryObject$1 in Forge 47.x).
 * Reflection instead of a mixin: the Mixin annotation processor fails on shadows of this class. If the fields can't be
 * found (another Forge version), {@link #available()} is false and callers treat every holder as unknown.
 */
final class RegistryObjectHolders {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HOLDER_CLASS = "net.minecraftforge.registries.RegistryObject$1";

    private static final Class<?> TYPE;
    private static final VarHandle REGISTRY_NAME;
    private static final VarHandle REGISTRY_EXISTS;
    private static final VarHandle INVALID_REGISTRY;

    static {
        Class<?> type = null;
        VarHandle name = null, exists = null, invalid = null;
        try {
            type = Class.forName(HOLDER_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
            name = lookup.findVarHandle(type, "val$registryName", ResourceLocation.class);
            exists = lookup.findVarHandle(type, "registryExists", boolean.class);
            invalid = lookup.findVarHandle(type, "invalidRegistry", boolean.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[ForgePreheat] Can't read Forge's object holders; object holder index stays off", e);
            type = null;
        }
        TYPE = type;
        REGISTRY_NAME = name;
        REGISTRY_EXISTS = exists;
        INVALID_REGISTRY = invalid;
    }

    private RegistryObjectHolders() {}

    static boolean available() {
        return TYPE != null;
    }

    /**
     * The registry of a holder whose accept() is a no-op unless the filter matches that registry; null when it might do
     * something else (run its one-time registry check, which can throw) or isn't a RegistryObject holder at all.
     * Holders of optional registries never set registryExists, so they get null and keep running, as in Forge.
     */
    static ResourceLocation settledRegistry(Object holder) {
        if (TYPE == null || holder.getClass() != TYPE) {
            return null;
        }
        boolean settled = (boolean) INVALID_REGISTRY.get(holder) || (boolean) REGISTRY_EXISTS.get(holder);
        return settled ? (ResourceLocation) REGISTRY_NAME.get(holder) : null;
    }
}
