
import io
p = r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\maid_javap.txt"
lines = io.open(p, encoding="utf-8", errors="replace").read().splitlines()
start = None
for i, l in enumerate(lines):
    if "Activity getScheduleDetail()" in l:
        start = i
        break
print("found at", start)
if start is not None:
    print("\n".join(lines[start:start+40]))
