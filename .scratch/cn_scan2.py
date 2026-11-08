
import io
p = r"D:\Game\MineCraft\.minecraft\versions\Better MC [FORGE] BMC4\logs\latest.log"
raw = open(p, "rb").read()
for enc in ("utf-8", "gbk"):
    try:
        txt = raw.decode(enc)
        n = txt.count("被子")
        print(enc, "部署命中:", n, "| 替换符:", txt.count("\ufffd"))
    except Exception as e:
        print(enc, "fail", e)
txt = raw.decode("gbk", errors="replace")
hits = [l for l in txt.splitlines() if "被子" in l or "校验" in l or "采集" in l]
out = "\n".join(hits[:30])
io.open(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\cn_hits2.txt", "w", encoding="utf-8").write(out or "(none)")
print("gbk hits", len(hits))
