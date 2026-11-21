package forgepreheat;

import com.mojang.logging.LogUtils;
import forgepreheat.core.Feature;
import forgepreheat.optimize.ObjectHolderIndex;
import forgepreheat.optimize.RegistrateCleanupSkip;
import forgepreheat.profile.StartupTimings;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Composition root for client startup, from this mod's construction to the first title screen: the only class that
 * lists the startup features. Ends on the title screen because that is when the game is usable.
 */
public final class ClientStartSession {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<Feature> FEATURES = List.of(
            StartupTimings.INSTANCE,
            RegistrateCleanupSkip.INSTANCE,
            ObjectHolderIndex.INSTANCE);

    private static boolean ended;

    private ClientStartSession() {}

    public static void begin() {
        FEATURES.forEach(Feature::begin);
        MinecraftForge.EVENT_BUS.addListener(ClientStartSession::onScreenInit);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (ended || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        ended = true;
        List<String> summaries = FEATURES.stream().map(Feature::summary).toList();
        LOGGER.info("[ForgePreheat] Client start reached the title screen {} s after launch; {}",
                String.format("%.2f", ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0),
                String.join("; ", summaries));
        FEATURES.forEach(Feature::end);
    }
}
