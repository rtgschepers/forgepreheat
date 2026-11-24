package forgepreheat.profile;

import com.mojang.logging.LogUtils;
import forgepreheat.JeiStartSession;
import forgepreheat.core.FeatureToggle;
import forgepreheat.optimize.AsyncJeiStart;
import forgepreheat.optimize.LazyRenderOnlyIndex;
import forgepreheat.ui.JeiStartProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

/**
 * Memory and timing runs used to need someone joining and leaving by hand. -Dforgepreheat.joinLoop=&lt;server address&gt;
 * joins that server joinLoopRuns times (default 3), logs out once JEI is fully loaded, and quits the game; with
 * joinLoopCancelLast (default true) the last join logs out mid-start instead. Off unless the address is set.
 */
public final class JoinLoop {
    private static final Logger LOGGER = LogUtils.getLogger();
    // The waits leave room for the heap watcher, which snapshots 15 s into the title screen and 30 s after a logout.
    private static final long TITLE_WAIT = seconds(45);
    private static final long MENU_WAIT = seconds(60);
    private static final long LOADED_WAIT = seconds(30);
    private static final long CANCEL_AFTER = seconds(15);
    private static final long RUN_LIMIT = seconds(300);

    private enum Step { TITLE, JOINING, IN_WORLD, MENU, DONE }

    private final String address;
    private final int runs;
    private final boolean cancelLast;
    private Step step = Step.TITLE;
    private int run;
    private long waitStart;
    private long joinStart;
    private long startSeenAt;
    private long loadedAt;
    private int endedStartsAtJoin;

    private JoinLoop(String address, int runs, boolean cancelLast) {
        this.address = address;
        this.runs = runs;
        this.cancelLast = cancelLast;
    }

    public static void install() {
        String address = System.getProperty(FeatureToggle.property("joinLoop"));
        if (address == null || address.isBlank()) {
            return;
        }
        int runs = Math.max(1, Integer.getInteger(FeatureToggle.property("joinLoopRuns"), 3));
        JoinLoop loop = new JoinLoop(address.trim(), runs, FeatureToggle.isEnabled("joinLoopCancelLast", true));
        MinecraftForge.EVENT_BUS.addListener(loop::onClientTick);
        LOGGER.info("[ForgePreheat] Join loop: {} joins of {}{}, then quit", runs, loop.address,
                loop.cancelLast ? ", the last one logging out mid-start" : "");
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || step == Step.DONE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        switch (step) {
            case TITLE -> {
                if (!(mc.screen instanceof TitleScreen) || mc.level != null) {
                    return;
                }
                if (waitStart == 0) {
                    waitStart = now;
                } else if (now - waitStart >= TITLE_WAIT) {
                    join(mc, now);
                }
            }
            case MENU -> {
                if (now - waitStart >= MENU_WAIT) {
                    if (run >= runs) {
                        quit(mc);
                    } else {
                        join(mc, now);
                    }
                }
            }
            case JOINING, IN_WORLD -> inGame(mc, now);
            default -> { }
        }
    }

    private void inGame(Minecraft mc, long now) {
        if (mc.screen instanceof DisconnectedScreen) {
            abort("run " + run + " was disconnected; the loop stops here");
            return;
        }
        if (step == Step.JOINING) {
            if (mc.level != null && mc.player != null) {
                step = Step.IN_WORLD;
                LOGGER.info("[ForgePreheat] Join loop: run {} in the world after {} s", run, secondsSince(joinStart, now));
            } else if (now - joinStart >= RUN_LIMIT) {
                abort("run " + run + " did not reach the world in " + secondsSince(joinStart, now) + " s");
            }
            return;
        }
        boolean ended = JeiStartSession.endedStarts() > endedStartsAtJoin;
        if (cancelLast && run == runs) {
            if (ended) {
                logOut(mc, now, "JEI finished before it could be interrupted");
            } else if (JeiStartProgress.INSTANCE.isRunning()) {
                if (startSeenAt == 0) {
                    startSeenAt = now;
                } else if (now - startSeenAt >= CANCEL_AFTER) {
                    logOut(mc, now, "mid-start, " + secondsSince(startSeenAt, now) + " s into JEI's start");
                }
            }
        } else if (loadedAt == 0) {
            if (ended && !AsyncJeiStart.INSTANCE.isLoading()
                    && !LazyRenderOnlyIndex.INSTANCE.isBackgroundBuildPending()) {
                loadedAt = now;
                LOGGER.info("[ForgePreheat] Join loop: run {} has JEI fully loaded {} s after joining", run,
                        secondsSince(joinStart, now));
            }
        } else if (now - loadedAt >= LOADED_WAIT) {
            logOut(mc, now, "JEI fully loaded");
        }
        if (step == Step.IN_WORLD && now - joinStart >= RUN_LIMIT) {
            logOut(mc, now, "time limit of " + secondsSince(joinStart, now) + " s reached; JEI may not have loaded");
        }
    }

    private void join(Minecraft mc, long now) {
        run++;
        joinStart = now;
        startSeenAt = 0;
        loadedAt = 0;
        endedStartsAtJoin = JeiStartSession.endedStarts();
        if (!ServerAddress.isValidAddress(address)) {
            abort("'" + address + "' is not a valid server address");
            return;
        }
        LOGGER.info("[ForgePreheat] Join loop: run {} of {} joins {}", run, runs, address);
        step = Step.JOINING;
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address), savedServer(mc), false);
    }

    // The saved entry carries the player's resource pack choice, so a server pack doesn't stop the run with a prompt.
    private ServerData savedServer(Minecraft mc) {
        ServerList list = new ServerList(mc);
        list.load();
        for (int i = 0; i < list.size(); i++) {
            if (address.equalsIgnoreCase(list.get(i).ip)) {
                return list.get(i);
            }
        }
        return new ServerData(address, address, false);
    }

    // Same calls as the pause screen's Disconnect button for a remote server.
    private void logOut(Minecraft mc, long now, String reason) {
        LOGGER.info("[ForgePreheat] Join loop: run {} logs out ({})", run, reason);
        if (mc.level != null) {
            mc.level.disconnect();
        }
        mc.clearLevel();
        mc.setScreen(new TitleScreen());
        step = Step.MENU;
        waitStart = now;
    }

    private void quit(Minecraft mc) {
        LOGGER.info("[ForgePreheat] Join loop: {} runs done, quitting", runs);
        step = Step.DONE;
        mc.stop();
    }

    private void abort(String reason) {
        LOGGER.warn("[ForgePreheat] Join loop: {}", reason);
        step = Step.DONE;
    }

    private static long seconds(long s) {
        return s * 1_000_000_000L;
    }

    private static String secondsSince(long start, long now) {
        return String.format("%.1f", (now - start) / 1e9);
    }
}
