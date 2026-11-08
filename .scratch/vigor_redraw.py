
from PIL import Image
N = 18
img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
px = img.load()

O = (58, 36, 16, 255)      # 描边
G = (246, 208, 106, 255)   # 头发亮
g = (224, 169, 58, 255)    # 头发主色
y = (170, 110, 36, 255)    # 头发暗部
S = (246, 217, 192, 255)   # 皮肤
E = (58, 36, 16, 255)      # 眼睛
M = (150, 62, 72, 255)     # 嘴
R = (178, 58, 72, 255)     # 衣领红
C = (245, 239, 228, 255)   # 衣领奶白
P = (232, 160, 168, 255)   # 耳内粉

def put(x, y_, c):
    if 0 <= x < N and 0 <= y_ < N:
        px[x, y_] = c

# 头：圆角方块，先描边再填色
for y_ in range(4, 15):
    for x in range(3, 15):
        if (x in (3, 14)) and (y_ in (4, 14)):
            continue
        put(x, y_, O)
for y_ in range(5, 14):
    for x in range(4, 14):
        put(x, y_, g)

# 耳朵：两个三角，耳廓描边 + 耳内粉
for i in range(5):
    row = 4 - i
    left = 3 + i
    right = 14 - i
    put(left, row, O); put(right, row, O)
    for x in range(left, right + 1):
        if row >= 0:
            put(x, row, O if x in (left, right) else (P if 2 <= i <= 3 and left + 1 < x < right - 1 else G))
# 耳内再补一层
for i in range(2, 4):
    row = 4 - i
    put(4 + i, row, P)
    put(13 - i, row, P)

# 刘海：亮部、暗部交替，做出层次
for x in range(5, 13):
    put(x, 5, G)
for x in range(4, 14):
    put(x, 6, G if x % 3 else g)
for x in range(4, 14):
    put(x, 7, g if x % 4 else G)
put(6, 7, y); put(11, 7, y)

# 脸
for y_ in range(8, 13):
    for x in range(5, 13):
        put(x, y_, S)

# 笑眼：向上弯的弧
for x, y_ in ((5, 9), (6, 8), (7, 9), (10, 9), (11, 8), (12, 9)):
    put(x, y_, E)
# 嘴：小笑
for x, y_ in ((7, 11), (8, 12), (9, 12), (10, 11)):
    put(x, y_, M)
# 腮红
put(4, 11, (240, 176, 168, 255)); put(13, 11, (240, 176, 168, 255))

# 衣领：红底 + 中间奶白 V
for y_ in range(13, 18):
    for x in range(4, 14):
        put(x, y_, R)
for y_, xs in ((14, (7, 8, 9, 10)), (15, (8, 9)), (16, (8, 9))):
    for x in xs:
        put(x, y_, C)
for x in range(3, 15):
    put(x, 17, O)
for y_ in range(13, 18):
    put(3, y_, O); put(14, y_, O)

img.save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor18_redraw.png")

# 与「原图直接缩」并排对比
from PIL import Image as I
shrink = I.open(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor18.png").convert("RGBA")
sheet = I.new("RGBA", (N*12*2 + 16, N*12), (34, 38, 48, 255))
sheet.alpha_composite(shrink.resize((N*12, N*12), I.NEAREST), (0, 0))
sheet.alpha_composite(img.resize((N*12, N*12), I.NEAREST), (N*12 + 16, 0))
sheet.convert("RGB").save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor_compare.png")
print("ok")
