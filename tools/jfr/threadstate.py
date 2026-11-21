# What one thread spent its wall time on, per window: running Java, in native code, parked, sleeping, monitor waits,
# plus whole-VM GC pauses. Usage:
#   python -I threadstate.py samples.txt waits.txt [thread] [window_s] [HH:MM:SS HH:MM:SS]
# samples.txt: jfr print --events jdk.ExecutionSample; waits.txt: jfr print --events jdk.ThreadPark,jdk.JavaMonitorWait,
# jdk.JavaMonitorEnter,jdk.ThreadSleep,jdk.NativeMethodSample,jdk.GarbageCollection --stack-depth 6.
# Java/native times are sample counts x the profile settings' intervals (10 ms Java, 20 ms native), so they are estimates;
# park/sleep/wait times are measured, but JFR only records waits longer than its threshold (20 ms in "profile").
import re, sys
from collections import defaultdict, Counter

samples, waits = sys.argv[1], sys.argv[2]
thread = sys.argv[3] if len(sys.argv) > 3 else "main"
window = int(sys.argv[4]) if len(sys.argv) > 4 else 5
t0, t1 = (sys.argv[5], sys.argv[6]) if len(sys.argv) > 6 else ("00:00:00", "23:59:59")
JAVA_MS, NATIVE_MS = 10, 20
time_re = re.compile(r"startTime = (\d\d):(\d\d):(\d\d)\.(\d+)")
dur_re = re.compile(r"(duration|sumOfPauses) = ([\d.,]+) (ms|s|us)")
frame_re = re.compile(r"^\s+(\S+?)\(")

def secs(m):
    h, mi, s, frac = m.groups()
    return int(h) * 3600 + int(mi) * 60 + int(s) + float("0." + frac)

def ms(value, unit):
    v = float(value.replace(",", "."))
    return v * 1000 if unit == "s" else v / 1000 if unit == "us" else v

def events(path):
    cur = None
    for line in open(path, encoding="utf-8", errors="replace"):
        if re.match(r"^jdk\.\w+ \{", line):
            cur = {"type": line.split()[0], "t": None, "th": "", "dur": 0.0, "frames": []}
            continue
        if cur is None:
            continue
        if cur["t"] is None and (m := time_re.search(line)):
            cur["t"] = secs(m); continue
        if (m := dur_re.search(line)) and (m.group(1) == "sumOfPauses" or cur["type"] != "jdk.GarbageCollection"):
            cur["dur"] = ms(m.group(2), m.group(3)); continue
        if ("sampledThread" in line or "eventThread" in line) and not cur["th"]:
            m = re.search(r'"([^"]*)"', line); cur["th"] = m.group(1) if m else ""; continue
        if (m := frame_re.match(line)):
            cur["frames"].append(m.group(1)); continue
        if line.startswith("}"):
            if cur["t"] is not None:
                yield cur
            cur = None

def hms(t):
    t = int(t); return "%02d:%02d:%02d" % (t // 3600, t // 60 % 60, t % 60)

acc = defaultdict(Counter)
native_top = defaultdict(Counter)
for e in list(events(samples)) + list(events(waits)):
    if not (t0 <= hms(e["t"]) <= t1):
        continue
    w = int(e["t"]) - int(e["t"]) % window
    if e["type"] == "jdk.GarbageCollection":
        acc[w]["gc"] += e["dur"]; continue
    if e["th"] != thread:
        continue
    kind = {"jdk.ExecutionSample": "java", "jdk.NativeMethodSample": "native", "jdk.ThreadPark": "park",
            "jdk.ThreadSleep": "sleep", "jdk.JavaMonitorWait": "wait", "jdk.JavaMonitorEnter": "lock"}.get(e["type"])
    if kind == "java":
        acc[w]["java"] += JAVA_MS
    elif kind == "native":
        acc[w]["native"] += NATIVE_MS
        frame = next((f for f in e["frames"] if not f.startswith(("java.", "jdk.", "sun."))), e["frames"][0] if e["frames"] else "?")
        native_top[w][frame] += 1
    elif kind:
        acc[w][kind] += e["dur"]

total = Counter()
print(f"{'window':8} {'java':>6} {'native':>6} {'park':>6} {'sleep':>6} {'wait':>6} {'lock':>6} {'gc':>6} {'sum':>6}   (seconds; top native frame)")
for w in sorted(acc):
    c = acc[w]
    total.update(c)
    keys = ["java", "native", "park", "sleep", "wait", "lock", "gc"]
    row = " ".join(f"{c[k] / 1000:6.1f}" for k in keys)
    top = native_top[w].most_common(1)
    print(f"{hms(w):8} {row} {sum(c[k] for k in keys if k != 'gc') / 1000:6.1f}   {top[0][0] if top else ''}")
print("total    " + " ".join(f"{total[k] / 1000:6.1f}" for k in ["java", "native", "park", "sleep", "wait", "lock", "gc"]))
