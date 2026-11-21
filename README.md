# ForgePreheat

A client-side Forge 1.20.1 mod that gets large modpacks ready sooner. It is developed and tested in All the Mods 9 Expert.

In that pack, joining a server used to sit on "Loading terrain" for about 140 seconds while JEI built its recipe index on the main thread. With ForgePreheat you are in the world in under 15 seconds, and JEI finishes loading in the background about a minute later.

## What it changes

- JEI starts on a background thread after you join. Its GUI setup still runs on the main thread, which causes a short hitch of a second or two when JEI becomes available.
- While JEI loads, inventory screens show "JEI is loading" with the current step where JEI's item list will appear. From the second join on, it also shows an estimate of the time left, based on the previous load.
- JEI's start itself is faster: shared fuel lists, a recipe index built on separate threads, a search index built in the background, and anvil and grindstone recipes made in parallel.
- Slow loading code in other mods is sped up when they are installed: Create, KubeJS, ElementalCraft, Powah, UtilitiX, Just Enough Resources and Registrate. Each fix keeps the mod's own results and is skipped when the mod is missing or the code it patches has changed.
- Client startup to the title screen is about 4 seconds shorter (Forge object holder index, Registrate listener cleanup skipped).

JEI shows the same recipes as without the mod, with one difference: because JEI now starts after the server's tags have arrived, fuel categories include tag-based fuels (logs, planks and so on) that a normal JEI start misses.

## Requirements

- Minecraft 1.20.1, Forge 47.x
- JEI 15.59 or newer within JEI 15
- Client only; servers don't need it and players without it can still join

## Installing

Put the jar in your instance's `mods` folder. No configuration is needed.

## Switching things off

Every feature can be turned off with a JVM argument in your launcher, for example `-Dforgepreheat.asyncStart=false`. The most useful ones:

| Argument | Turns off |
|---|---|
| `-Dforgepreheat.asyncStart=false` | JEI starting in the background |
| `-Dforgepreheat.asyncSearch=false` | the background search index |
| `-Dforgepreheat.parallelRecipeIndex=false` | the recipe index threads |
| `-Dforgepreheat.lazyRenderOnlyIndex=false` | building JEI's render-only index after the start |
| `-Dforgepreheat.loadingNotice=false` | the "JEI is loading" text |

## Reporting problems

Send `logs/latest.log` and the newest `logs/forgepreheat-jei-*.txt` from your instance. The log lines starting with `[ForgePreheat]` show which features ran and what they did. Also say which pack you play and whether the problem goes away with `-Dforgepreheat.asyncStart=false`.

## License

MIT
