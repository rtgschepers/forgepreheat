package forgepreheat.optimize;

import com.mojang.logging.LogUtils;
import forgepreheat.core.FeatureToggle;
import forgepreheat.core.PhaseFeature;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ObjectHolderRegistry;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * After each registry's RegisterEvent, Forge calls ObjectHolderRegistry.applyObjectHolders(registry::equals), which runs
 * every object holder (one per RegistryObject of every mod) although only that registry's holders do anything: the
 * registry-events phase takes ~10 s in this pack. This keeps the holders grouped by registry and, for a one-registry
 * pass, runs only that registry's group plus every holder that could still do something else (non-RegistryObject
 * holders, and RegistryObject holders whose one-time registry check hasn't run yet). Those run in Forge's own
 * iteration order through Forge's own per-holder action, so errors are collected exactly as before.
 * Passes matching more than one registry go to Forge's loop. -Dforgepreheat.objectHolderVerify=true checks every skip.
 */
public final class ObjectHolderIndex extends PhaseFeature {
    public static final ObjectHolderIndex INSTANCE = new ObjectHolderIndex();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_LOGGED_MISMATCHES = 10;

    private record Entry(int rank, Consumer<Predicate<ResourceLocation>> holder) {}

    private final boolean verify = FeatureToggle.isEnabled("objectHolderVerify", false);
    private volatile boolean dirty = true;
    private int indexedSize = -1;
    private List<Entry> alwaysRun = new ArrayList<>();
    private Map<ResourceLocation, List<Entry>> byRegistry = new HashMap<>();
    private long passes, indexedPasses, forgePasses, rebuilds, holderCalls, holdersSkipped, mismatches;

    private ObjectHolderIndex() {
        super("object holder index", "objectHolderIndex", true);
    }

    @Override
    protected void onBegin() {
        passes = indexedPasses = forgePasses = rebuilds = holderCalls = holdersSkipped = mismatches = 0;
        dirty = true;
    }

    @Override
    protected void onEnd() {
        alwaysRun = new ArrayList<>();
        byRegistry = new HashMap<>();
        dirty = true;
    }

    /** Called when Forge adds or removes a holder, from any thread. */
    public void invalidate() {
        dirty = true;
    }

    /**
     * Runs one applyObjectHolders pass by handing the holders that need it to Forge's per-holder action, in Forge's
     * iteration order. Falls back to iterating every holder when inactive or when the filter matches several registries.
     */
    public void forEach(Set<Consumer<Predicate<ResourceLocation>>> holders, Predicate<ResourceLocation> filter,
                        Consumer<Consumer<Predicate<ResourceLocation>>> perHolder) {
        if (!isActive() || !RegistryObjectHolders.available()) {
            holders.forEach(perHolder);
            return;
        }
        passes++;
        // Same monitor as ObjectHolderRegistry.addHandler/removeHandler.
        synchronized (ObjectHolderRegistry.class) {
            if (dirty || holders.size() != indexedSize) {
                rebuild(holders);
            }
        }
        List<Entry> group = Collections.emptyList();
        int matches = 0;
        for (Map.Entry<ResourceLocation, List<Entry>> e : byRegistry.entrySet()) {
            if (filter.test(e.getKey())) {
                group = e.getValue();
                matches++;
            }
        }
        if (matches > 1) {
            forgePasses++;
            holders.forEach(perHolder);
            return;
        }
        List<Entry> run = merge(alwaysRun, group);
        if (verify) {
            check(holders, filter, run);
        }
        for (Entry e : run) {
            perHolder.accept(e.holder());
        }
        indexedPasses++;
        holderCalls += run.size();
        holdersSkipped += indexedSize - run.size();
        promote();
    }

    private void rebuild(Set<Consumer<Predicate<ResourceLocation>>> holders) {
        List<Entry> always = new ArrayList<>();
        Map<ResourceLocation, List<Entry>> groups = new HashMap<>();
        int rank = 0;
        for (Consumer<Predicate<ResourceLocation>> holder : holders) {
            Entry entry = new Entry(rank++, holder);
            ResourceLocation registry = skippableRegistry(holder);
            if (registry == null) {
                always.add(entry);
            } else {
                groups.computeIfAbsent(registry, k -> new ArrayList<>()).add(entry);
            }
        }
        alwaysRun = always;
        byRegistry = groups;
        indexedSize = holders.size();
        dirty = false;
        rebuilds++;
    }

    private static ResourceLocation skippableRegistry(Consumer<Predicate<ResourceLocation>> holder) {
        return RegistryObjectHolders.settledRegistry(holder);
    }

    // Holders only ever become skippable (their flags go from false to true), so moving them once is enough.
    private void promote() {
        List<Entry> still = new ArrayList<>();
        for (Entry e : alwaysRun) {
            ResourceLocation registry = skippableRegistry(e.holder());
            if (registry == null) {
                still.add(e);
                continue;
            }
            List<Entry> group = byRegistry.computeIfAbsent(registry, k -> new ArrayList<>());
            int at = Collections.binarySearch(group, e, (a, b) -> Integer.compare(a.rank(), b.rank()));
            group.add(at < 0 ? -at - 1 : at, e);
        }
        alwaysRun = still;
    }

    private static List<Entry> merge(List<Entry> a, List<Entry> b) {
        List<Entry> out = new ArrayList<>(a.size() + b.size());
        int i = 0;
        int j = 0;
        while (i < a.size() && j < b.size()) {
            out.add(a.get(i).rank() < b.get(j).rank() ? a.get(i++) : b.get(j++));
        }
        while (i < a.size()) {
            out.add(a.get(i++));
        }
        while (j < b.size()) {
            out.add(b.get(j++));
        }
        return out;
    }

    // Every holder this pass skips must be one Forge's loop would have run as a no-op.
    private void check(Set<Consumer<Predicate<ResourceLocation>>> holders, Predicate<ResourceLocation> filter,
                       List<Entry> run) {
        Map<Object, Boolean> runSet = new IdentityHashMap<>();
        run.forEach(e -> runSet.put(e.holder(), Boolean.TRUE));
        for (Consumer<Predicate<ResourceLocation>> holder : holders) {
            if (runSet.containsKey(holder)) {
                continue;
            }
            ResourceLocation registry = skippableRegistry(holder);
            if (registry == null || filter.test(registry)) {
                if (mismatches++ < MAX_LOGGED_MISMATCHES) {
                    LOGGER.warn("[ForgePreheat] Object holder index would skip a holder Forge runs: {} (registry {})",
                            holder, registry);
                }
            }
        }
    }

    @Override
    protected String describeStats() {
        String base = String.format("%d passes (%d indexed, %d via Forge's loop), %d index rebuilds, %d holder calls, "
                + "%d skipped, %d holders always run at the end", passes, indexedPasses, forgePasses, rebuilds,
                holderCalls, holdersSkipped, alwaysRun.size());
        return verify ? base + ", verify: " + mismatches + " mismatches" : base;
    }
}
