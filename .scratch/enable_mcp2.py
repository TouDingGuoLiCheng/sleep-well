
import shutil, datetime, re
cfg = r"C:\Users\23777\.dsh\profiles\desktop\cordis.patch.yml"
shutil.copyfile(cfg, cfg + ".bak-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
lines = open(cfg, encoding="utf-8").read().split("\n")
out, changed = [], 0
inside = False
for line in lines:
    if line.startswith("#- id: mcp-pixellab") or line.startswith("# - id: mcp-pixellab"):
        inside = True
    if inside and line.startswith("#"):
        out.append(line[1:])
        changed += 1
        if "toolCallTimeoutMs" in line:
            inside = False
        continue
    out.append(line)
open(cfg, "w", encoding="utf-8", newline="\n").write("\n".join(out))
text = open(cfg, encoding="utf-8").read()
print("stripped:", changed)
print("active:", "\n- id: mcp-pixellab" in text)
tail = re.sub(r"Bearer [A-Za-z0-9_\-]+", "Bearer <REDACTED>", "\n".join(out[-11:]))
print(tail)
