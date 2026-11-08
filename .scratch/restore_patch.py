
import shutil, os, re
cfg = r"C:\Users\23777\.dsh\profiles\desktop\cordis.patch.yml"
bak = r"C:\Users\23777\.dsh\profiles\desktop\cordis.patch.yml.bak-20261108-152525"
shutil.copyfile(bak, cfg)
text = open(cfg, encoding="utf-8").read()
print("active entry present:", "\n- id: mcp-pixellab" in text)
print("commented block present:", "# - id: mcp-pixellab" in text)
print("size", os.path.getsize(cfg))
