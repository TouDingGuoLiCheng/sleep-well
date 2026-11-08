
from PIL import Image
import numpy as np
img = Image.open(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor18_crop.png").convert("RGBA")
a = np.asarray(img).copy()
N = 18

# skin: the light warm colour that fills the lower middle of the head
inner = a[8:16, 4:14, :3].reshape(-1, 3)
cols, counts = np.unique(inner, axis=0, return_counts=True)
skin = cols[np.argmax(counts)]
print("skin", skin)
skin_mask = (np.abs(a[:, :, :3].astype(int) - skin.astype(int)).sum(axis=2) < 20) & (a[:, :, 3] > 0)
ys, xs = np.where(skin_mask)
y0, y1, x0, x1 = ys.min(), ys.max()+1, xs.min(), xs.max()+1
print("face box", x0, y0, x1, y1)

skin_px = tuple(int(v) for v in skin) + (255,)
eye = (58, 36, 16, 255)
mouth = (150, 62, 72, 255)
blush = (238, 168, 162, 255)

# wipe whatever dark marks the resize left inside the face
for y in range(y0, y1):
    for x in range(x0, x1):
        if a[y, x, 3] > 0 and int(a[y, x, :3].sum()) < 300:
            a[y, x] = skin_px

def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        a[y, x] = c

w = x1 - x0
h = y1 - y0
ey = y0 + max(1, int(round(h * 0.40)))
cx1 = x0 + max(1, int(round(w * 0.26)))
cx2 = x0 + max(1, int(round(w * 0.74)))
for cx in (cx1, cx2):
    put(cx - 1, ey + 1, eye)
    put(cx, ey, eye)
    put(cx + 1, ey + 1, eye)
my = ey + max(2, int(round(h * 0.30)))
mid = (cx1 + cx2) // 2
put(mid - 1, my, mouth)
put(mid, my + 1, mouth)
put(mid + 1, my, mouth)
# blush on the cheeks
by = my
put(x0, by, blush)
put(x1 - 1, by, blush)

out = Image.fromarray(a, "RGBA")
out.save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor18_retouch.png")
sheet = Image.new("RGBA", (N*14*2 + 16, N*14), (34, 38, 48, 255))
sheet.alpha_composite(Image.open(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor18_crop.png").convert("RGBA").resize((N*14, N*14), Image.NEAREST), (0, 0))
sheet.alpha_composite(out.resize((N*14, N*14), Image.NEAREST), (N*14 + 16, 0))
sheet.convert("RGB").save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor_retouch_compare.png")
print("ok")
