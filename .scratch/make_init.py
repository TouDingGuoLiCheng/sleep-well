
from PIL import Image
src = Image.open(r"D:\Game\MineCraft\模组开发\Tool\WineFox\winefox.png").convert("RGBA")
print("ref", src.size)
# 610x905 -> 43x64, centred on a 64x64 transparent canvas
h = 64
w = round(src.width * h / src.height)
small = src.resize((w, h), Image.NEAREST)
canvas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
canvas.alpha_composite(small, ((64 - w) // 2, 0))
canvas.save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\drowsy_init_64.png")
print("init", small.size, "->", canvas.size)
