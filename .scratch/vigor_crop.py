
from PIL import Image
import numpy as np, collections
from collections import deque

src = Image.open(r"D:\Pic\干劲.png").convert("RGB")
native = np.asarray(src.resize((64, 64), Image.NEAREST)).astype(np.int16)
h, w, _ = native.shape
bg = native[0, 0]
mask = np.abs(native - bg).sum(axis=2) <= 24
seen = np.zeros((h, w), bool); q = deque()
for x in range(w):
    for y_ in (0, h-1):
        if mask[y_, x] and not seen[y_, x]: seen[y_, x] = True; q.append((y_, x))
for y_ in range(h):
    for x in (0, w-1):
        if mask[y_, x] and not seen[y_, x]: seen[y_, x] = True; q.append((y_, x))
while q:
    y_, x = q.popleft()
    for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
        yy, xx = y_+dy, x+dx
        if 0 <= yy < h and 0 <= xx < w and mask[yy, xx] and not seen[yy, xx]:
            seen[yy, xx] = True; q.append((yy, xx))
keep = ~seen
ys, xs = np.where(keep)
top, bottom, left, right = ys.min(), ys.max()+1, xs.min(), xs.max()+1
print("bbox", left, top, right, bottom, "content", right-left, "x", bottom-top)
rgba = np.dstack([native.astype(np.uint8), np.where(keep, 255, 0).astype(np.uint8)])

def down(crop, n):
    ch, cw, _ = crop.shape
    out = np.zeros((n, n, 4), np.uint8)
    for oy in range(n):
        y0 = int(round(oy*ch/n)); y1 = max(y0+1, int(round((oy+1)*ch/n)))
        for ox in range(n):
            x0 = int(round(ox*cw/n)); x1 = max(x0+1, int(round((ox+1)*cw/n)))
            block = crop[y0:y1, x0:x1].reshape(-1, 4)
            opaque = block[block[:, 3] > 0]
            if len(opaque) < block.shape[0] * 0.5: continue
            out[oy, ox] = collections.Counter(map(tuple, opaque)).most_common(1)[0][0]
    for _ in range(2):
        fix = out.copy()
        for y_ in range(n):
            for x in range(n):
                if out[y_, x, 3] != 0: continue
                nb = [tuple(out[y_+dy, x+dx]) for dy, dx in ((1,0),(-1,0),(0,1),(0,-1))
                      if 0 <= y_+dy < n and 0 <= x+dx < n and out[y_+dy, x+dx, 3] > 0]
                if len(nb) >= 3: fix[y_, x] = collections.Counter(nb).most_common(1)[0][0]
        out = fix
    out[:, :, 3] = np.where(out[:, :, 3] > 0, 255, 0)
    return Image.fromarray(out, "RGBA")

crop = rgba[top:bottom, left:right]
v = down(crop, 18)
v.save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor18_crop.png")
sheet = Image.new("RGBA", (18*12, 18*12), (34, 38, 48, 255))
sheet.alpha_composite(v.resize((18*12, 18*12), Image.NEAREST), (0, 0))
sheet.convert("RGB").save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor_crop_preview.png")
print("ok")
