
from PIL import Image
S = 16
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
px = img.load()

CREAM = (243, 239, 229, 255)
PINK  = (232, 201, 208, 255)
ROSE  = (204, 158, 168, 255)
DEEP  = (168, 122, 136, 255)
UNDER = (252, 247, 238, 255)
LINE  = (104, 80, 97, 255)

def put(x, y, c):
    if 0 <= x < S and 0 <= y < S:
        px[x, y] = c

# folded quilt: two layers, a turned-down top edge and one corner folded back
for y in range(3, 14):
    for x in range(1, 15):
        put(x, y, PINK if ((x // 4) + (y // 4)) % 2 else CREAM)

# fold crease: the sheet is doubled over, so the lower third is a separate layer
for x in range(1, 15):
    put(x, 10, DEEP)
    put(x, 11, ROSE)

# turned-down top edge showing the underside
for y in range(3, 6):
    for x in range(2, 14):
        put(x, y, UNDER)
for x in range(2, 14):
    put(x, 5, ROSE)

# one corner folded back
for y in range(9, 14):
    for x in range(9, 15):
        if (x - 9) + (14 - y) <= 5:
            put(x, y, UNDER)
for i in range(6):
    put(9 + i, 13 - i, ROSE)

# outline, one pixel, corners opened up
for x in range(1, 15):
    put(x, 2, LINE)
    put(x, 14, LINE)
for y in range(2, 15):
    put(0, y, LINE)
    put(15, y, LINE)
for (x, y) in ((0, 2), (15, 2), (0, 14), (15, 14), (1, 2), (14, 2)):
    put(x, y, (0, 0, 0, 0))
for (x, y) in ((0, 3), (15, 3), (1, 14), (14, 14)):
    put(x, y, LINE)

img.save(r"D:\Game\MineCraft\模组开发\睡个好觉\src\main\resources\assets\sleep_well\textures\gui\quilt_icon.png")
img.resize((S*10, S*10), Image.NEAREST).save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\quilt_icon_preview.png")
print("ok")
