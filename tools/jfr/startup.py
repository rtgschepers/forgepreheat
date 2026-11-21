# Startup view: per time window, which threads were busy and in which mod's code. Usage:
#   python -I startup.py samples.txt [window_seconds] [HH:MM:SS HH:MM:SS]
# A sample is credited to the innermost frame outside Java, Minecraft, Forge and framework packages (the mod doing the work).
import re, sys
from collections import Counter, defaultdict

path = sys.argv[1]
window = int(sys.argv[2]) if len(sys.argv) > 2 else 5
t0, t1 = (sys.argv[3], sys.argv[4]) if len(sys.argv) > 4 else ("00:00:00", "23:59:59")
frame_re = re.compile(r"^\s+(\S+?)\(")
time_re = re.compile(r"startTime = (\d\d):(\d\d):(\d\d)")
FRAMEWORK = ("java.", "jdk.", "sun.", "com.sun.", "net.minecraft.", "net.minecraftforge.", "cpw.mods.", "org.spongepowered.",
             "com.google.", "it.unimi.", "org.lwjgl.", "com.mojang.", "org.objectweb.", "io.netty.", "org.apache.",
             "com.electronwill.", "net.jodah.", "org.slf4j.", "kotlin.", "org.openjdk.", "MC-BOOTSTRAP")

def owner(frames):
    for f in frames:  # innermost first
        if not f.startswith(FRAMEWORK):
            parts = f.split(".")
            return ".".join(parts[:3]) if len(parts) > 3 else f
    # All framework: name the innermost Minecraft/Forge area instead.
    for f in frames:
        if f.startswith(("net.minecraft.", "net.minecraftforge.", "cpw.mods.", "org.spongepowered.")):
            return "[" + ".".join(f.split(".")[:4]) + "]"
    return "[jdk]"

def group(thread):
    name = re.sub(r"[-#]?\d+$", "", thread)
    return name

buckets = defaultdict(Counter)   # window start -> Counter((thread group, owner))
threads = defaultdict(Counter)   # window start -> Counter(thread group)
cur, frames, in_stack = None, [], False
for line in open(path, encoding="utf-8", errors="replace"):
    if line.startswith("jdk.ExecutionSample"):
        cur = {"t": None, "th": ""}; frames = []; continue
    if cur is None:
        continue
    if cur["t"] is None:
        m = time_re.search(line)
        if m:
            h, mi, s = map(int, m.groups()); cur["t"] = h * 3600 + mi * 60 + s; continue
    if "sampledThread" in line:
        m = re.search(r'"([^"]*)"', line); cur["th"] = m.group(1) if m else "?"; continue
    if "stackTrace = [" in line:
        in_stack = True; continue
    if in_stack and line.strip() == "]":
        in_stack = False; continue
    if in_stack:
        m = frame_re.match(line)
        if m: frames.append(m.group(1))
        continue
    if line.startswith("}") and cur["t"] is not None:
        hms = "%02d:%02d:%02d" % (cur["t"] // 3600, cur["t"] // 60 % 60, cur["t"] % 60)
        if t0 <= hms <= t1:
            w = cur["t"] - cur["t"] % window
            g = group(cur["th"])
            buckets[w][(g, owner(frames))] += 1
            threads[w][g] += 1
        cur = None

for w in sorted(buckets):
    hms = "%02d:%02d:%02d" % (w // 3600, w // 60 % 60, w % 60)
    total = sum(threads[w].values())
    ths = ", ".join(f"{g} {n}" for g, n in threads[w].most_common(4))
    print(f"{hms}  {total:4} samples  [{ths}]")
    for (g, o), n in buckets[w].most_common(4):
        print(f"          {n:4}  {g:28} {o}")
