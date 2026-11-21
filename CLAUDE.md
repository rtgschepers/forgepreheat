# ForgePreheat

Client-only Forge 1.20.1 mod (formerly JEI Join Profiler, renamed in 0.4.2) that measures and speeds up JEI's startup when joining a world in the
**All the Mods 9 Expert** pack (CurseForge instance). Joining the server used to sit on "Loading terrain"
for ~140 s; the cause is JEI rebuilding its recipe index on the render thread, not the network or server.

## Direction (decided 2026-11-21)

The mod outgrows its name: the goal is **faster loading for any Forge 1.20.1 pack**, covering both joining a world
(JEI start) and, later, client startup to the main menu. ATM9 Expert stays the test pack, not the target.

- **Name: ForgePreheat** (mod id and package `forgepreheat`), chosen by the user: you preheat a forge, and the mod
  targets Forge mods. Renamed from JEI Join Profiler in 0.4.2, before `git init`. The Gradle project name is pinned in
  `settings.gradle`, so the folder name doesn't leak into the manifest. Still open: check CurseForge/Modrinth for a
  clash before publishing. The project folder was renamed to `forgepreheat` on 2026-11-21.
- The installed 0.3.1 jar keeps the old id `jeijoinprofiler`; remove it when installing a `forgepreheat` jar, and use
  `-Djeijoinprofiler.*` toggles only with that jar.
- **Package layout for the wider scope**: `core` (scope, toggle, snapshot, `Feature`), one session per loading phase
  (`JeiStartSession` now, a client startup session later), features grouped by the mod they target
  (e.g. `feature.jei`, `feature.create`, `feature.kubejs`, later `feature.gtceu`), profiling and reporting separate.
- **How generic each part is**: the profiler/report and `IngredientListCache` help any pack with JEI;
  `CreateExclusionIndex` any pack with Create; `KubeJSHideLists` any pack hiding items through KubeJS; deferred JEI
  start any pack with JEI (riskiest); GregTech previews only packs with GTCEu. Nothing checks for ATM9.
- **Rules that keep it safe in unknown packs**: every feature keeps its toggle; mods other than JEI are reached only
  through `@Pseudo` compat mixins that disable themselves when the mod is missing or changed; the summary line lists
  which features ran. Consider a soft-fail mode for the required JEI mixins so a major JEI update disables the mod
  instead of crashing. Other mods may mixin into the same JEI methods; watch for conflicts.
- **Versions**: stay on Forge 1.20.1 (NeoForge 1.20.1 is close). Multi-version builds only once people outside the
  pack use the mod.
- **Client startup (later)**: the pack already ships ModernFix, FerriteCore and Fastload, and ModernFix covers much of
  the usual ground (dynamic resources, model loading). Start with a JFR capture of the 93 s to the main menu and only
  target what those mods leave, so we neither duplicate nor fight them.

## Environment

| What | Where / value |
|---|---|
| Project | `D:\JavaProjects\forgepreheat` (not a git repo yet) |
| Pack instance | `C:\Users\Rick\curseforge\minecraft\Instances\All the Mods 9 Expert` |
| JDK for builds | `C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot` (Temurin 17) |
| `jfr` tool | `C:\Users\Rick\curseforge\minecraft\Install\java\Jre_21\bin\jfr.exe` |
| Versions compiled against | Forge 47.4.10, JEI 15.59.0.210 (local jar), Create 6.0.8, KubeJS 2001.6.5-build.26 |
| Server | modded, ~15 ms ping; network and MTU were checked and are fine |

## Build, install, verify

```bash
cd /d/JavaProjects/forgepreheat
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.21.10002-hotspot"
./gradlew build --console=plain > build-log.txt 2>&1   # jar lands in build/libs/
```

- Install: copy the jar into the instance's `mods` folder and remove the previous `forgepreheat-*.jar` (or the old `jeijoinprofiler-*.jar`)
  (two jars with the same mod id stop Forge). Only do this while `javaw.exe` is not running.
- JEI is compiled against `pack_mods_dir` in `gradle.properties` via `flatDir` + `fg.deobf`. If the pack updates JEI,
  update `jei_version` and re-check every mixin target signature.
- After every build, verify before installing:
  - `forgepreheat.refmap.json` maps each Minecraft-class mixin target to its SRG name (e.g. `broadcastChanges` -> `m_38946_`).
  - Manifest `MixinConfigs` lists both `forgepreheat.mixins.json` and `forgepreheat.compat.mixins.json`.
  - `javap -c` on the classes shows Minecraft calls as `m_*` names (reobf worked), including method references in the bootstrap table (`javap -v`).
- Each run writes `logs/forgepreheat-jei-<timestamp>.txt` in the instance (before 0.4.2: `jei-join-profile-*.txt`), plus a `[ForgePreheat]` summary line in `latest.log`.
  Also check `latest.log` for `InvalidInjectionException` and for `Recipe crashed during Recipe Layout creation` (JEI's caught display errors).

## Architecture

```
forgepreheat/
  ForgePreheat             @Mod entry; does nothing else
  JeiStartSession          composition root: the only class that lists all features; begin/end per JEI start
  core/
    Feature                interface: begin(), end(), summary()
    ScopedFeature          base class: toggle + startup-thread scope + summary format, written once
    StartupScope           active only on JEI's start thread between begin and end
    FeatureToggle          -Dforgepreheat.<key>=true|false
    ListSnapshot           element-identity snapshot guarding identity-keyed caches
  optimize/                one class per speedup, each a ScopedFeature singleton (INSTANCE)
  profile/                 RecipeTimings (collect), RecipeInspector (names, NBT details), ProfileReport (write), RecipeStat
  mixin/                   required mixins (JEI + Minecraft targets)
  mixin/compat/            optional mixins for other mods (Create, KubeJS), @Pseudo, in the non-required config
tools/jfr/                 Python scripts for analysing JFR samples (see Profiling)
```

Flow: `JeiStarterMixin` (HEAD/RETURN of `JeiStarter.start`) calls `JeiStartSession.begin/end`. `end()` collects every
feature's `summary()` first, writes the report, then calls `end()` on each feature (which clears caches).

## Code style and conventions

Follow these for every change.

- **No emoji** anywhere (code, comments, docs, commit messages).
- **Comments**: one sentence, two at most. Explain why, not what. No comment when the code already says it.
  Class Javadoc states the problem the class solves, with the measured numbers when it is an optimization.
- **DRY**: shared mechanics live in `core` (scope, toggle, snapshot, summary format). Never copy the owner-thread or
  identity-check logic into a feature; extend `ScopedFeature` / use `ListSnapshot`.
- **Separation of concerns**: mixins are glue only. They delegate to one method on a feature and contain no logic
  beyond argument plumbing and cancel/return handling. Measuring, inspecting and reporting are separate classes.
- **Open/closed**: adding a feature means a new `ScopedFeature` subclass, a mixin, and one line in
  `JeiStartSession.FEATURES`. Nothing else should need to change.
- **Dependency inversion**: the session and report depend on `Feature` / summary strings, not on concrete optimizations.
- **Features**: singleton `INSTANCE`, private constructor, `super(displayName, toggleKey, enabledByDefault)`.
  `onBegin()` resets stats and state, `onEnd()` drops anything that must not outlive the JEI start, `describeStats()` is one line.
  Every public entry point starts with `if (!isActive()) return <original behavior>;`.
- **Identical results**: an optimization must produce exactly what the original code produces. Prefer sharing or
  indexing work over changing it; when an index can't rule a case out, fall back to the original check.
  Anything that changes behavior must be opt-in (`enabledByDefault = false`) and say why in its Javadoc.
- **Mixins**: injected members are prefixed `forgepreheat$`. Targets in other mods use `remap = false`. Minecraft
  targets are remapped through the refmap (MixinGradle). Pin exact target descriptors for overloaded methods.
  Mixins into mods other than JEI go in `mixin/compat` with `@Pseudo`, so a mod update disables them instead of crashing.
- **Versioning**: bump `mod_version` in `gradle.properties` for every jar that gets installed, so logs and reports
  can be tied to a version.
- Measure before and after every optimization with the report (and JFR when the cause is unclear). Record the numbers below.

## Findings so far (2026-11-20/21)

Baseline join: ~140 s from menu to in-game, of which JEI start ~113 s, recipe indexing (`addRecipe`) 72 s.

| Cause | Cost | Status |
|---|---|---|
| JEI puts the full furnace fuel list (~1,050 stacks) on every cooking recipe; ~7,300 GregTech smelting/blasting recipes each convert it (NBT copy, registry check) | 47 s | fixed in 0.2.0 (`IngredientListCache`) |
| JEI vanilla anvil/grindstone generation drives real menus; most time in `broadcastChanges` | ~10 s | 0.3.0 skip broke grindstone display; now opt-in, needs another approach |
| KubeJS `jei_hide.js` makes 18 `hide()` passes, each rebuilding every JEI item stack | 5.2 s | fix in 0.4.x (`KubeJSHideLists`), not yet measured |
| Create `addTypedRecipesExcluding` compares ~4,000 x 3,700 recipe pairs (`doInputsMatch`) | 5.1 s | fix in 0.4.x (`CreateExclusionIndex`), not yet measured |
| JEI ingredient filter / search index build (tooltips 37%, substring index 32%) | 8.7 s | untouched; candidate for deferral |
| GregTech JEI plugin, mostly building 158 multiblock pattern previews up front | 8.9 s | untouched; candidate for lazy construction |
| Just Enough Resources category registration (first creative-tab build) | 4.6 s | leave: the work happens once anyway |
| ElementalCraft pure-ore recipe scan after JEI (dpanvil `TagsUpdated`) | ~10 s | untouched, outside JEI start |
| Iris shader pipeline compile on spawn | ~4 s | user setting (shaders) |

## Version history

| Version | Change | Measured |
|---|---|---|
| 0.1.0 | Profiler only | join 138.9 s, JEI start 113.4 s |
| 0.2.0 | Shared converted ingredient list (fuel list) | join 92.6 s, JEI start 68.9 s, `jei:minecraft` 68 s -> 18.5 s, furnace 28.1 -> 2.6 s, blasting 22.9 -> 1.9 s |
| 0.3.0 | Skip `broadcastChanges` on JEI's fake anvil/grindstone menus | JEI start 65.7 s, but grindstone recipes show `All slots must have the same number of ingredients` (empty output) |
| 0.3.1 | Menu skip made opt-in (`-Djeijoinprofiler.menuSkip=true`) | **installed in the instance; grindstone test still pending** |
| 0.4.0 | Create exclusion index, KubeJS hide-event list reuse | built, never installed |
| 0.4.1 | Refactor to the architecture above (no behavior change vs 0.4.0) | built, not installed |
| 0.4.2 | Rename to ForgePreheat (mod id, package, toggles, report name); no behavior change vs 0.4.1 | built and verified, not installed |

## Where to pick up

0. **Git**: the rename is done (0.4.2, verified). The **user runs `git init` and the first commit themselves**;
   don't do it for them.
   `.gitignore` already excludes `build`, `.gradle`, `run`, `build-log.txt` and `__pycache__/` (checked 2026-11-21).
   Per the user's global rules, commit messages get no Claude attribution (no `Co-Authored-By: Claude` trailer, no
   "Generated with Claude Code" line). After that, commit each installed version separately so changes can be reviewed and reverted.
1. **Run the 0.3.1 test**: join, open JEI's grindstone, anvil and furnace tabs, quit. No `Recipe crashed` lines means the
   menu skip caused the 0.3.0 grindstone error. If errors remain, the error predates our changes (the old logs never
   show the grindstone tab being opened, so there is no baseline).
2. **Install 0.4.2** (remove `jeijoinprofiler-0.3.1.jar`) and measure: expect Create ~5.1 -> ~1 s and KubeJS hide ~5.2 -> ~1.5 s. Check that Create's
   automatic shaped/shapeless/packing and fan categories look the same, and that hidden items are still hidden.
3. **Cache tidy-up**: `IngredientListCache` stored ~43,000 large lists that were each seen once. Store a list only on its
   second sighting (keep a cheap seen-once marker first).
4. **Defer JEI start** until the player is in the world, so the remaining ~60 s happen while playing. Needs testing
   with mods that expect JEI's runtime to exist at join.
5. **Anvil/grindstone, take two**: understand why skipping `broadcastChanges` emptied grindstone outputs before trying
   again (read `GrindstoneMenu.createResult` and JEI's `GrindstoneRecipeMaker`), or cache the generated recipes, which
   don't depend on the server's recipes.
6. Optional: lazy GregTech multiblock previews (~4 s).
7. After joining is fixed: **client startup** (93 s to the main menu) is the next topic the user wants to look at.

## Profiling workflow

1. Add `-XX:StartFlightRecording=filename=jei-join.jfr,settings=profile` to the instance's JVM arguments in CurseForge.
2. Join, wait until in-world, quit the game completely (the file is only written on JVM exit). Copy `jei-join.jfr` and
   `latest.log` away before the next launch overwrites them.
3. Export samples: `jfr.exe print --events jdk.ExecutionSample --stack-depth 200 jei-join.jfr > samples.txt`.
4. Analyse with `tools/jfr/*.py` (run with `python -I`). The render thread appears as `"main"` in JFR. JFR timestamps can
   be a few seconds off from the game log, and sample counts are not proportional to time: take durations from the
   log's JEI timings, causes from the samples.
5. Remove the JFR argument afterwards; it writes ~45 MB per launch.

Inspecting other mods' code: extract the jar into its own temp folder and use `javap -c -l -p -cp <dir> <class>`
(line numbers map JFR frames to bytecode).
