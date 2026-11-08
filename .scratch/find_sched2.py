
import io
p = r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\maid_javap.txt"
lines = io.open(p, encoding="utf-8", errors="replace").read().splitlines()
print("total", len(lines))
hits = [i for i, l in enumerate(lines) if "getScheduleDetail" in l]
print("hits", hits[:5])
for h in hits[:2]:
    print("----", h)
    print("\n".join(lines[h:h+30]))
