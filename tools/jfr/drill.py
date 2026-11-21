# Top-down paths and hottest leaves of the main thread in a window. Usage: python -I drill.py samples.txt HH:MM:SS HH:MM:SS
import re, sys
from collections import Counter
path, t0, t1 = sys.argv[1:4]
fr_re = re.compile(r"^\s+(\S+?)\(.*?\)(?:\s+line:\s*(\d+))?"); tr = re.compile(r"startTime = (\d\d:\d\d:\d\d)")
SKIP = ("java.", "jdk.", "sun.", "com.google.", "it.unimi.")
GEN = SKIP + ("net.minecraftforge.eventbus", "cpw.", "org.spongepowered.", "mezz.jei.library.load.", "mezz.jei.library.startup.",
              "mezz.jei.forge.startup.", "mezz.jei.common.async.", "mezz.jei.forge.events.", "net.minecraft.client.Minecraft",
              "net.minecraft.client.multiplayer.", "net.minecraft.network.", "net.minecraftforge.client.", "net.minecraft.util.thread.")
mid, leaf, n = Counter(), Counter(), 0
cur, fr, t, th, st = None, [], "", "", False
for line in open(path, encoding="utf-8", errors="replace"):
    if line.startswith("jdk.ExecutionSample"): fr, t, th, cur = [], "", "", 1
    elif cur is None: continue
    elif "startTime" in line and not t: t = tr.search(line).group(1)
    elif "sampledThread" in line: th = line
    elif "stackTrace = [" in line: st = True
    elif st and line.strip() == "]": st = False
    elif st:
        m = fr_re.match(line)
        if m: fr.append(m.group(1) + (":" + m.group(2) if m.group(2) else ""))
    elif line.startswith("}"):
        cur = None
        if '"main"' not in th or not (t0 <= t <= t1): continue
        n += 1
        top = fr[::-1]
        i = next((k for k, x in enumerate(top) if not x.startswith(GEN)), len(top) - 1)
        path_ = [x for x in top[i:] if not x.startswith(SKIP)]
        mid[" > ".join(path_[:5])] += 1
        lf = [x for x in fr if not x.startswith(SKIP)][:3]
        leaf[" < ".join(lf)] += 1
print(f"main samples {t0}-{t1}: {n}")
print("-- top-down paths (first 5 non-dispatch frames)")
for k, v in mid.most_common(7): print(f"{100*v/max(n,1):5.1f}%  {k}")
print("-- hottest leaves (leaf < caller < caller)")
for k, v in leaf.most_common(6): print(f"{100*v/max(n,1):5.1f}%  {k}")
