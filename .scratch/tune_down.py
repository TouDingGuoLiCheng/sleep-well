
import numpy as np, os
from PIL import Image
S = 64
out = r"D:\Game\MineCraft\模组开发\睡个好觉\src\main\resources\assets\sleep_well\textures\quilt"
xx, yy = np.meshgrid(np.arange(S), np.arange(S))
a = np.zeros((S, S, 3), np.float64)
for c, v in enumerate((246, 243, 235)): a[:, :, c] = v
cx = ((xx + 16) % 32) - 16
cy = ((yy + 16) % 32) - 16
r2 = np.clip((cx*cx + cy*cy) / 256.0, 0, 1)
a *= (1.0 + 0.09*(1.0 - r2) - 0.07*r2)[:, :, None]
seam = (np.abs(cx) == 15) | (np.abs(cy) == 15)
a[seam] *= 0.84
hi = (np.abs(cx) == 14) | (np.abs(cy) == 14)
a[hi] = a[hi]*0.94 + np.array([252, 250, 244])*0.06
sheen = 3.0 * np.cos(2*np.pi*cy/32.0) * (np.abs(cx) < 12)
a[:, :, 0] += sheen*0.5; a[:, :, 1] += sheen*0.6; a[:, :, 2] += sheen*0.5
Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB").save(os.path.join(out, "down.png"), optimize=True)
t = np.asarray(Image.open(os.path.join(out, "down.png")).convert("RGB"))
Image.fromarray(np.tile(t, (3, 3, 1))).resize((S*4, S*4), Image.NEAREST).save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\down_preview.png")
print("ok")
