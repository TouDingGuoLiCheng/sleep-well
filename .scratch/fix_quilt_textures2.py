
import numpy as np, os, math
from PIL import Image
S = 64
out = r"D:\Game\MineCraft\模组开发\睡个好觉\src\main\resources\assets\sleep_well\textures\quilt"
xx, yy = np.meshgrid(np.arange(S), np.arange(S))

def save(name, a):
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB").save(os.path.join(out, name), optimize=True)

def base(rgb):
    a = np.zeros((S, S, 3), np.float64)
    for c in range(3): a[:, :, c] = rgb[c]
    return a

def weave(a, s, fx, fy, ph=0.0):
    w = s * np.sin(2*np.pi*fx*xx/S + ph) * np.sin(2*np.pi*fy*yy/S + ph*0.5)
    a[:, :, 0] += w*0.7; a[:, :, 1] += w*0.9; a[:, :, 2] += w*0.7
    return a

def blob(a, cx, cy, rx, ry, ang, rgb, alpha):
    cx = int(round(cx)); cy = int(round(cy))
    ca, sa = math.cos(ang), math.sin(ang)
    R = int(max(rx, ry)) + 1
    for dy in range(-R, R+1):
        for dx in range(-R, R+1):
            u = dx*ca + dy*sa; v = -dx*sa + dy*ca
            if (u/rx)**2 + (v/ry)**2 <= 1.0:
                px, py = (cx+dx) % S, (cy+dy) % S
                for c in range(3):
                    a[py, px, c] = a[py, px, c]*(1-alpha) + rgb[c]*alpha

a = weave(base((233, 224, 205)), 3.2, 16, 16)
diag = (xx + yy) % 16
a[diag == 0] *= 0.96
for (fx_, fy_) in ((8, 8), (40, 8), (8, 40), (40, 40)):
    for (ox, oy, ang) in ((3.4, 0, 0), (-3.4, 0, 0), (0, 3.4, 90), (0, -3.4, 90)):
        blob(a, fx_+ox, fy_+oy, 2.6, 1.7, math.radians(ang), (190, 108, 106), 0.85)
    blob(a, fx_, fy_, 1.7, 1.7, 0, (228, 190, 96), 0.95)
    blob(a, fx_+9, fy_+9, 2.0, 1.2, math.radians(-45), (176, 130, 120), 0.5)
save("cotton.png", a)

a = base((245, 242, 233))
cx = ((xx + 16) % 32) - 16
cy = ((yy + 16) % 32) - 16
r2 = np.clip((cx*cx + cy*cy) / 256.0, 0, 1)
a *= (1.0 + 0.12*(1.0 - r2) - 0.10*r2)[:, :, None]
seam = (np.abs(cx) == 15) | (np.abs(cy) == 15)
a[seam] *= 0.74
stitch_hi = (np.abs(cx) == 16) | (np.abs(cy) == 16)
a[stitch_hi] = a[stitch_hi]*0.9 + np.array([252, 250, 244])*0.1
sheen = 4.5 * np.cos(2*np.pi*cy/32.0) * (np.abs(cx) < 12)
a[:, :, 0] += sheen*0.6; a[:, :, 1] += sheen*0.7; a[:, :, 2] += sheen*0.6
save("down.png", a)

a = base((247, 242, 228))
sheen = 10.0 * np.sin(2*np.pi*3*yy/S + 1.4*np.sin(2*np.pi*2*xx/S))
slub = 3.5 * np.sin(2*np.pi*(7*xx + yy)/S + 0.4) + 2.0*np.sin(2*np.pi*(3*xx + 5*yy)/S)
a[:, :, 0] += sheen*0.55 + slub*0.5
a[:, :, 1] += sheen*0.95 + slub*0.5
a[:, :, 2] += sheen*0.95 + slub*0.9
save("silk.png", a)

names = ["cotton", "down", "silk"]
sheet = Image.new("RGB", (S*4*3 + 16, S*4), (40, 40, 44))
for i, n in enumerate(names):
    t = np.asarray(Image.open(os.path.join(out, n + ".png")).convert("RGB"))
    im = Image.fromarray(np.tile(t, (3, 3, 1))).resize((S*4, S*4), Image.NEAREST)
    sheet.paste(im, (i*(S*4+8), 0))
sheet.save(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\quilt_fix_preview.png")
print("ok")
