
import numpy as np, os
from PIL import Image
S = 64
out = r"D:\Game\MineCraft\模组开发\睡个好觉\src\main\resources\assets\sleep_well\textures\quilt"
xx, yy = np.meshgrid(np.arange(S), np.arange(S))

a = np.zeros((S, S, 3), np.float64)
for c, v in enumerate((246, 240, 224)): a[:, :, c] = v

# satin sheen: broad diagonal bands that are clearly visible at 16 px per block
band = np.sin(2*np.pi*(xx*1 + yy*2)/32.0)
a[:, :, 0] += band*5.0; a[:, :, 1] += band*6.5; a[:, :, 2] += band*10.0

# twill: fine diagonal weave, the thing that makes silk read as woven rather than painted
twill = np.sin(2*np.pi*(xx + yy)*16/S)
a[:, :, 0] += twill*4.0; a[:, :, 1] += twill*4.8; a[:, :, 2] += twill*3.6

# tonal drift so the sheet is not one flat value
drift = 4.0*np.sin(2*np.pi*2*xx/S + 0.7) + 3.0*np.sin(2*np.pi*3*yy/S + 1.9)
a[:, :, 0] += drift*0.8; a[:, :, 1] += drift*0.9; a[:, :, 2] += drift*0.6

# woven gold thread every 32 px: one pixel of warm ochre, the silk's only ornament
gold = (xx % 32 == 0)
a[gold] = a[gold]*0.72 + np.array([206, 182, 132])*0.28
gold2 = (yy % 32 == 0)
a[gold2] = a[gold2]*0.80 + np.array([214, 194, 150])*0.20

Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB").save(os.path.join(out, "silk.png"), optimize=True)
t = np.asarray(Image.open(os.path.join(out, "silk.png")).convert("RGB"))
Image.fromarray(np.tile(t, (3, 3, 1))).resize((S*4, S*4), Image.NEAREST).save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\silk_preview.png")
print("ok")
