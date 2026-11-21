package forgepreheat.optimize;

import forgepreheat.core.PhaseFeature;

/**
 * Registrate (Create, GTCEu and others) unregisters every one-time event listener at load complete. Forge's
 * EventBus.unregister doesn't know which event a listener was for, so it scans every event type's listener list for
 * each listener: ~4 s on the main thread in this pack (JFR). The listeners are inert once fired (Registrate's
 * OneTimeEventReceiver guards them with a consumed flag), so leaving them registered changes no behavior; it only
 * keeps them in memory. Off: -Dforgepreheat.registrateCleanupSkip=false, which times the original cleanup instead.
 */
public final class RegistrateCleanupSkip extends PhaseFeature {
    public static final RegistrateCleanupSkip INSTANCE = new RegistrateCleanupSkip();

    private int left;

    private RegistrateCleanupSkip() {
        super("Registrate cleanup skip", "registrateCleanupSkip", true);
    }

    @Override
    protected void onBegin() {
        left = 0;
    }

    /** Returns true when the unregistering of these listeners must be skipped. */
    public boolean skip(int pendingUnregisters) {
        if (!isActive()) {
            return false;
        }
        left += pendingUnregisters;
        return true;
    }

    @Override
    protected String describeStats() {
        return left + " consumed one-time listeners left registered instead of unregistered";
    }
}
