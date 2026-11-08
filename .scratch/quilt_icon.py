
from PIL import Image
import os
S = 16
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
px = img.load()

CREAM = (242, 238, 228, 255)
PINK  = (232, 201, 208, 255)
ROSE  = (206, 160, 170, 255)
UNDER = (251, 246, 236, 255)
LINE  = (110, 85, 102, 255)
SHAD  = (176, 138, 154, 255)

def put(x, y, c):
    if 0 <= x < S and 0 <= y < S:
        px[x, y] = c

# body: a gingham square from (1,4) to (14,13)
for y in range(4, 14):
    for x in range(1, 15):
        check = ((x // 3) + (y // 3)) % 2
        put(x, y, PINK if check else CREAM)

# turned-down top band, the part that reads as "a bed being made"
for y in range(4, 7):
    for x in range(2, 14):
        put(x, y, UNDER)
for x in range(2, 14):
    put(x, 6, ROSE)

# folded corner at the bottom right: underside triangle + crease
for y in range(9, 14):
    for x in range(9, 15):
        if (x - 9) + (14 - y) <= 5:
            put(x, y, UNDER)
for i in range(6):
    put(9 + i, 13 - i, SHAD)

# stitches along the top band and the left hem
for x in range(3, 13, 2):
    put(x, 5, ROSE)
for y in range(8, 13, 2):
    put(1, y, ROSE)

# outline
for x in range(1, 15):
    put(x, 3, LINE)
    put(x, 14, LINE)
for y in range(3, 15):
    put(0, y, LINE)
    put(15, y, LINE)
# soften the four corners
for (x, y) in ((0, 3), (15, 3), (0, 14), (15, 14)):
    put(x, y, (0, 0, 0, 0))

out = r"D:\Game\MineCraft\模组开发\睡个好觉\src\main\resources\assets\sleep_well\textures\gui\quilt_icon.png"
img.save(out)
img.resize((S*8, S*8), Image.NEAREST).save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\quilt_icon_preview.png")
print("ok")
