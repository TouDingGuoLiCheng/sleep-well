
import io, re
p = r"D:\Game\MineCraft\.minecraft\versions\Better MC [FORGE] BMC4\logs\latest.log"
raw = open(p, "rb").read()
txt = raw.decode("gbk", errors="replace")
lines = txt.splitlines()
keep = []
for l in lines:
    if ("[被子]" in l or "[sleep_well]" in l) and ("wireframe" not in l):
        keep.append(l)
io.open(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\quilt_lines.txt", "w", encoding="utf-8").write("\n".join(keep))
print("kept", len(keep), "total", len(lines))
errs = [l for l in lines if ("Exception" in l or "Caused by" in l) ]
print("exception lines", len(errs))
