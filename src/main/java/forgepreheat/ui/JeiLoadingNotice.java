package forgepreheat.ui;

import forgepreheat.core.FeatureToggle;
import forgepreheat.optimize.AsyncJeiStart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

/**
 * While JEI loads in the background, inventory screens show nothing where JEI's item list will appear, so players think
 * JEI is broken. This writes the current phase there and, after one full start, the time left, centered vertically
 * and at three quarters of that strip.
 * -Dforgepreheat.loadingNotice=false turns it off.
 */
public final class JeiLoadingNotice {
    private static final int MARGIN = 4;
    private static final int MIN_WIDTH = 70;
    private static final int TEXT = 0xFFFFFFFF;

    private JeiLoadingNotice() {}

    public static void install() {
        if (FeatureToggle.isEnabled("loadingNotice", true)) {
            MinecraftForge.EVENT_BUS.addListener(JeiLoadingNotice::onRender);
        }
    }

    private static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || !AsyncJeiStart.INSTANCE.isLoading()
                || AsyncJeiStart.INSTANCE.isCancelled()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        // The drawer is the strip right of the container where JEI's item list will appear.
        int left = screen.getGuiLeft() + screen.getXSize() + MARGIN;
        int width = screen.width - MARGIN - left;
        if (width < MIN_WIDTH) {
            return;
        }
        List<String> lines = lines();
        int lineHeight = font.lineHeight + 2;
        int y = (screen.height - lines.size() * lineHeight + 2) / 2;
        // Centered at three quarters of the strip, kept inside it for long lines.
        int center = left + width * 3 / 4;
        for (String text : lines) {
            String line = font.plainSubstrByWidth(text, width);
            int x = Math.max(left, Math.min(center - font.width(line) / 2, left + width - font.width(line)));
            event.getGuiGraphics().drawString(font, line, x, y, TEXT, true);
            y += lineHeight;
        }
    }

    private static List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("JEI is loading");
        if (AsyncJeiStart.INSTANCE.isWaitingForTags() || !JeiStartProgress.INSTANCE.isRunning()) {
            lines.add("Waiting for server data");
            return lines;
        }
        lines.add(JeiStartProgress.INSTANCE.phase());
        long left = JeiStartProgress.INSTANCE.secondsLeft();
        if (left > 0) {
            lines.add("about " + left + " s left");
        } else if (left == 0) {
            lines.add("almost done");
        }
        return lines;
    }
}
