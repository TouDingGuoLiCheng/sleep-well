
from PIL import Image
import numpy as np, collections
src = Image.open(r"D:\Pic\icon1.png").convert("RGBA")
native = np.asarray(src.resize((64, 64), Image.NEAREST))
n = 20
f = 64 // n  # 3 with one row of slack; sample the first 60 px
a = native[:n*f, :n*f]
out = np.zeros((n, n, 4), np.uint8)
for y in range(n):
    for x in range(n):
        block = a[y*f:(y+1)*f, x*f:(x+1)*f].reshape(-1, 4)
        opaque = block[block[:, 3] > 0]
        if len(opaque) < block.shape[0] * 0.5:
            continue
        out[y, x] = collections.Counter(map(tuple, opaque)).most_common(1)[0][0]

# fill pinholes: a transparent pixel with three or more opaque neighbours takes the majority of them
for _ in range(2):
    fix = out.copy()
    for y in range(n):
        for x in range(n):
            if out[y, x, 3] != 0:
                continue
            nb = []
            for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
                yy, xx = y+dy, x+dx
                if 0 <= yy < n and 0 <= xx < n and out[yy, xx, 3] > 0:
                    nb.append(tuple(out[yy, xx]))
            if len(nb) >= 3:
                fix[y, x] = collections.Counter(nb).most_common(1)[0][0]
    out = fix

# binarise alpha
out[:, :, 3] = np.where(out[:, :, 3] > 0, 255, 0)
img = Image.fromarray(out, "RGBA")
img.save(r"D:\Game\MineCraft\模组开发\睡个好觉\src\main\resources\assets\sleep_well\textures\gui\quilt_icon.png")
img.resize((n*12, n*12), Image.NEAREST).save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\quilt_icon_preview.png")
print("colours", len(np.unique(out.reshape(-1, 4), axis=0)), "opaque", int((out[:,:,3]>0).sum()))
