# ForgePreheat

Client-only Forge 1.20.1 mod (formerly JEI Join Profiler, renamed in 0.4.2) that measures and speeds up JEI's startup when joining a world in the
**All the Mods 9 Expert** pack (CurseForge instance). Joining the server used to sit on "Loading terrain"
for ~140 s; the cause is JEI rebuilding its recipe index on the render thread, not the network or server.
Since 0.8.0 JEI starts on a worker thread by default: "Loading terrain" takes < 15 s and JEI appears ~1.5 min after
joining, with a ~2 s hitch when its GUI is built on the main thread.

## Direction (decided 2026-11-21)

The mod outgrows its name: the goal is **faster loading for any Forge 1.20.1 pack**, covering both joining a world
(JEI start) and, later, client startup to the main menu. ATM9 Expert stays the test pack, not the target.

**Goal (user, 2026-11-21): get the game ready in a complete state as fast as possible.** "Ready" includes JEI being
fully loaded, not just the player being in the world. Moving work to the background is not the end state: **speeding
up JEI's indexing is a requirement**, to be done after the client-startup work.

- **Name: ForgePreheat** (mod id and package `forgepreheat`), chosen by the user: you preheat a forge, and the mod
  targets Forge mods. Renamed from JEI Join Profiler in 0.4.2, before `git init`. The Gradle project name is pinned in
  `settings.gradle`, so the folder name doesn't leak into the manifest. Still open: check CurseForge/Modrinth for a
  clash before publishing. The project folder was renamed to `forgepreheat` on 2026-11-21.
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
- **Client startup: closed (user, 2026-11-21).** A and B (0.9.0, ~4 s) stay; no further startup work, since what is
  left is per-mod, pre-mod (jar scan) or machine-level (antivirus, Java, launcher). Next: JEI indexing.

## Environment

| What | Where / value |
|---|---|
| Project | `D:\JavaProjects\forgepreheat`, git `github.com:rtgschepers/forgepreheat` branch `main` |
| Pack instance | `C:\Users\Rick\curseforge\minecraft\Instances\All the Mods 9 Expert` |
| JDK for builds | `C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot` (Temurin 17) |
| `jfr` tool | `C:\Users\Rick\curseforge\minecraft\Install\java\Jre_21\bin\jfr.exe` |
| Versions compiled against | Forge 47.4.10, JEI 15.59.0.210 (BlameJared maven), Create 6.0.8, KubeJS 2001.6.5-build.26 |
| Server | modded, ~15 ms ping; network and MTU were checked and are fine |

## Build, install, verify

```bash
cd /d/JavaProjects/forgepreheat
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.21.10002-hotspot"
./gradlew build --console=plain > build-log.txt 2>&1   # jar lands in build/libs/
```

- Install: copy the jar into the instance's `mods` folder and remove the previous `forgepreheat-*.jar`
  (two jars with the same mod id stop Forge). Only do this while `javaw.exe` is not running.
- JEI is compiled against `mezz.jei:jei-1.20.1-forge:<jei_version>` from `maven.blamejared.com` via `fg.deobf` (the
  full jar, since the mixins target JEI internals; 15.59.0.210 there is byte-identical to the pack's jar). If the pack
  updates JEI, update `jei_version` and re-check every mixin target signature.
- After every build, verify before installing:
  - `forgepreheat.refmap.json` maps every Minecraft-class mixin target (or `@At(..., remap = true)` target) to its SRG
    name. Since 0.8.0 no mixin targets Minecraft, so the refmap is empty; check it again when one is added.
  - Manifest `MixinConfigs` lists both `forgepreheat.mixins.json` and `forgepreheat.compat.mixins.json`.
  - `javap -c` on the classes shows Minecraft calls as `m_*` names (reobf worked), including method references in the bootstrap table (`javap -v`).
- Each run writes `logs/forgepreheat-jei-<timestamp>.txt` in the instance (before 0.4.2: `jei-join-profile-*.txt`), plus a `[ForgePreheat]` summary line in `latest.log`.
  Also check `latest.log` for `InvalidInjectionException` and for `Recipe crashed during Recipe Layout creation` (JEI's caught display errors).

## Architecture

```
forgepreheat/
  ForgePreheat             @Mod entry; on the client starts ClientStartSession
  JeiStartSession          composition root for JEI's start: lists those features; begin/end per JEI start
  ClientStartSession       composition root for client startup: begin in the mod constructor, end on the first
                           TitleScreen (logs "[ForgePreheat] Client start reached the title screen N s after launch")
  core/
    Feature                interface: begin(), end(), summary()
    ToggledFeature         shared base: name, -D toggle, summary format
    ScopedFeature          ToggledFeature active only on the thread owning JEI's start (StartupScope)
    PhaseFeature           ToggledFeature active for a whole phase on any thread (client startup)
    StartupScope           global owner thread of JEI's start; moves with MainThreadBridge hops
    FeatureToggle          -Dforgepreheat.<key>=true|false
    ListSnapshot           element-identity snapshot guarding identity-keyed caches
  optimize/                one class per speedup, each a ScopedFeature singleton (INSTANCE)
    IngredientListCache    shared converted fuel list (and other big repeated lists)
    CreateExclusionIndex   Create automatic-recipe exclusion index (compat)
    KubeJSHideLists        one item list per KubeJS hide event (compat)
    ParallelVanillaMakers  JEI's anvil and grindstone recipe makers on their own threads during the vanilla plugin
    FilterUpdateBatching   one ingredient-list change notification at the end of JEI's start instead of one per hide batch
    CreativeTabBuildLock   JEI's worker and the creative screen never build creative tabs at the same time
    ParallelRecipeIndex    INPUT/OUTPUT/CATALYST recipe maps filled by their own threads (PipelinedRecipeIndex), in order
    LazyRenderOnlyIndex    RENDER_ONLY recipe index built after the start (DeferredRecipeIndex replays JEI's calls)
    BackgroundSearchIndex  JEI search index built on a worker (BackgroundElementSearch); cancelled when JEI stops
    AsyncJeiStart          JeiStarter.start on a worker; MainThreadBridge runs GUI/runtime steps and listed plugins
                           on the main thread; waits for the server's tags; parks stops behind the running start
  profile/                 RecipeTimings (collect, with setup/index split), RecipeInspector (names, NBT details),
                           ProfileReport (write), RecipeStat, StepTimings (named steps/laps inside plugins),
                           RecipeIdDump (sorted recipe ids per category, -Dforgepreheat.dumpCategories),
                           StepClock (shared step timer), StartupTimings (client-start steps, thread-safe)
  mixin/                   required mixins (JEI targets only since 0.8.0)
  mixin/compat/            optional mixins for other mods (Create, KubeJS, GTCEu timings), @Pseudo, non-required config
ui/                        JeiLoadingNotice (panel while JEI loads), JeiStartProgress (phase + estimate from last start)
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
- **Mixins**: never put a non-mixin class (interface, helper) under `forgepreheat.mixin`: Mixin refuses to load it and the game crashes at startup (0.9.5). Injected members are prefixed `forgepreheat$`. Targets in other mods use `remap = false`. Minecraft
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
| JEI vanilla anvil/grindstone recipe generation. Measured with `StepTimings` (0.5.2): make grindstone 4.75 s, make anvil 3.62 s, plus adding them 1.23 + 0.74 s. Menu sync (`broadcastChanges`) is only ~0.5 s of it (0.3.x A/B). Grindstone: `GrindstoneRecipeMaker.getDisenchantRecipes` loops every non-curse enchantment x every registry item and does `new ItemStack(item)` for each pair (JFR: ~45% of its samples in the `ItemStack` constructor, capability events and constructor mixins), then `isEnchantable`/`canEnchant`; only enchantable pairs get `copy()` + grindstone menu. Anvil: iterates ingredient stacks (no new stack per pair), cost spread over `canEnchant` and the `AnvilMenu` result per recipe | ~10.3 s | the cost is running each recipe through the real grindstone/anvil menu (see 0.6.1). Next candidate: run `AnvilRecipeMaker.getAnvilRecipes` and `GrindstoneRecipeMaker.getGrindstoneRecipes` on worker threads from `VanillaPlugin.registerRecipes` HEAD and join where JEI calls them (~7 s of main-thread work before the anvil call), opt-in |
| KubeJS `jei_hide.js` makes 18 `hide()` passes, each rebuilding every JEI item stack | 5.2 s | fixed in 0.4.x (`KubeJSHideLists`); together with Create: JEI start -6.9 s in 0.4.2 |
| Create `addTypedRecipesExcluding` compares ~4,000 x 3,700 recipe pairs (`doInputsMatch`) | 5.1 s | fixed in 0.4.x (`CreateExclusionIndex`); measured together with KubeJS, see above. The report does not time either phase on its own |
| JEI ingredient filter / search index build (tooltips 37%, substring index 32%) | 7.5 s ("Building ingredient filter" in the log), ~7 s of it `ElementSearch` | 0.5.0 builds `ElementSearch` on a worker thread (`BackgroundSearchIndex`, opt-in `-Dforgepreheat.asyncSearch=true`): filter build 7.46 s -> 0.38 s on the render thread; worker takes 8.4 s |
| GregTech JEI plugin `registerRecipes` 8.03 s, measured with `StepTimings` (0.5.1, 2026-11-21): multiblock 1.31 s (158 preview widgets 1.20 s); machine recipes 6.62 s for 67,004 `GTRecipeWidget`s: slots 3.89 s, of which `createUITemplate` 3.23 s (one per recipe; `createDefault()` rebuilds the type's default layout, or deserializes the type's custom UI NBT via `Resources.fromNBT` + `deserializeNBT`, every time), text 0.50 s, buttons 0.04 s, representative recipes 0.02 s, ~2.2 s elsewhere in widget/wrapper construction | 8.0 s | lazy widgets ruled out (JEI's `setRecipe` reads each widget's slots at start). Sharing the per-type template would need LDLib widget/texture cloning semantics: at most ~1.5-2 s, risky. Deprioritized |
| Just Enough Resources category registration (first creative-tab build) | 4.6 s | leave: the work happens once anyway |
| ElementalCraft pure-ore recipe scan after JEI (dpanvil `TagsUpdated`) | ~10 s | untouched, outside JEI start |
| Iris shader pipeline compile on spawn | ~4 s | user setting (shaders) |

## Async JEI start (feasibility checked 2026-11-21; prototype 0.7.0; on by default since 0.8.0)

Trigger: `StartEventObserver` (JEI forge) calls `JeiStarter.start` synchronously on the main thread once it has seen
`LoggingIn` + `RecipesUpdatedEvent`; `restart()` on a new recipe sync or resource reload (F3+T); `JeiStarter.stop` on
`LoggingOut`. Phases of `JeiStarter.start` (0.6.1 log): checks/registry access/config ~0 s; subtypes + ingredients ~3 s
(builds creative-tab contents); categories + recipes ~48 s; "Building runtime" + "Registering Runtime" ~1.4 s
(`ForgeGuiPlugin` -> `JeiGuiStarter.start`: overlays, input handlers, ingredient list/filter, returns event handlers
that get registered on the Forge bus); "Sending Runtime" ~0.5 s (`onRuntimeAvailable` to every plugin), then
`Internal.setRuntime`, `verifyClientRecipes`.

Design that holds up:
- Worker runs everything up to the runtime (~51 s). Main thread runs "Registering Runtime", "Sending Runtime" and
  `Internal.setRuntime` via `Minecraft.submit(...).join()` (worker blocked meanwhile): ~2 s in-world hitch.
- `StartupScope` must follow the work: worker during the worker part, main thread during the hops (worker is
  blocked, so features never see two threads). Otherwise `BackgroundSearchIndex` is skipped (filter is built in the hop).
- Per-plugin main-thread list: wrap the consumer in `PluginCaller.callOnPlugins` so listed plugin uids run every step on
  the main thread. Default `kubejs:jei` (its `registerRecipes` runs `JEIEvents.information` scripts; Rhino is not
  thread-safe and `tooltips.js` etc. run on the main thread). `hideItems`/`addItems` fire in `onRuntimeAvailable`, already
  on the main thread.
- Stop/restart while the worker runs: set a cancel flag, have the wrapped consumer skip the remaining plugins, then run
  `JeiStarter.stop` (and any queued start) on the main thread after the worker ends. Never block the main thread on the
  worker while the worker waits on the main thread (deadlock).
- Detection: JEI's `PluginCaller` catches `RuntimeException`/`LinkageError` per plugin and logs
  `Caught an error from mod plugin` (baseline: 0 in the 0.6.1 log). Compare that count and the per-category recipe
  counts in the report between a sync and an async run.
- Found in use: plugins waiting with `Minecraft.managedBlock` from the worker drain the main thread's task queue on the
  worker (JER loot tables). Handled since 0.8.1. Watch the log for `Error executing task on Client` from the worker.
- Known hazards: font glyph-info caches (widgets measuring text), creative-tab rebuild races (main thread rebuilds when
  a creative inventory opens), plugins asserting the render thread (show up as caught plugin errors), mods reading
  player/level state. Opt-in only.

Async vs sync, full comparison (2026-11-21): sync baseline `logs/forgepreheat-jei-20261121-155047.txt` (0.7.2,
`asyncSearch` only: JEI start 53.0 s, 540,552 recipes, 623 categories, 0 plugin errors) vs async
`logs/forgepreheat-jei-20261121-152809.txt` (0.7.1: 576,796 recipes, 624 categories). 614 categories identical. Differ, all
tag-related: fuel categories (`minecraft:fuel` 1,112 -> 4,342, generatorgalore:solid_fuels 7,784 -> 30,394,
ironfurnaces:generator_regular and industrialforegoing:mycelial_furnace 979 -> 4,210, thermal:stirling_fuel 960 -> 4,170,
thermal:disenchantment_fuel 149 -> 297), anvil +186 (not itemised), grindstone -51 (= 51 Tinkers dagger self-repair recipes that only a sync start lists),
jeresources:enchantment +1; `elementalcraft:purification` exists only in async (ElementalCraft builds it on TagsUpdated,
which a sync JEI start always precedes). So a normal JEI start in this pack shows incomplete fuel data and no
purification recipes.

## Client startup findings (2026-11-21)

Launch to title: 84-93 s on earlier days, 70-79 s on 2026-11-21. Breakdown of the 70.7 s launch at 16:10 from log
timestamps (complete by construction), with what JFR (`jei-join.jfr`, 13:13 launch, 88.9 s incl. JFR overhead; copied
to the scratchpad as `startup.jfr`) shows inside each phase:

| s after start | Phase | Dur | Inside (JFR / log) | Mod can fix? |
|---|---|---|---|---|
| 0-11 | ModLauncher: discover/open 349 jars, early window | 11 s | main mostly native: `CreateFile0`, zip `inflate` (disk bound) | no (before mods); antivirus/disk |
| 11-17 | module layer, mixin setup, bootstrap begins (ModernFix "bootstrap stage" at 18.2 s) | 6 s | `ModuleLayerHandler`, mixin config | no |
| 17-25 | vanilla bootstrap, window + GL init, Iris/Oculus loads the shader pack | 8 s | `org.anarres.cpp` preprocessing, GL | shaders are a user setting |
| 25-36 | mod construction on modloading workers, main waits | 11 s | IE, Mekanism, Create, KubeJS, Supplementaries, ... | per mod only |
| 36-46 | registry events on main | 10 s | `ObjectHolderRegistry.applyObjectHolders` runs every holder once per registry (`GameData.postRegisterEvents` passes `registry::equals`); `RegistryObject.registryExists` | **B**: index holders per registry. ModernFix `object_holder_cleanup` (clears after the loop) and `forge_registry_lambda` (memory) don't. Forge core, high care |
| 46-62 | resource reload on workers (models/textures; `ModelEvent.ModifyBakingResult` 3.7 s: framedblocks 1.5 s, modernfix 0.5, immersiveconvergence 0.4, ...); main-thread deferred tasks: gtceu 1.5 s, sophisticatedbackpackscreateintegration 2.9 s | 16 s | reload workers busy, main mostly waiting | per mod; the 2.9 s deferred task is worth a look |
| 62-71 | sound engine, atlas upload, shader compile (vanilla/mod core shaders + Iris pack), Registrate cleanup, title | 9 s | `ListenerList.unregister` (Registrate), `antlr`/glsl_transformer | **A**: skip Registrate's cleanup (~4 s, see below) |

A: Registrate `OneTimeEventReceiver.onLoadComplete` (Registrate 1.3.11 from GTCEu's jar-in-jar) unregisters every
consumed one-time listener; Forge's `EventBus.unregister` -> `ListenerList.unregisterAll` scans every event type's
listener list per listener. The receivers are inert after firing (`consumed` AtomicBoolean), so skipping the cleanup
changes no behavior, only keeps them in memory.

## Related mods (checked 2026-11-21, author claims only)

- **JEIOptimizer** (Modrinth `jeioptimizer`): parallel build of JEI's `IngredientFilter` suffix trees (default
  `PARALLEL_FULL`), experimental `filter.async` (background build), experimental parallel plugin registration (author:
  Theurgy, Ars Nouveau, Compact Machines crash) and parallel creative tabs (author: lost 22% of items). Suggests
  `tooltipSearchMode = DISABLED` in `jei-client.ini` (~8 s). Page metadata lists Forge 1.20.1 but its description only
  NeoForge 1.21.1: unconfirmed. Overlaps `BackgroundSearchIndex` (both touch `IngredientFilter`): don't combine.
- **LightSpeedRe** (`lightspeed-1.20.1-2.0.5+hotfix.jar`): resource-pack index preload/cache, dedicated reload executor,
  indexed resource lookup, plus registry patches overlapping our B (`ObjectHolderRegistryMixin` cancels
  `applyObjectHolders` at HEAD and routes per registry; `GameDataMixin` wraps RegisterEvent posting and holder
  application with MixinExtras `WrapOperation`, so MixinExtras is available in this pack). Optional bootstrap Java agent
  (`installBootstrapAgent`, off by default) could reach the pre-mod phase. Tested 2026-11-21 with both ForgePreheat
  startup features off (JVM args left from the A/B test): title 77.3 s (first launch, builds `lightspeed-cache`) and
  72.2 s (second) vs 71.7 s without it: no measurable gain here. With both mods, its HEAD cancel means our object
  holder index never runs (no conflict, no double work). Agent not tested.
- FastBoot: Modrinth page gone. DashLoader: content cache (other loader). EMI: JEI alternative, reloads in the background
  by design (EMI Accelerator adds caching): a pack-level change, not ours.

## Version history

| Version | Change | Measured |
|---|---|---|
| 0.1.0 | Profiler only | join 138.9 s, JEI start 113.4 s |
| 0.2.0 | Shared converted ingredient list (fuel list) | join 92.6 s, JEI start 68.9 s, `jei:minecraft` 68 s -> 18.5 s, furnace 28.1 -> 2.6 s, blasting 22.9 -> 1.9 s |
| 0.3.0 | Skip `broadcastChanges` on JEI's fake anvil/grindstone menus | JEI start 65.7 s, but grindstone recipes show `All slots must have the same number of ingredients` (empty output) |
| 0.3.1 | Menu skip made opt-in (`-Djeijoinprofiler.menuSkip=true`) | join test 2026-11-21: grindstone recipes display correctly (user checked), 0 `Recipe crashed`, 0 `InvalidInjectionException`; JEI start 66.3 s vs 65.7 s with the skip on, so the skip saved ~0.5 s, not ~10 s. Game start 72.0 s. |
| 0.4.0 | Create exclusion index, KubeJS hide-event list reuse | built, never installed |
| 0.4.1 | Refactor to the architecture above (no behavior change vs 0.4.0) | built, not installed |
| 0.4.2 | Rename to ForgePreheat (mod id, package, toggles, report name); no behavior change vs 0.4.1 | installed 2026-11-21: JEI start 59.4 s (vs 66.3 s on 0.3.1), addRecipe 23.8 s; server connect to JEI done 71 s (vs 79 s); game start 70.3 s. Create categories look right (user checked); 0 errors. KubeJS hides checked (single id, id list, tag): still hidden. The `{mod, custom}` rule in `jei_hide.js` never hid anything, because KubeJS 2001.6.5 `IngredientJS.of` ignores those keys and returns an empty ingredient; this is a pack bug, not ours. The user didn't notice the difference: about 10% of the join. A crash during this session (2026-11-21 13:33) was not ours: More Overlays' inventory search highlight calls Create's `ZapperItem` tooltip with a null level (NPE) |
| 0.5.0 | Background search index (opt-in): JEI's own `ElementSearch` constructor runs on a daemon worker; uid map for lookups built on the render thread; first text search waits for the worker; falls back to a render-thread build if the worker throws | tested 2026-11-21 with the flag set in the instance: JEI start 52.6 s (vs 59.4 s on 0.4.2), server connect to JEI done 65 s (vs 71 s), worker done 7 s after JEI start; searches (name, @mod, $tag) work (user checked); 0 errors, no worker exceptions. Keep opt-in until a few more clean sessions |
| 0.5.1 | Profiling only: `StepTimings` plus six `@Pseudo` GTCEu timing mixins (multiblock registration and preview widgets; recipe registration, representative recipes, widget slots/text/buttons, UI template, editable template) | measured 2026-11-21, see the GregTech findings row. JEI start 53.6 s (noise vs 52.6 s on 0.5.0) |
| 0.5.2 | Profiling only: `VanillaPluginMixin` redirects each `addRecipes` call in `VanillaPlugin.registerRecipes`; `StepTimings` laps book "make <type>" (time since the previous add) and "add <type>" | measured 2026-11-21: `jei:minecraft` recipes 17.9 s = make grindstone 4.75, make anvil 3.62, add crafting 2.49, add furnace 2.38, add blasting 1.80, add anvil 1.23, add grindstone 0.74, brewing 0.31, rest < 0.5 |
| 0.6.0 | `GrindstoneStackReuse` (default on): one shared stack per item in `GrindstoneRecipeMaker.getDisenchantRecipes` (redirect of `new ItemStack(ItemLike)`), replaced by a fresh stack whenever `ItemStack.matches` against a pristine twin fails | tested 2026-11-21: grindstone recipes look right and count unchanged (user checked), 0 errors; 5,996,566 stacks reused, 2,674 replaced (mods do change stacks during the checks), 43,160 items. But make grindstone 4.81 s vs 4.75 s: **no gain**. Stack construction was not the cost (JFR sample bias), or `ItemStack.matches` (Forge compares capabilities) costs as much. JEI start 51.6 s |
| 0.6.1 | `GrindstoneStackReuse` off by default (no gain). Profiling: `GrindstoneRecipeMakerMixin` times the disenchant loop, its stack creation and `isEnchantable` (redirects), `canEnchant` and `getGrindstoneRecipe` (menu result, disenchant + repair) | measured 2026-11-21: disenchant loop 5.28 s; new stack 0.73 s (6,042,400), isEnchantable 0.20 s (6,042,400), canEnchant 0.10 s (747,040), menu result 3.48 s (145,998 calls, ~24 us each: Forge `RecipeHelper.getGrindstoneResult` sets both slots, each triggering `createResult` with `ForgeHooks.onGrindstoneChange` events and `broadcastChanges`). Timer overhead included. JEI start 54.2 s |
| 0.7.0 | `AsyncJeiStart` prototype (opt-in `-Dforgepreheat.asyncStart=true`): `JeiStarterMixin` hands `start` to a daemon worker and defers `stop` behind it; `PluginCallerMixin` runs the "Registering Runtime"/"Sending Runtime" steps on the main thread via `MainThreadBridge` (holds the main thread until start returns) and wraps plugin calls (cancel skip, `asyncStartMainThreadPlugins` default `kubejs:jei` per-plugin hops). `StartupScope` owner is now global and moves with the hops | tested 2026-11-21 (JEI not checked in game yet): "Loading terrain" < 15 s (was ~65 s); worker 99.9 s; main thread 2.23 s for 17 tasks (Registering Runtime 1.34 s, Sending Runtime 0.89 s, of which kubejs 0.80 s; 15 kubejs plugin steps); 0 plugin errors. User felt a 1-2 s hitch on the first inventory open (cause unknown, not a main-thread task of ours) and one when JEI finished. Differences vs the sync baseline `logs/forgepreheat-jei-20261121-144618.txt` (0.6.1): fuel list ~4,066 stacks vs ~1,047, generatorgalore:solid_fuels 30,394 vs 7,784, ingredients 70,405 vs 70,078, anvil 96,735 vs 96,549, grindstone 143,828 vs 143,879. Cause: the server sends tags after recipes; Forge rebuilds `ForgeHooks.VANILLA_BURNS` in `ForgeInternalHandler.tagsUpdated`; a sync JEI start always runs before that, the async one raced it. PneumaticCraft `no world available` errors also occur in sync runs (12 each), not ours |
| 0.7.1 | Async start waits for the server's tags (`TagsUpdatedEvent` CLIENT_PACKET_RECEIVED, 5 s fallback via client tick) before starting the worker, so results are deterministic (full fuel list); report gains a full sorted "Recipe count per category (all)" section | tested 2026-11-21: connect 15:26:19, worker started 20 s later (waited 8.13 s for the tags packet), JEI done 110 s after connect; worker 90.2 s; main thread 2.18 s for 17 tasks; 0 plugin errors, 0 crashed layouts, 0 injection errors. User checks all fine (KubeJS info page, hidden items, grindstone, Create, GT, multiblock, recipe transfer); <0.5 s hitch opening the Mechanical Press page (first layout build, likely normal). Counts identical to 0.7.0 across 55 shared categories (deterministic now). Vs sync 0.6.1 only generatorgalore:solid_fuels 7,784 -> 30,394, anvil 96,549 -> 96,735, grindstone 143,879 -> 143,828 differ, plus 327 more ingredients: all attributed to JEI seeing the server's tags. Report now has the full count section |
| 0.7.2 | Cancelled starts skip both main-thread steps. Logout test on 0.7.1 (2026-11-21): stop parked, worker skipped the rest (0 recipes, start returned after 17 s), stop ran on the main thread, clean shutdown; the only error was `jei:forge_gui` failing on `minecraft.level must not be null` in the GUI step, caught by JEI, runtime built without it. 0.7.2 skips that step instead | installed 2026-11-21, not yet tested |
| 0.7.3 | Profiling: `RecipeIdDump` writes `logs/forgepreheat-ids-<timestamp>.txt` with the sorted recipe ids (`IRecipeCategory.getRegistryName`, "(rejected)" when `addRecipe` returned false) of the categories in `-Dforgepreheat.dumpCategories=<uid>,...` | run 2026-11-21: sync `logs/forgepreheat-ids-20261121-160020.txt` vs async `logs/forgepreheat-ids-20261121-160452.txt`. Grindstone: the only difference is 51 `grindstone.self_repair.item.tconstruct.dagger` recipes (one per material variant) present in sync, absent in async; all other 143,828 ids identical. Tinkers' `ModifiableItem.isRepairable` returns false, so with the server's tags the dagger gets no grindstone repair: async likely correct. Anvil: JEI anvil recipes have no registry name (dump shows the class), so the +186 can't be itemised without dumping inputs/outputs |
| 0.7.4 | Profiling: report tables gain "setup s" (`IngredientSupplierHelper.getIngredientSupplier`, i.e. the category's setRecipe building ingredient lists) and "index s" (`RecipeMap.addRecipe` per role) per category. Reason: async furnace addRecipe is ~13 s vs 2.6 s sync with a ~4,070-stack fuel list; the cache's own list copy is only ~35M reference copies, so the cost must be in JEI | run 2026-11-21 (async, `logs/forgepreheat-jei-20261121-161259.txt`, JEI start 84.7 s, addRecipe 43.8 s): furnace total 10.40 = setup 0.26 + index 10.13 s; blasting 8.96 = 0.20 + 8.76; all listed categories setup 11.6 s, index 29.4 s. JEI's `RecipeMap.addRecipe` per ingredient: `getIngredientUid` (cheap base-item path unless the item has subtypes), a per-recipe `IngredientUidIndex` dedupe, then global `ingredientUidToCategoryMap` + `recipeTable.addExact`. With ~4,070 fuels x ~7,900 cooking recipes that is ~35M real (fuel, recipe) entries JEI keeps for R/U lookups; cutting it would mean replacing JEI's index structure (not worth it). Background-only cost |
| 0.8.0 | Defaults: `asyncStart` and `asyncSearch` on (user's decision after testing; behavior differs from plain JEI only by the tag-based fuel data, see the comparison above). Background search index build is cancelled when JEI stops (`PrefixInfoMixin` on `PrefixInfo.getStrings`), so quitting early no longer floods the log with tooltip errors. Removed `FakeMenuSyncSkip` + `AbstractContainerMenuMixin` (broke grindstone, ~0.5 s) and `GrindstoneStackReuse` + `GrindstoneRecipeMakerMixin` (no gain, and its 6M-call loop timers cost time every start) | installed 2026-11-21 |
| 0.8.1 | Fix: JER's `LootTableHelper.getLootDataManager` (in its category registration) reloads server data with Minecraft as the apply executor and waits with `Minecraft.managedBlock`, which on the JEI worker ran the main thread's task queue there (0.8.0 log: 12,088 `Error executing task on Client: RunningOnDifferentThreadException` from bounced packets in 3 s; other queued tasks may have run off-thread too). `BlockableEventLoopMixin` (Minecraft target, refmap `m_18701_`) makes the worker wait without draining; if the main thread is held by the start, the wait runs there | verified 2026-11-21 on 0.9.1: 1 wait kept off the main queue, 0 `Error executing task on Client` |
| 0.9.0 | Client startup: `ClientStartSession`, `PhaseFeature`, `StartupTimings`. A: `RegistrateCleanupSkip` (compat redirect of the `toUnregister.forEach` in `OneTimeEventReceiver.lambda$onLoadComplete$1`; off: times the original). B: `ObjectHolderIndex` (redirect of `objectHolders.forEach` in `ObjectHolderRegistry.applyObjectHolders`, Forge's own per-holder lambda runs only the holders that need it; `RegistryObject$1` state read via VarHandles in `RegistryObjectHolders`; verify with `-Dforgepreheat.objectHolderVerify=true`; off: times Forge's loop). Mixin AP 0.8.5 crashes (`SignaturePrinter` StringIndexOutOfBounds) on some @Shadow/@Accessor combinations of bytecode-only targets, hence redirects and VarHandles | tested 2026-11-21 (3 launches): verify run 0 mismatches over 140 passes. Index: 140 passes all indexed, 8 rebuilds, 795,156 holder calls instead of ~10.7M (9,941,588 skipped), 4,662 holders always run (optional registries / non-RegistryObject). Timings: object holder passes 1.38 s (Forge) -> 0.40 s (index); Registrate cleanup of 31,528 listeners 2.84 s -> skipped. Title screen 71.7 s (both off) -> 67.7 s (both on). Note: the JFR guesses were ~9 s for the registry loop and ~4 s for Registrate; real 1.38 s and 2.84 s |
| 0.9.1 | Profiling: index time split per ingredient role (`RecipeMapMixin` reads `RecipeMap.role`), with entry counts, plus render-only index time per category. Reason: JEI keeps one `RecipeMap` per role and only reads one for a focus of that role (R/U use OUTPUT/INPUT; no JEI code builds a RENDER_ONLY focus); the cooking categories put the whole fuel list in a RENDER_ONLY slot unless the recipe needs a specific fuel | run 2026-11-21 (`logs/forgepreheat-jei-20261121-204225.txt`, async, JEI start 83.1 s, addRecipe 44.3 s): index INPUT 8.96 s (5.30M entries), OUTPUT 2.06 s (0.83M), CATALYST 0.17 s, **RENDER_ONLY 20.01 s (35.30M entries)**; render-only furnace 9.73 s, blasting 9.03 s, tag_recipes 0.74 s, grindstone 0.24 s. 0.8.1 managedBlock fix verified: 1 wait kept off the main queue, 0 `Error executing task on Client`, 0 plugin errors. Title 67.6 s |
| 0.9.2 | `LazyRenderOnlyIndex` (default on, identical results): `RecipeMapMixin` defers the RENDER_ONLY map's `addRecipe` calls during JEI's start into `DeferredRecipeIndex` and cancels its `compact`; after the start a low-priority daemon thread replays them in order (then compacts). Every read of that map (`getRecipeTypes`, `getRecipes`, `isCatalystForRecipeCategory`, `addCatalystForCategory`) builds it first under the same lock; a JEI stop cancels the background build. `-Dforgepreheat.renderOnlyIndexBackground=false` builds only on first read | tested 2026-11-21 (2 joins): JEI start 83.1 s -> 66.2 / 64.5 s, addRecipe 44.3 -> 23.4 / 23.7 s; per-category recipe counts identical to 0.9.1; background build 24.8 s at low priority, finished 25 s after JEI was ready, 0 reads waited for it; quitting early stops it cleanly; 0 plugin errors, 0 failed deferred entries; all other errors also in 0.9.1. INPUT entries vary run to run (5.30M, 5.09M, 4.90M) with identical counts, in runs with and without the deferral, so it is the pack, not us. Seen in both 0.9.x async logs: JER `Failed loading villager cartographer` (NPE, `trader` null) on the JEI worker: not checked against a sync start yet |
| 0.9.3 | `ParallelVanillaMakers` (default on, toggle `parallelVanillaRecipes`): `VanillaPluginMixin` starts `AnvilRecipeMaker.getAnvilRecipes` and `GrindstoneRecipeMaker.getGrindstoneRecipes` on their own daemon threads at `registerRecipes` HEAD and redirects the two maker calls to join them (exceptions rethrown unchanged). Safe because each maker uses its own static fake menu (`AnvilHelper.ANVIL_MENU`, `GrindstoneHelper.GRINDSTONE_MENU`) and only reads the player, registries and the ingredient list; they overlap the crafting/furnace/blasting adds (~8 s) and each other | tested 2026-11-21 (`logs/forgepreheat-jei-20261121-211036.txt`): JEI start 64.5 -> 60.1 s, `jei:minecraft` recipes 16.6 -> 10.3 s; JEI waited 1.34 s for the lists; on their own threads anvil took 7.30 s and grindstone 7.84 s (4.3 and 5.3 s when serial: contention), add crafting 3.2 -> 4.6 s for the same reason; per-category counts identical, 0 plugin errors, no new errors |
| 0.9.4 | Profiling: `HitchSampler` (default on, `hitchSampler`). In-world frames (end to end of `RenderTickEvent`, so ticks, tasks and screen opens are included) over 100 ms get the render thread's stack sampled every 5 ms by a watchdog thread; frames over 300 ms log `[ForgePreheat] Hitch:` with the screen, per-method sample shares and the top stacks (max 30 reports). Reason: the 1-2 s hitch on the first inventory open before JEI is loaded, and the one when JEI finishes; JFR samples the main thread too rarely | tested 2026-11-21 (creative mode, inventory opened before JEI was done and held open when it finished): see 0.9.5 |
| 0.9.5 | Hitch fixes from the 0.9.4 sampler log. (1) 44.7 s freeze when JEI finished with the creative inventory open: in "Sending Runtime" (main thread) KubeJS hides items in many batches (`IngredientManager.removeIngredientsAtRuntime` -> `IngredientBlacklistInternal.onIngredientsRemoved` -> `IngredientFilter.onIngredientsVisibilityChanged`), and every batch's `notifyListenersOfChange` made the open overlay's `IngredientGridWithNavigation` re-filter all ingredients (`IngredientFilter.getElements`, 5,825 of 6,411 samples). `FilterUpdateBatching` (ScopedFeature, `filterUpdateBatching`) holds the notifications back during JEI's start (main-thread hops included) and sends one per filter at the end (via `Minecraft.execute` when the start ends on the worker); `IngredientFilterMixin` shadows the private notify and implements `FilterListenerNotifier`. JEI start that run: 102.3 s, because the held main thread was frozen. (2) 4.5 s first-open hitch in creative: `CreativeModeInventoryScreen.<init>` -> `CreativeModeTabs.tryRebuildTabContents` builds every tab on the main thread (UtilitiX `ModCreativeTab.onCreateTabs` ~37%), overlapping JEI's own tab build in `ItemStackListFactory.create` on the worker (21:15:20-25). The rebuild itself is vanilla (JEI builds tabs per tab and never sets `CreativeModeTabs`' cached parameters, so plain JEI has it too); the overlap is ours. `CreativeTabBuildLock` (`creativeTabLock`): JEI's tab forEach and both screen call sites (`<init>`, `tryRefreshInvalidatedTabs`, refmap `m_269226_`/`m_257967_`) take one lock; the main thread waits at most 30 s then builds anyway | crashed at startup 2026-11-21: the interface sat in `forgepreheat.mixin.access`, inside the mixin package, so Mixin refused to load it (`IllegalClassLoadError ... is in a defined mixin package`) when ModernFix's `JEIBackedSearchTree` loaded `IngredientFilter` |
| 0.9.6 | 0.9.5 with `FilterListenerNotifier` moved to `optimize` | tested 2026-11-21 (creative, inventory opened before JEI was done and held open while it finished): JEI start 62.1 s, counts identical, 0 plugin errors. 9,368 filter notifications sent as 1; the JEI-finished frame 44.7 s -> 2.67 s (left: `JeiGuiStarter.start` ~48%, KubeJS `HideJEIEventJS.hide` scripts ~32%: JEI's normal main-thread steps). Creative screen waited 0.64 s on `CreativeTabBuildLock`; first open still 3.39 s, of which UtilitiX `ModCreativeTab.onCreateTabs` ~44%: for every tab's `BuildCreativeModeTabContentsEvent` it walks all ~43,000 registry items and calls `ForgeRegistries.ITEMS.getKey` on each to find its own (fix candidate: compat redirect of `getValues()` to a cached list of its own items). Join: one 14.8 s frame on `ReceivingLevelScreen` handling `ClientboundUpdateRecipesPacket` on the main thread; ~53% ElementalCraft `PureOreManager.reload` via dpanvil `DataHandler.onTagsUpdated` (fired from `ClientPacketListener.fireDeferredTags`): `AbstractPureOreRecipeInjector.getRecipe` makes a new `ItemStack` per (ore, recipe) and tests every recipe's ingredient |
| 0.9.7 | Compat. `PureOreRecipeIndex` (`pureOreIndex`, verify `pureOreIndexVerify`): `ElementalCraftPureOreInjectorMixin` redirects the `Collection.stream()` in `AbstractPureOreRecipeInjector.getRecipe(Item)` to a stream of only the recipes whose first ingredient lists the item (plus every recipe whose ingredient is not `isSimple()`, empty or unreadable), in the original order; ElementalCraft's own filter and `min` run on that. Only for the cooking and grinding injectors (their filter requires the first ingredient to match). `ElementalCraftPureOreManagerMixin` times `PureOreManager.reload`. Baseline: ElementalCraft logs "Pure ore generation ended in 7597ms", JEI shows 448 `elementalcraft:purification` recipes. `UtilitiXTabItems` (`utilitixTabItems`): `UtilitiXCreativeTabMixin` redirects `IForgeRegistry.getValues()` in `ModCreativeTab.onCreateTabs` to the items in UtilitiX's namespace (mod id read from `UtilitiX.getInstance().modid`) | tested 2026-11-21 with `pureOreIndexVerify=true`: 18,032 lookups tested 11,281 instead of 35,924,252 recipes, verify 0 mismatches; 448 purification recipes and all JEI category counts identical; pure-ore generation 7.60 -> 4.97 s, but the rest is the verify check itself (it re-tests every skipped recipe; samples are in `check`), so a run without verify is still needed. Join frame 14.8 -> 12.0 s (incl. verify). First creative open 3.39 -> 1.68 s (user: "a lot better"); JEI-finished frame 3.16 s (user: hardly noticed). JEI start 57.9 s, 0 plugin errors |
| 0.9.8 | Compat. 0.9.7 without verify (2026-11-21): pure-ore generation 7.60 -> 1.02 s, join frame (`ClientboundUpdateRecipesPacket` on `ReceivingLevelScreen`) 14.8 -> 8.1 s, counts identical. Next in that frame: Powah's wiki (`owmii.powah.lib.client.wiki.Wiki`, RecipesUpdated via Architectury) ~27%: for each Powah item `lambda$static$3` calls `RecipeManager.getAllRecipesFor(CRAFTING)` twice (the second list is stored as "smelting") and checks every recipe's `getResultItem(...).is(item)`. `PowahWikiRecipes` (`powahWikiIndex`): `PowahWikiMixin` marks the update (`lambda$static$5` HEAD/RETURN) and redirects both calls (refmap `m_44013_`, Wiki arg captured with `@Coerce Object`) to the crafting recipes whose result is the item, in order; recipes whose result throws stay in every list. Rest of the frame: other `RecipesUpdatedEvent` listeners (~15%) and tag listeners | tested 2026-11-21: Powah "Wiki recipes collecting completed in" 2127 -> 258 ms (288 lookups, index of 35,745 crafting recipes); join frame 8.1 -> 6.3 s; JEI counts identical, 0 plugin errors. Left in the 6.3 s frame (sampler shares, rough): ElementalCraft pure ores ~18% (1.06 s: per-lookup `new ItemStack` for the remaining candidates, `ECTags.getTag`), Thermal `ThermalRecipeManagers.refreshClient` ~18%, other `PacketUtils` tasks ~24%, the rest spread. Each is ~1 s or less; the sampler's 40-method/4-stack output no longer separates them well |
| 0.10.0 | Back to JEI start (0.9.8 log: start 59.9 s; Registering recipes 40.7 s: jei:minecraft 10.7, gtceu 9.3, create 6.7, irons_spellbooks 3.1, industrialforegoing 2.0, tconstruct 1.3, jei:internal 1.3; categories 7.2 s, of which jeresources 5.9; ingredients 3.8; configuring 2.4 (kubejs); index INPUT 10.4 s / 5.0M entries, OUTPUT 2.5, CATALYST 0.15). (1) `ParallelRecipeIndex` (`parallelRecipeIndex`): `RecipeMapMixin` queues the INPUT/OUTPUT/CATALYST maps' `addRecipe` calls to one `PipelinedRecipeIndex` thread per map, in order; compact, every read and any later direct add wait until the queue is drained. Known difference: JEI drops a recipe whose indexing throws, here it stays (failures counted in the summary). (2) Loading notice: `ui.JeiLoadingNotice` (`loadingNotice`) draws a vanilla/JEI-style gray panel right of any container screen while `AsyncJeiStart.isLoading()`: "JEI is loading", the phase (`PluginCaller` title, or "Waiting for server data"), "about N s left" from `ui.JeiStartProgress`, which saves each phase's offset of the last non-cancelled start in `config/forgepreheat-jei-timing.properties` (no estimate on the first start) | tested 2026-11-21, two joins in one session. Join 1: JEI start 59.9 -> 55.8 s, addRecipe 26.7 -> 17.4 s, Registering recipes 40.7 -> 36.8 s; index threads INPUT 16.8 s, OUTPUT 6.9 s, CATALYST 0.9 s (slower than inline from contention), JEI waited 0.00 s, 0 failures; counts identical to 0.9.3. Gain smaller than the 9 s addRecipe drop: the extra threads slow the rest. Join 2 (rejoin, warm): 35.4 s; crafting +154 and thermal:disenchantment_fuel 297 -> 593 vs a fresh join, i.e. Thermal re-adds its fuels on a rejoin (not checked against a rejoin without ForgePreheat). Countdown accurate to ~1 s (user). User wants flat text centered in the drawer instead of a panel |
| 0.10.1 | Loading notice as plain white shadowed text, centered horizontally and vertically in the strip right of the container | tested 2026-11-21 with `parallelRecipeIndex=false`: join 60.7 s, rejoin 39.4 s (vs 55.8 / 35.4 s with it on), and the rejoin shows the same crafting +154 and thermal:disenchantment_fuel 297 -> 593, so that is the pack, not the index. User: text looks better, wants it further right |
| 0.10.2 | Loading text centered at 75% of the strip width (clamped inside it) | installed 2026-11-21, not yet tested |
| 0.10.3 | Profiling for Create (6.7 s) and JER (5.9 s categories), generic for every plugin. `PluginStepTimings` (`pluginSteps`, default on): `PluginCallerMixin` wraps each plugin call (current plugin uid, read by the sampler) and `RecipeRegistrationMixin` (HEAD/RETURN of `RecipeRegistration.addRecipes`) books "uid > make <type>" (time since the plugin started or its previous add), "uid > add <type>" and "uid > after last add"; slowest 30 in the summary. `JeiStartSampler` (opt-in `startSampler`): samples `StartupScope.owner()` every 5 ms, grouped by phase + plugin (+ "(main thread)"), writes `logs/forgepreheat-jei-samples-<ts>.txt` with per-step method shares and top stacks for steps with >= 100 samples | run 2026-11-21 with the sampler (`logs/forgepreheat-jei-samples-20261121-223351.txt`; JEI start 60.4 s incl. sampler cost). Slowest plugin steps: jei:minecraft make anvil 4.06 s (wait for the parallel makers), create make spout_filling 3.58 s, gtceu add ore_forging 2.14 s, gtceu make multiblock_info 2.06 s, industrialforegoing make stone_work 2.04 s, irons_spellbooks add arcane_anvil 1.71 s, jei:minecraft make fuel 1.21 s. Create: `SpoutCategory.consumeRecipes` tries every fluid-holding item against every fluid (capability gathering on stack copies 28%, Tinkers tool fluid caps, `FluidStack.copy`). JER: 81% of its categories step is waiting (`managedBlock`) in `LootTableHelper.getLootDataManager`, which builds its own `LootDataManager` once per session (static field) by reloading all mods' SERVER_DATA loot tables |
| 0.10.4 | `JerLootPrefetch` (`jerLootPrefetch`): `AsyncJeiStart.dispatch` starts a daemon thread that calls JER's own `getLootDataManager()` (reflection) during the wait for the server's tags; `JerLootTableHelperMixin` (compat, HEAD) makes JER's call wait for it (main thread waits with `managedBlock`, others join); on failure JER's `lootDataManager` field is reset to null so JER loads it itself. The prefetch thread is registered with `AsyncJeiStart.parkManagedWaits`, so its `managedBlock` parks instead of draining the main queue | tested 2026-11-21: prefetch took 7.85 s in the background (contended with the join), finished before JER asked (no wait); JER categories 5.9 -> 0.97 s, Registering categories 7.2 -> 3.9 s, JEI start 55.8 -> 53.6 s; all counts identical incl. every jeresources category; 0 plugin errors (the JER cartographer warning is the known one) |
| 0.10.5 | Code review fixes. `PipelinedRecipeIndex`: the consumer thread used to exit on an interrupt or an uncaught `Error`, leaving entries queued so compact and every later read would wait forever; it now keeps waiting on interrupts and logs and counts any `Throwable` per entry. `RecipeMapMixin`/`DeferredRecipeIndex`: a direct `addRecipe` on the render-only map after the start (a runtime recipe add) raced the background replay on the same map; it now finishes the replay first (`buildBeforeDirectAdd`). `mod_description` rewritten (it still described 0.4). Known, not fixed: when `JeiStarter.start` throws, `JeiStartSession.end()` never runs (no RETURN), so the scope owner and index threads stay until the next start | tested 2026-11-21: JEI start 53.6 s, counts identical, 0 index failures, 0 plugin errors, 0 injection errors; JER prefetch 7.6 s in the background. Title screen 84.4 s that launch (vs ~70 s before; no startup code changed, so likely disk/cache variance) |
| 0.10.6 | Fix (reported 2026-11-24): GTCEu multiblock previews in JEI were empty since async start (0.8.0). LDLib's `LDLib.isRemote()` is `isClient() && Minecraft.isSameThread()`, so a `SceneWidget` built on the JEI worker skips `createScene` (no renderer) and `setRenderedCore`. `GTCEuMultiblockPreviewMixin` (compat) redirects the `Stream.toList()` in `MultiblockInfoCategory.registerRecipes` (the lazy stream builds every `MultiblockInfoWrapper` there) to `AsyncJeiStart.callOnMainThread`, a `MainThreadBridge` hop; expected main-thread cost ~1.3 s (0.5.1: multiblock step 1.31 s). The 0.7.1 "multiblock" check likely saw the page, not the 3D view. JEI now comes from the BlameJared maven instead of the pack folder | tested 2026-11-24 on the Mac pack (JEI start ~30 s there vs ~54 s on Windows), baseline 0.10.5 `logs/forgepreheat-jei-20261124-103327.txt` (previews empty, user reproduced) vs `logs/forgepreheat-jei-20261124-103636.txt`: previews render (user checked); main thread 1.55 s / 17 tasks -> 2.38 s / 18 tasks, seen as one 933 ms frame in the inventory (sampler: 98% `PatternPreviewWidget.getPatternWidget` in our hop); 158 previews in 0.91 s; JEI start 31.8 -> 29.4 s (noise); 624 categories and 576,788 recipes, every count identical; 0 plugin errors, 0 injection errors, 0 crashed layouts |

## Where to pick up

Git: `github.com:rtgschepers/forgepreheat`, branch `main`. Commit only when the user asks; one-line messages, never a
co-author or attribution line. The JFR JVM argument was removed from the instance on 2026-11-21; the user's JVM
arguments may still contain `-Dforgepreheat.asyncSearch=true -Dforgepreheat.asyncStart=true` (harmless since 0.8.0).

Status 2026-11-21: join ~140 s -> < 15 s (JEI ready ~1.5 min later in the background). The user plays with 0.8.0 and
reports problems. Open topics, the user picks:
1. **Speed up JEI's indexing** (required, see Goal; after client startup): addRecipe ~44 s async, of which index ~29 s (furnace + blasting ~19 s from
   ~35M fuel x cooking-recipe entries) and setup ~12 s. JEI keeps every entry for R/U; anything beyond micro-gains means
   changing JEI's index structure.
2. **Client startup** (in progress since 2026-11-21) to the main menu (~70 s now, 93 s originally): `jei-join.jfr` (23 MB, written 13:16 on
   2026-11-21 in the instance folder) is a startup-only capture with JFR overhead. ModernFix, FerriteCore and Fastload
   are in the pack; only target what they leave.
3. Smaller: `IngredientListCache` stores ~43,000 large lists seen once (store on second sighting); GTCEu per-recipe
   widget template (~3.2 s, needs widget cloning, deprioritized); the +186 anvil recipes in async are not itemised.

## Profiling workflow

1. Add `-XX:StartFlightRecording=filename=jei-join.jfr,settings=profile` to the instance's JVM arguments in CurseForge.
2. Join, wait until in-world, quit the game completely (the file is only written on JVM exit). Copy `jei-join.jfr` and
   `latest.log` away before the next launch overwrites them.
3. Export samples: `jfr.exe print --events jdk.ExecutionSample --stack-depth 200 jei-join.jfr > samples.txt`.
4. Analyse with `tools/jfr/*.py` (run with `python -I`). The render thread appears as `"main"` in JFR. JFR timestamps can
   be a few seconds off from the game log, and sample counts are not proportional to time: take durations from the
   log's JEI timings, causes from the samples.
5. Remove the JFR argument afterwards; it writes ~45 MB per launch.
6. JFR execution sampling is throttled across threads: with 100+ threads the main thread got ~10 samples/s, not 100.
   Sample counts can't be turned into durations; use log timestamps or our own timers for durations and JFR only for
   "what runs in this window". `tools/jfr/startup.py` (per-window busy threads and owning mod) and
   `tools/jfr/threadstate.py` (one thread's Java/native/park/sleep/GC per window) help with that.

Inspecting other mods' code: extract the jar into its own temp folder and use `javap -c -l -p -cp <dir> <class>`
(line numbers map JFR frames to bytecode).
