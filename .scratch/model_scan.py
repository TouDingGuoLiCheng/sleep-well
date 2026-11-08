
import io
p = r"D:\Game\MineCraft\.minecraft\versions\Better MC [FORGE] BMC4\logs\latest.log"
txt = open(p, "rb").read().decode("gbk", errors="replace")
lines = txt.splitlines()
keys = ("kaenbyou", "BedrockModel", "little_maid_mob", "Bedrock", "model loading", "ModelManger", "ModelManager", "已挂载 gecko")
for l in lines:
    if any(k in l for k in keys) and "silhouette" not in l:
        print(l[:200])
