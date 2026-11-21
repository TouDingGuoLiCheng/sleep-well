
# -*- coding: utf-8 -*-
"""生成 4 张 64x64 无缝被面贴图：西瓜被 / 蛋糕被 / 绵羊被 / 猫咪被。"""
from PIL import Image, ImageDraw
import os, sys

W = H = 64
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                   "src", "main", "resources", "assets", "sleep_well", "textures", "quilt")
SRC = os.path.dirname(os.path.abspath(__file__))

def shade(c, d):
    return (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d)), max(0, min(255, c[2] + d)), 255)

def cloth(base, weave=5, stitch=14, dash=8, run=4):
    im = Image.new("RGBA", (W, H), base + (255,))
    d = ImageDraw.Draw(im)
    for y in range(H):
        for x in range(W):
            if (x + y) % 2 == 1:
                im.putpixel((x, y), shade(base, -weave))
    # 缝线：虚线格子，浅压一层，像被面的绗缝
    for y in range(H):
        if y % dash < run:
            im.putpixel((0, y), shade(base, -stitch))
            im.putpixel((W / 2 and W // 2, y), shade(base, -stitch))
    for x in range(W):
        if x % dash < run:
            im.putpixel((x, 0), shade(base, -stitch))
            im.putpixel((x, H // 2), shade(base, -stitch))
    return im

def circle(d, cx, cy, r, fill, outline=None):
    box = (cx - r, cy - r, cx + r - 1, cy + r - 1)
    if outline:
        d.ellipse((box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1), fill=outline)
    d.ellipse(box, fill=fill)

def px(d, x, y, c):
    d.point((x, y), fill=c)

def watermelon(im, cx, cy):
    d = ImageDraw.Draw(im)
    r = 12
    circle(d, cx, cy, r, (48, 110, 50, 255), (34, 78, 36, 255))        # 深绿瓜皮
    circle(d, cx, cy, r - 2, (128, 180, 94, 255))                       # 浅绿瓜皮
    circle(d, cx, cy, r - 3, (250, 244, 224, 255))                      # 白瓤
    circle(d, cx, cy, r - 4, (236, 62, 72, 255))                        # 红瓤
    d.arc((cx - r + 6, cy - r + 6, cx + r - 7, cy + r - 7), 200, 340, fill=(190, 40, 52, 255))
    for (sx, sy) in ((-4, -4), (3, -5), (5, 2), (-2, 4), (-6, 1)):
        d.rectangle((cx + sx, cy + sy, cx + sx + 1, cy + sy + 1), fill=(58, 38, 30, 255))
    # 高光
    px(d, cx - 7, cy - 7, (255, 150, 150, 255))

def cake(im, cx, cy):
    d = ImageDraw.Draw(im)
    # 海绵胚
    d.rounded_rectangle((cx - 11, cy - 4, cx + 11, cy + 9), radius=3,
                        fill=(238, 199, 126, 255), outline=(122, 85, 44, 255))
    d.line((cx - 10, cy + 4, cx + 10, cy + 4), fill=(206, 160, 92, 255))
    # 奶油层 + 粉色淋面
    d.rounded_rectangle((cx - 12, cy - 7, cx + 12, cy + 1), radius=3,
                        fill=(255, 248, 238, 255), outline=(122, 85, 44, 255))
    d.rounded_rectangle((cx - 11, cy - 10, cx + 11, cy - 4), radius=3,
                        fill=(247, 168, 192, 255), outline=(150, 78, 96, 255))
    for k in (-8, -3, 2, 7):                    # 淋面垂下的奶油
        d.ellipse((cx + k - 1, cy - 4, cx + k + 1, cy - 1), fill=(255, 248, 238, 255))
    # 草莓
    circle(d, cx + 5, cy - 13, 3, (222, 48, 58, 255), (128, 26, 34, 255))
    d.line((cx + 5, cy - 17, cx + 5, cy - 15), fill=(74, 132, 58, 255))
    px(d, cx + 4, cy - 14, (255, 160, 170, 255))

def sheep(im, cx, cy):
    import math
    d = ImageDraw.Draw(im)
    # 羊毛：横宽的一团卷毛，比脸大一圈，读起来才是绵羊不是花
    wool, edge = (247, 244, 233, 255), (176, 170, 156, 255)
    d.ellipse((cx - 13, cy - 10, cx + 13, cy + 9), fill=wool)
    for k in range(12):
        a = math.radians(k * 30 + 12)
        ox, oy = int(round(13 * math.cos(a))), int(round(8.5 * math.sin(a)))
        circle(d, cx + ox, cy + oy, 5, wool)
    d.ellipse((cx - 13, cy - 10, cx + 13, cy + 9), outline=edge)
    # 耳朵：先画，被脸压住一半
    d.ellipse((cx - 14, cy - 2, cx - 8, cy + 3), fill=(214, 160, 143, 255), outline=(150, 104, 90, 255))
    d.ellipse((cx + 8, cy - 2, cx + 14, cy + 3), fill=(214, 160, 143, 255), outline=(150, 104, 90, 255))
    # 脸
    d.ellipse((cx - 7, cy - 4, cx + 7, cy + 8), fill=(233, 189, 168, 255), outline=(162, 118, 100, 255))
    # 眼睛：眼白 + 黑瞳，才有表情
    d.rectangle((cx - 5, cy - 2, cx - 2, cy + 2), fill=(255, 252, 244, 255))
    d.rectangle((cx + 2, cy - 2, cx + 5, cy + 2), fill=(255, 252, 244, 255))
    d.rectangle((cx - 4, cy - 1, cx - 2, cy + 1), fill=(54, 48, 44, 255))
    d.rectangle((cx + 2, cy - 1, cx + 4, cy + 1), fill=(54, 48, 44, 255))
    # 鼻嘴
    d.rectangle((cx - 1, cy + 4, cx + 1, cy + 5), fill=(196, 140, 124, 255))
    d.line((cx, cy + 5, cx, cy + 6), fill=(150, 104, 90, 255))

def cat(im, cx, cy):
    d = ImageDraw.Draw(im)
    # 耳朵
    d.polygon([(cx - 10, cy - 3), (cx - 11, cy - 13), (cx - 2, cy - 7)],
              fill=(233, 164, 94, 255), outline=(138, 90, 47, 255))
    d.polygon([(cx + 10, cy - 3), (cx + 11, cy - 13), (cx + 2, cy - 7)],
              fill=(233, 164, 94, 255), outline=(138, 90, 47, 255))
    d.polygon([(cx - 8, cy - 5), (cx - 9, cy - 10), (cx - 4, cy - 7)], fill=(246, 176, 176, 255))
    d.polygon([(cx + 8, cy - 5), (cx + 9, cy - 10), (cx + 4, cy - 7)], fill=(246, 176, 176, 255))
    # 头
    circle(d, cx, cy, 10, (233, 164, 94, 255), (138, 90, 47, 255))
    # 额头花纹
    for k in (-4, 0, 4):
        d.line((cx + k, cy - 8, cx + k, cy - 5), fill=(200, 127, 62, 255))
    # 口鼻
    circle(d, cx, cy + 5, 5, (255, 242, 226, 255))
    d.rectangle((cx - 1, cy + 4, cx + 1, cy + 5), fill=(232, 140, 152, 255))
    d.line((cx, cy + 6, cx, cy + 7), fill=(160, 110, 84, 255))
    # 眼
    d.rectangle((cx - 5, cy - 1, cx - 3, cy + 1), fill=(74, 92, 58, 255))
    d.rectangle((cx + 3, cy - 1, cx + 5, cy + 1), fill=(74, 92, 58, 255))
    d.rectangle((cx - 5, cy, cx - 4, cy + 1), fill=(32, 30, 28, 255))
    d.rectangle((cx + 4, cy, cx + 5, cy + 1), fill=(32, 30, 28, 255))
    # 胡须
    for oy in (-1, 2):
        d.line((cx - 12, cy + 4 + oy, cx - 7, cy + 4 + oy), fill=(150, 110, 90, 255))
        d.line((cx + 7, cy + 4 + oy, cx + 12, cy + 4 + oy), fill=(150, 110, 90, 255))

QUILTS = [
    ("watermelon", (236, 242, 228), watermelon),
    ("cake",       (251, 236, 228), cake),
    ("sheep",      (239, 237, 244), sheep),
    ("cat",        (248, 238, 220), cat),
]

POS = [(16, 16), (48, 16), (16, 48), (48, 48)]
made = []
for name, base, fn in QUILTS:
    im = cloth(base)
    for (cx, cy) in POS:
        fn(im, cx, cy)
    path = os.path.normpath(os.path.join(OUT, name + ".png"))
    im.save(path)
    im.save(os.path.join(SRC, "被面-" + {"watermelon": "西瓜", "cake": "蛋糕",
                                         "sheep": "绵羊", "cat": "猫咪"}[name] + ".png"))
    made.append((name, im))

# 预览：每格把贴图平铺 2x2 再放大 3 倍，看接缝和密度
CELL = 64 * 2 * 3 // 2 * 2
sheet = Image.new("RGBA", (640, 640), (28, 32, 44, 255))
for i, (name, im) in enumerate(made):
    tile = Image.new("RGBA", (128, 128))
    for ty in range(2):
        for tx in range(2):
            tile.paste(im, (tx * 64, ty * 64))
    big = tile.resize((288, 288), Image.NEAREST)
    x = 16 + (i % 2) * 308
    y = 40 + (i // 2) * 300
    sheet.alpha_composite(big, (x, y))
    ImageDraw.Draw(sheet).text((x, y - 14), name, fill=(240, 240, 240, 255))
sheet.save(os.path.join(SRC, "被子预览.png"))
print("ok", [n for n, _ in made])
