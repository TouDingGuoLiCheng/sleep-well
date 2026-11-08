
import json, io, os, shutil, datetime
cfg = r"C:\Users\23777\.dsh\profiles\desktop\cordis.patch.yml"
bak = cfg + ".bak-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
shutil.copyfile(cfg, bak)

mcp = json.load(open(r"C:\Users\23777\.cursor\mcp.json", encoding="utf-8"))
args = mcp["mcpServers"]["pixellab"]["args"]
token = None
for a in args:
    if a.startswith("Authorization:"):
        token = a.split("Bearer", 1)[1].strip()
print("token found:", bool(token), "len", len(token) if token else 0)

text = open(cfg, encoding="utf-8").read()
if "mcp-pixellab" in text:
    print("already configured")
else:
    block = f"""
# PixelLab MCP（出像素图用）。token 与 ~/.cursor/mcp.json 里那份相同，属于本机配置；
# 想换 key 时改这一行即可，别把它贴进会话。
- id: mcp-pixellab
  name: '@deepseek-ai/dsh-mcp-client'
  config:
    serverName: pixellab
    transport: streamable-http
    url: https://api.pixellab.ai/mcp
    headers:
      Authorization: 'Bearer {token}'
    toolCallTimeoutMs: 180000
"""
    open(cfg, "w", encoding="utf-8", newline="\n").write(text.rstrip() + "\n" + block)
    print("appended")

try:
    import yaml
    data = yaml.safe_load(open(cfg, encoding="utf-8").read())
    ids = [d.get("id") for d in data if isinstance(d, dict)]
    print("yaml ok, entries:", len(ids), "| pixellab:", "mcp-pixellab" in ids)
except Exception as e:
    print("yaml check skipped:", type(e).__name__, str(e)[:120])
print("backup:", os.path.basename(bak))
