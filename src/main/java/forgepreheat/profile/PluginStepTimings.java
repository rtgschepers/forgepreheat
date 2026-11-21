package forgepreheat.profile;

import forgepreheat.core.ScopedFeature;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.RecipeType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * For every plugin's registerRecipes, books the time spent making each recipe list (since the plugin started or its
 * previous addRecipes) and handing it to JEI (the addRecipes call), so a slow plugin shows which recipe type is slow
 * and whether the plugin or JEI is to blame. Also tells the start sampler which plugin is running.
 */
public final class PluginStepTimings extends ScopedFeature {
    public static final PluginStepTimings INSTANCE = new PluginStepTimings();

    private static final int SHOWN = 30;

    private final Map<String, long[]> steps = new HashMap<>();
    private volatile String plugin = "";
    private long lapMark;
    private int depth;
    private int adds;

    private PluginStepTimings() {
        super("plugin step timings", "pluginSteps", true);
    }

    @Override
    protected void onBegin() {
        steps.clear();
        plugin = "";
        depth = 0;
    }

    @Override
    protected void onEnd() {
        steps.clear();
        plugin = "";
    }

    /** The plugin whose call is running, or "" between plugins; read by the sampler thread. */
    public String currentPlugin() {
        return plugin;
    }

    /** Wraps each plugin call of a JEI phase so the plugin is known while it runs. */
    public Consumer<IModPlugin> wrap(Consumer<IModPlugin> call) {
        return p -> {
            String previous = plugin;
            enterPlugin(p.getPluginUid());
            try {
                call.accept(p);
            } finally {
                exitPlugin();
                plugin = previous;
            }
        };
    }

    private void enterPlugin(Object uid) {
        plugin = String.valueOf(uid);
        if (isActive()) {
            lapMark = System.nanoTime();
            depth = 0;
            adds = 0;
        }
    }

    // Only registerRecipes adds recipes, so other phases book nothing here.
    private void exitPlugin() {
        if (isActive() && adds > 0) {
            book("after last add", System.nanoTime() - lapMark);
        }
    }

    public void beforeAdd(RecipeType<?> type) {
        if (isActive() && depth++ == 0) {
            adds++;
            long now = System.nanoTime();
            book("make " + type.getUid(), now - lapMark);
            lapMark = now;
        }
    }

    public void afterAdd(RecipeType<?> type) {
        if (isActive() && --depth == 0) {
            long now = System.nanoTime();
            book("add " + type.getUid(), now - lapMark);
            lapMark = now;
        }
    }

    private void book(String step, long nanos) {
        long[] stat = steps.computeIfAbsent(plugin + " > " + step, k -> new long[2]);
        stat[0] += nanos;
        stat[1]++;
    }

    @Override
    protected String describeStats() {
        return "slowest " + SHOWN + ": " + steps.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(SHOWN)
                .map(e -> String.format("%s %.2f s (%d)", e.getKey(), e.getValue()[0] / 1e9, e.getValue()[1]))
                .collect(Collectors.joining(", "));
    }
}
