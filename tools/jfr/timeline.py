# Per-second view of what the main thread was doing. Usage: python -I timeline.py samples.txt HH:MM:SS HH:MM:SS
import re, sys
from collections import Counter, defaultdict

path, T0, T1 = sys.argv[1:4]
frame_re = re.compile(r"^\s+(\S+?)\(")
time_re = re.compile(r"startTime = (\d\d:\d\d:\d\d)")
# Ordered: first marker found anywhere in the stack names the phase.
MARKERS = [("sirttas.elementalcraft.pureore", "ElementalCraft pure-ore recipe gen"),
           ("sirttas.dpanvil", "ElementalCraft/dpanvil other"),
           ("HideJEIEventJS", "KubeJS JEI hide script"),
           ("kubejs.integration.forge.jei", "KubeJS JEI other"),
           ("net.irisshaders.iris.pipeline.PipelineManager", "Iris shader pipeline compile"),
           ("owmii.powah.lib.client.wiki", "Powah wiki recipe scan"),
           ("GrindstoneRecipeMaker", "JEI vanilla: grindstone"),
           ("AnvilRecipeMaker", "JEI vanilla: anvil"),
           ("VanillaPlugin.registerRecipes", "JEI vanilla: add recipes (smelting etc)"),
           ("com.gregtechceu", "GregTech"),
           ("com.simibubi.create.compat.jei", "Create JEI"),
           ("ironsspellbooks", "Iron's Spellbooks"),
           ("JeiStarter", "JEI other plugins/runtime"),
           ("cyclopscore", "Cyclops infobooks"),
           ("ClientboundLevelChunkPacketData", "chunk loading"),
           ("ClientPacketListener", "other packet handling"),
           ("GameRenderer", "rendering loading screen"),
          ]
sec = defaultdict(Counter)
cur, frames, t, thread, st = None, [], "", "", False
for line in open(path, encoding="utf-8", errors="replace"):
    if line.startswith("jdk.ExecutionSample"): frames, t, thread, cur = [], "", "", 1
    elif cur is None: continue
    elif "startTime" in line and not t: t = time_re.search(line).group(1)
    elif "sampledThread" in line: thread = line
    elif "stackTrace = [" in line: st = True
    elif st and line.strip() == "]": st = False
    elif st:
        m = frame_re.match(line)
        if m: frames.append(m.group(1))
    elif line.startswith("}"):
        cur = None
        if '"main"' in thread and T0 <= t <= T1:
            s = "\n".join(frames)
            sec[t][next((n for k, n in MARKERS if k in s), "other")] += 1
# Each second goes to its dominant phase; merge runs.
runs = []
for t in sorted(sec):
    top = sec[t].most_common(1)[0][0]
    if runs and runs[-1][2] == top: runs[-1][1] = t
    else: runs.append([t, t, top])
tot = Counter()
for a, b, n in runs:
    d = (int(b[3:5])*60+int(b[6:8])) - (int(a[3:5])*60+int(a[6:8])) + 1
    tot[n] += d
    print(f"{a}-{b} {d:4d}s  {n}")
print("\n== wall seconds per phase ==")
for n, d in tot.most_common(): print(f"{d:4d}s  {n}")
