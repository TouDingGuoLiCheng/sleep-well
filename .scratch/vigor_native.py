
from PIL import Image
import numpy as np
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
rgba = np.dstack([native.astype(np.uint8), np.where(keep, 255, 0).astype(np.uint8)])
ys, xs = np.where(keep)
crop = rgba[ys.min():ys.max()+1, xs.min():xs.max()+1]
print("crop", crop.shape)
img = Image.fromarray(crop, "RGBA")
img.save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor_native.png")
img.resize((img.width*8, img.height*8), Image.NEAREST).save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor_native_preview.png")
print("ok", img.size)
