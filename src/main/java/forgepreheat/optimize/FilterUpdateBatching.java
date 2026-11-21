package forgepreheat.optimize;

import forgepreheat.core.ScopedFeature;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * During JEI's start, KubeJS hides items in many separate batches, and each batch makes JEI's ingredient filter tell its
 * listeners the list changed. With an inventory open the item list overlay then re-filters all ~70,000 ingredients
 * per batch: a 44.7 s freeze in the "Sending Runtime" step. This holds the notifications back during the start and
 * sends one per filter when it ends, so listeners see the same final list.
 */
public final class FilterUpdateBatching extends ScopedFeature {
    public static final FilterUpdateBatching INSTANCE = new FilterUpdateBatching();

    private final Set<FilterListenerNotifier> pending = Collections.newSetFromMap(new IdentityHashMap<>());
    private long heldBack;

    private FilterUpdateBatching() {
        super("filter update batching", "filterUpdateBatching", true);
    }

    @Override
    protected void onBegin() {
        pending.clear();
        heldBack = 0;
    }

    @Override
    protected void onEnd() {
        if (pending.isEmpty()) {
            return;
        }
        Set<FilterListenerNotifier> filters = Set.copyOf(pending);
        pending.clear();
        Runnable flush = () -> filters.forEach(FilterListenerNotifier::forgepreheat$notifyListeners);
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            flush.run();
        } else {
            mc.execute(flush);
        }
    }

    /** Returns true when the notification was held back for the end of the start. */
    public boolean holdBack(FilterListenerNotifier filter) {
        if (!isActive()) {
            return false;
        }
        pending.add(filter);
        heldBack++;
        return true;
    }

    @Override
    protected String describeStats() {
        return heldBack + " ingredient list change notifications sent as " + pending.size() + " at the end";
    }
}
