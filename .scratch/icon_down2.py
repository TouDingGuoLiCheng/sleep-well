
from PIL import Image
import numpy as np
src = Image.open(r"D:\Pic\icon1.png").convert("RGBA")
native = np.asarray(src.resize((64, 64), Image.NEAREST)).astype(np.float64)

# palette: every colour that actually appears in the artwork
pal = np.unique(np.asarray(src.resize((64, 64), Image.NEAREST)).reshape(-1, 4)[
    np.asarray(src.resize((64, 64), Image.NEAREST)).reshape(-1, 4)[:, 3] > 0], axis=0)
pal = pal[:, :3].astype(np.float64)
print("palette", len(pal))

def area_quant(a, n):
    h, w, _ = a.shape
    f = h / n
    out = np.zeros((n, n, 4), np.uint8)
    for y in range(n):
        y0, y1 = int(round(y*f)), int(round((y+1)*f))
        for x in range(n):
            x0, x1 = int(round(x*f)), int(round((x+1)*f))
            block = a[y0:y1, x0:x1].reshape(-1, 4)
            alpha = block[:, 3].mean()
            if alpha < 110:
                continue
            rgb = block[block[:, 3] > 100][:, :3]
            if len(rgb) == 0:
                continue
            mean = rgb.mean(axis=0)
            d = ((pal - mean) ** 2).sum(axis=1)
            out[y, x] = (*pal[int(np.argmin(d))].astype(np.uint8), 255)
    return Image.fromarray(out, "RGBA")

v20 = area_quant(native, 20)
v16 = area_quant(native, 16)
v20.save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\icon20q.png")
v16.save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\icon16q.png")
sheet = Image.new("RGBA", (20*12 + 16*12 + 24, 20*12), (40, 40, 44, 255))
sheet.alpha_composite(v20.resize((20*12, 20*12), Image.NEAREST), (0, 0))
sheet.alpha_composite(v16.resize((16*12, 16*12), Image.NEAREST), (20*12 + 24, 0))
sheet.convert("RGB").save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\icon_compare2.png")
print("ok")
