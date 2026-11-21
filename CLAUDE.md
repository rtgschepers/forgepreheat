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
                           StepTimings: named HEAD/RETURN step timer for other mods' plugin internals (fed by compat mixins)
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
| JEI vanilla anvil/grindstone recipe generation. Measured with `StepTimings` (0.5.2): make grindstone 4.75 s, make anvil 3.62 s, plus adding them 1.23 + 0.74 s. Menu sync (`broadcastChanges`) is only ~0.5 s of it (0.3.x A/B). Grindstone: `GrindstoneRecipeMaker.getDisenchantRecipes` loops every non-curse enchantment x every registry item and does `new ItemStack(item)` for each pair (JFR: ~45% of its samples in the `ItemStack` constructor, capability events and constructor mixins), then `isEnchantable`/`canEnchant`; only enchantable pairs get `copy()` + grindstone menu. Anvil: iterates ingredient stacks (no new stack per pair), cost spread over `canEnchant` and the `AnvilMenu` result per recipe | ~10.3 s | the cost is running each recipe through the real grindstone/anvil menu (see 0.6.1). Next candidate: run `AnvilRecipeMaker.getAnvilRecipes` and `GrindstoneRecipeMaker.getGrindstoneRecipes` on worker threads from `VanillaPlugin.registerRecipes` HEAD and join where JEI calls them (~7 s of main-thread work before the anvil call), opt-in |
| KubeJS `jei_hide.js` makes 18 `hide()` passes, each rebuilding every JEI item stack | 5.2 s | fixed in 0.4.x (`KubeJSHideLists`); together with Create: JEI start -6.9 s in 0.4.2 |
| Create `addTypedRecipesExcluding` compares ~4,000 x 3,700 recipe pairs (`doInputsMatch`) | 5.1 s | fixed in 0.4.x (`CreateExclusionIndex`); measured together with KubeJS, see above. The report does not time either phase on its own |
| JEI ingredient filter / search index build (tooltips 37%, substring index 32%) | 7.5 s ("Building ingredient filter" in the log), ~7 s of it `ElementSearch` | 0.5.0 builds `ElementSearch` on a worker thread (`BackgroundSearchIndex`, opt-in `-Dforgepreheat.asyncSearch=true`): filter build 7.46 s -> 0.38 s on the render thread; worker takes 8.4 s |
| GregTech JEI plugin `registerRecipes` 8.03 s, measured with `StepTimings` (0.5.1, 2026-11-21): multiblock 1.31 s (158 preview widgets 1.20 s); machine recipes 6.62 s for 67,004 `GTRecipeWidget`s: slots 3.89 s, of which `createUITemplate` 3.23 s (one per recipe; `createDefault()` rebuilds the type's default layout, or deserializes the type's custom UI NBT via `Resources.fromNBT` + `deserializeNBT`, every time), text 0.50 s, buttons 0.04 s, representative recipes 0.02 s, ~2.2 s elsewhere in widget/wrapper construction | 8.0 s | lazy widgets ruled out (JEI's `setRecipe` reads each widget's slots at start). Sharing the per-type template would need LDLib widget/texture cloning semantics: at most ~1.5-2 s, risky. Deprioritized |
| Just Enough Resources category registration (first creative-tab build) | 4.6 s | leave: the work happens once anyway |
| ElementalCraft pure-ore recipe scan after JEI (dpanvil `TagsUpdated`) | ~10 s | untouched, outside JEI start |
| Iris shader pipeline compile on spawn | ~4 s | user setting (shaders) |

## Async JEI start: feasibility (checked 2026-11-21; prototype in 0.7.0)

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
- Known hazards: font glyph-info caches (widgets measuring text), creative-tab rebuild races (main thread rebuilds when
  a creative inventory opens), plugins asserting the render thread (show up as caught plugin errors), mods reading
  player/level state. Opt-in only.

Async vs sync, full comparison (2026-11-21): sync baseline `logs/forgepreheat-jei-20261121-155047.txt` (0.7.2,
`asyncSearch` only: JEI start 53.0 s, 540,552 recipes, 623 categories, 0 plugin errors) vs async
`logs/forgepreheat-jei-20261121-152809.txt` (0.7.1: 576,796 recipes, 624 categories). 614 categories identical. Differ, all
tag-related: fuel categories (`minecraft:fuel` 1,112 -> 4,342, generatorgalore:solid_fuels 7,784 -> 30,394,
ironfurnaces:generator_regular and industrialforegoing:mycelial_furnace 979 -> 4,210, thermal:stirling_fuel 960 -> 4,170,
thermal:disenchantment_fuel 149 -> 297), anvil +186, grindstone -51 (only decrease, not yet explained per recipe),
jeresources:enchantment +1; `elementalcraft:purification` exists only in async (ElementalCraft builds it on TagsUpdated,
which a sync JEI start always precedes). So a normal JEI start in this pack shows incomplete fuel data and no
purification recipes.

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

## Where to pick up

Done: git repo at `github.com:rtgschepers/forgepreheat`, branch `main` (first commit 2026-11-21); the 0.3.1 grindstone
test (see version history). The user creates repos and makes commits themselves unless they ask otherwise. Commit
messages get no Claude attribution (no `Co-Authored-By: Claude` trailer, no "Generated with Claude Code" line), per the
user's global rules. Commit each installed version separately so changes can be reviewed and reverted. The JFR JVM
argument was removed from the instance on 2026-11-21.

2. ~~Install 0.4.2 and measure~~: done, saved 6.9 s of JEI start (see version history). Create categories checked.
   KubeJS hides checked too (see version history).
3. **Cache tidy-up**: `IngredientListCache` stored ~43,000 large lists that were each seen once. Store a list only on its
   second sighting (keep a cheap seen-once marker first).
4. **Defer JEI start** (investigated 2026-11-21, not started). JEI's `StartEventObserver` starts JEI synchronously on
   the render thread once it has seen both `ClientPlayerNetworkEvent.LoggingIn` and `RecipesUpdatedEvent` (the
   second only when JEI is on the server). That is why "Loading terrain" hangs. JEI 15 has no async start. Deferring
   on the render thread only moves the ~60 s freeze into the world, so it only helps if `JeiStarter.start` runs on a
   worker thread, which is a behavior change (opt-in). Risks: plugins touching render-thread-only state (textures,
   creative tabs, tooltips with a null level), races with the game, and JEI's runtime-available callbacks (overlay,
   other mods' JEI integrations) needing the render thread. A lower-risk alternative is making single pieces lazy:
   the search index (8.7 s, build on first search) and GregTech multiblock previews (8.9 s, build on first view).
5. **Anvil/grindstone, take two** (reopened 2026-11-21): generation is ~8.4 s, but not in menu sync. See the findings row.
   `FakeMenuSyncSkip` stays opt-in and broken for grindstone; remove it once the new approach lands.
6. GregTech: deprioritized, see the findings row.
6b. JEI plugin ranking from the log (0.5.1 run, total 52.6 s): `jei:minecraft` recipes 18.5 s, gtceu 8.0 s, JER categories 4.4 s, create 4.3 s, irons_spellbooks 3.1 s, `jei:minecraft` ingredients 2.8 s, `jei:internal` 1.6 s, industrialforegoing 1.5 s, forge_gui runtime 1.4 s. Per-category addRecipe explains only ~10 s of `jei:minecraft`, so ~8 s is recipe generation (candidates: brewing, anvil/grindstone makers, tag recipes); break it down with `StepTimings` next.
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
