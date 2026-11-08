
import io, re
p = r"D:\Game\MineCraft\.minecraft\versions\Better MC [FORGE] BMC4\logs\latest.log"
lines = io.open(p, encoding="utf-8", errors="replace").read().splitlines()
hits = [(i+1, l) for i, l in enumerate(lines) if ("被子" in l) or ("已挂载" in l) or ("线框" in l) or ("采集" in l) or ("校验" in l)]
out = "\n".join(f"{n}: {t}" for n, t in hits[:40])
io.open(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\cn_hits.txt", "w", encoding="utf-8").write(out or "(no hits)")
print("hits", len(hits))
