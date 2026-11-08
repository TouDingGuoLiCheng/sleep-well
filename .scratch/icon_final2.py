
from PIL import Image
import numpy as np, collections
src = Image.open(r"D:\Pic\icon1.png").convert("RGBA")
native = np.asarray(src.resize((64, 64), Image.NEAREST))
def build(n):
    f = 64 // n
    a = native[:n*f, :n*f]
    out = np.zeros((n, n, 4), np.uint8)
    for y in range(n):
        for x in range(n):
            block = a[y*f:(y+1)*f, x*f:(x+1)*f].reshape(-1, 4)
            opaque = block[block[:, 3] > 0]
            if len(opaque) < block.shape[0] * 0.5:
                continue
            out[y, x] = collections.Counter(map(tuple, opaque)).most_common(1)[0][0]
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
    out[:, :, 3] = np.where(out[:, :, 3] > 0, 255, 0)
    return Image.fromarray(out, "RGBA")

v16 = build(16); v20 = build(20)
sheet = Image.new("RGBA", (16*12 + 20*12 + 30, 20*12), (40, 40, 44, 255))
sheet.alpha_composite(v16.resize((16*12, 16*12), Image.NEAREST), (0, 0))
sheet.alpha_composite(v20.resize((20*12, 20*12), Image.NEAREST), (16*12 + 30, 0))
sheet.convert("RGB").save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\icon_compare3.png")
v16.save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\icon16_final.png")
print("ok")
