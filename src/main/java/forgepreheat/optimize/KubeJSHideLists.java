package forgepreheat.optimize;

import forgepreheat.core.ScopedFeature;

import java.util.ArrayList;
import java.util.Collection;

/**
 * JEI's getAllIngredients is a view that builds a fresh stack per item on every pass, and each hide() call in a
 * KubeJS script makes a pass. Copying the view once per hide event lets every hide() call reuse the same stacks.
 */
public final class KubeJSHideLists extends ScopedFeature {
    public static final KubeJSHideLists INSTANCE = new KubeJSHideLists();

    private long eventsPrepared;

    private KubeJSHideLists() {
        super("KubeJS hide-event item list reuse", "kubejsHide", true);
    }

    @Override
    protected void onBegin() {
        eventsPrepared = 0;
    }

    public <T> Collection<T> prepare(Collection<T> allIngredients) {
        if (!isActive()) {
            return allIngredients;
        }
        eventsPrepared++;
        return new ArrayList<>(allIngredients);
    }

    @Override
    protected String describeStats() {
        return eventsPrepared + " hide events used a shared item list";
    }
}
