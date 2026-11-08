
import io, glob, os
paths = [
 r"D:\Game\MineCraft\.minecraft\versions\1.20.1-Forge\logs\latest.log",
 r"D:\Game\MineCraft\.minecraft\logs\latest.log",
]
for p in paths:
    if not os.path.exists(p):
        print("missing", p); continue
    raw = open(p, "rb").read()
    txt = raw.decode("gbk", errors="replace")
    print("=" * 20, p, "size", len(raw), "modified", __import__("datetime").datetime.fromtimestamp(os.path.getmtime(p)))
    lines = txt.splitlines()
    for l in lines:
        if "layer boxes=" in l or "碰撞盒偏少" in l or "模型采集" in l or "silhouette maid" in l:
            print(l[:170])
