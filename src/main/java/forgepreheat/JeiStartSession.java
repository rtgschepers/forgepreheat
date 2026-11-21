package forgepreheat;

import forgepreheat.core.Feature;
import forgepreheat.core.StartupScope;
import forgepreheat.optimize.AsyncJeiStart;
import forgepreheat.optimize.BackgroundSearchIndex;
import forgepreheat.optimize.CreateExclusionIndex;
import forgepreheat.optimize.FilterUpdateBatching;
import forgepreheat.optimize.IngredientListCache;
import forgepreheat.optimize.KubeJSHideLists;
import forgepreheat.optimize.LazyRenderOnlyIndex;
import forgepreheat.optimize.ParallelVanillaMakers;
import forgepreheat.profile.ProfileReport;
import forgepreheat.profile.RecipeIdDump;
import forgepreheat.profile.RecipeTimings;
import forgepreheat.profile.StepTimings;

import java.util.List;

/** Composition root: the one place that knows every feature. New features are registered here and nowhere else. */
public final class JeiStartSession {
    // Order is only the order of lines in the report header.
    private static final List<Feature> FEATURES = List.of(
            RecipeTimings.INSTANCE,
            RecipeIdDump.INSTANCE,
            StepTimings.INSTANCE,
            IngredientListCache.INSTANCE,
            CreateExclusionIndex.INSTANCE,
            KubeJSHideLists.INSTANCE,
            ParallelVanillaMakers.INSTANCE,
            LazyRenderOnlyIndex.INSTANCE,
            BackgroundSearchIndex.INSTANCE,
            FilterUpdateBatching.INSTANCE,
            AsyncJeiStart.INSTANCE);

    private JeiStartSession() {}

    public static void begin() {
        StartupScope.setOwner(Thread.currentThread());
        FEATURES.forEach(Feature::begin);
    }

    public static void end() {
        // Summaries first: ending a feature clears the statistics they report.
        List<String> summaries = FEATURES.stream().map(Feature::summary).toList();
        if (RecipeTimings.INSTANCE.isEnabled()) {
            ProfileReport.write(RecipeTimings.INSTANCE, summaries);
        }
        FEATURES.forEach(Feature::end);
        StartupScope.setOwner(null);
    }
}
