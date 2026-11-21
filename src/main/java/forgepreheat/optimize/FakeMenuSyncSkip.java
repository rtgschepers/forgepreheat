package forgepreheat.optimize;

import forgepreheat.core.ScopedFeature;

/**
 * Skips slot syncing on the in-memory anvil and grindstone menus JEI drives to generate recipes.
 * Off by default: in 0.3.0 grindstone recipes showed empty outputs with it on, and it only saved about 3 s.
 */
public final class FakeMenuSyncSkip extends ScopedFeature {
    public static final FakeMenuSyncSkip INSTANCE = new FakeMenuSyncSkip();

    private boolean inVanillaRecipes;
    private long skipped;

    private FakeMenuSyncSkip() {
        super("fake anvil/grindstone menu sync skip", "menuSkip", false);
    }

    @Override
    protected void onBegin() {
        inVanillaRecipes = false;
        skipped = 0;
    }

    @Override
    protected void onEnd() {
        inVanillaRecipes = false;
    }

    /** Brackets JEI's vanilla plugin recipe registration, the only time JEI drives these fake menus. */
    public void enterVanillaRecipes() {
        inVanillaRecipes = true;
    }

    public void exitVanillaRecipes() {
        inVanillaRecipes = false;
    }

    public boolean shouldSkip() {
        if (!inVanillaRecipes || !isActive()) {
            return false;
        }
        skipped++;
        return true;
    }

    @Override
    protected String describeStats() {
        return skipped + " syncs skipped";
    }
}
