# Entry points and hot leaves for one class/method. Usage: python -I who.py samples.txt HH:MM:SS HH:MM:SS <frame substring>
import re, sys
from collections import Counter

path, t0, t1, target = sys.argv[1:5]
frame_re = re.compile(r"^\s+(\S+?)\(.*?\)(?:\s+line:\s*(\d+))?")
time_re = re.compile(r"startTime = (\d\d:\d\d:\d\d)")

events, cur, in_stack = [], None, False
with open(path, encoding="utf-8", errors="replace") as f:
    for line in f:
        if line.startswith("jdk.ExecutionSample"):
            cur = {"t": "", "thread": "", "frames": []}
        elif cur is None:
            continue
        elif "startTime" in line and not cur["t"]:
            m = time_re.search(line); cur["t"] = m.group(1) if m else ""
        elif "sampledThread" in line:
            cur["thread"] = line.split("=", 1)[1].strip()
        elif "stackTrace = [" in line:
            in_stack = True
        elif in_stack and line.strip() == "]":
            in_stack = False
        elif in_stack:
            m = frame_re.match(line)
            if m:
                cur["frames"].append(m.group(1) + (":" + m.group(2) if m.group(2) else ""))
        elif line.startswith("}"):
            events.append(cur); cur = None

hits = [e for e in events if e["thread"].startswith('"main"') and t0 <= e["t"] <= t1 and any(target in x for x in e["frames"])]
secs = Counter(e["t"] for e in hits)
print(f"{target}: {len(hits)} samples, from {min(secs)} to {max(secs)}")
# Outermost target frame, its callers (context) and the leaf, to see who triggers it and where it burns.
ctx, leaf = Counter(), Counter()
for e in hits:
    fr = e["frames"]
    i = max(k for k, x in enumerate(fr) if target in x)
    callers = [x for x in fr[i+1:i+8] if not x.startswith(("java.", "jdk."))][:4]
    ctx[" < ".join([fr[i]] + callers)] += 1
    inner = [x for x in fr[:i] if not x.startswith(("java.", "jdk.", "com.google", "it.unimi"))]
    leaf[" > ".join(inner[::-1][-4:]) if inner else fr[0]] += 1
print("-- entry points (outermost frame < callers)")
for k, v in ctx.most_common(4): print(f"{v:5d}  {k}")
print("-- where time goes (last 4 non-JDK frames)")
for k, v in leaf.most_common(6): print(f"{v:5d}  {k}")
