
from PIL import Image, ImageDraw
import numpy as np, collections

src = Image.open(r"D:\Pic\干劲.png").convert("RGB")
native = np.asarray(src.resize((64, 64), Image.NEAREST)).astype(np.int16)

# key out the flat backdrop: flood fill from the border, tolerance so the art's own outline survives
h, w, _ = native.shape
bg = native[0, 0].copy()
print("backdrop", tuple(int(v) for v in bg))
dist = np.abs(native - bg).sum(axis=2)
mask = dist <= 24
# only the region connected to the border counts as background
from collections import deque
seen = np.zeros((h, w), bool)
q = deque()
for x in range(w):
    for y in (0, h-1):
        if mask[y, x] and not seen[y, x]:
            seen[y, x] = True; q.append((y, x))
for y in range(h):
    for x in (0, w-1):
        if mask[y, x] and not seen[y, x]:
            seen[y, x] = True; q.append((y, x))
while q:
    y, x = q.popleft()
    for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
        yy, xx = y+dy, x+dx
        if 0 <= yy < h and 0 <= xx < w and mask[yy, xx] and not seen[yy, xx]:
            seen[yy, xx] = True; q.append((yy, xx))
alpha = np.where(seen, 0, 255).astype(np.uint8)
rgba = np.dstack([native.astype(np.uint8), alpha])
print("kept", round(float((alpha>0).mean()),3))

N = 18
out = np.zeros((N, N, 4), np.uint8)
for oy in range(N):
    y0, y1 = int(round(oy*h/N)), max(int(round(oy*h/N))+1, int(round((oy+1)*h/N)))
    for ox in range(N):
        x0, x1 = int(round(ox*w/N)), max(int(round(ox*w/N))+1, int(round((ox+1)*w/N)))
        block = rgba[y0:y1, x0:x1].reshape(-1, 4)
        opaque = block[block[:, 3] > 0]
        if len(opaque) < block.shape[0] * 0.55:
            continue
        out[oy, ox] = collections.Counter(map(tuple, opaque)).most_common(1)[0][0]

# pinhole fill
for _ in range(2):
    fix = out.copy()
    for y in range(N):
        for x in range(N):
            if out[y, x, 3] != 0:
                continue
            nb = [tuple(out[y+dy, x+dx]) for dy, dx in ((1,0),(-1,0),(0,1),(0,-1))
                  if 0 <= y+dy < N and 0 <= x+dx < N and out[y+dy, x+dx, 3] > 0]
            if len(nb) >= 3:
                fix[y, x] = collections.Counter(nb).most_common(1)[0][0]
    out = fix
out[:, :, 3] = np.where(out[:, :, 3] > 0, 255, 0)

img = Image.fromarray(out, "RGBA")
img.save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor18.png")

# preview: the icon at 12x on both a dark plate and a light plate
dark = Image.new("RGBA", (N*12, N*12), (30, 34, 44, 255))
light = Image.new("RGBA", (N*12, N*12), (226, 226, 226, 255))
big = img.resize((N*12, N*12), Image.NEAREST)
sheet = Image.new("RGBA", (N*12*2 + 16, N*12), (40, 40, 44, 255))
for i, bgimg in enumerate((dark, light)):
    bgimg.alpha_composite(big, (0, 0))
    sheet.alpha_composite(bgimg, (i*(N*12+16), 0))
sheet.convert("RGB").save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor_preview.png")
print("colours", len(np.unique(out.reshape(-1,4), axis=0)))
