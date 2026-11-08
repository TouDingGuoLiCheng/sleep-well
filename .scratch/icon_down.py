
from PIL import Image
import numpy as np, collections
src = Image.open(r"D:\Pic\icon1.png").convert("RGBA")
native = src.resize((64, 64), Image.NEAREST)
a = np.asarray(native)

def down_mode(a, n):
    h, w, _ = a.shape
    f = h // n
    out = np.zeros((n, n, 4), np.uint8)
    for y in range(n):
        for x in range(n):
            block = a[y*f:(y+1)*f, x*f:(x+1)*f].reshape(-1, 4)
            # majority vote on (r,g,b,a) but keep edges: prefer opaque colours, fall back to transparent
            opaque = block[block[:, 3] > 0]
            if len(opaque) < block.shape[0] * 0.5:
                out[y, x] = (0, 0, 0, 0)
                continue
            counts = collections.Counter(map(tuple, opaque))
            out[y, x] = counts.most_common(1)[0][0]
    return Image.fromarray(out, "RGBA")

v16 = down_mode(a, 16)
v20 = down_mode(a, 20)
v16.save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\icon16.png")
v20.save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\icon20.png")
sheet = Image.new("RGBA", (16*12 + 20*12 + 24, 20*12), (40, 40, 44, 255))
sheet.alpha_composite(v16.resize((16*12, 16*12), Image.NEAREST), (0, 0))
sheet.alpha_composite(v20.resize((20*12, 20*12), Image.NEAREST), (16*12 + 24, 0))
sheet.convert("RGB").save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\icon_compare.png")
print("ok")
