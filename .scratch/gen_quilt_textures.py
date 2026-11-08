
import numpy as np, os, math
from PIL import Image

S = 64
out = r"D:\Game\MineCraft\模组开发\睡个好觉\src\main\resources\assets\sleep_well\textures\quilt"
os.makedirs(out, exist_ok=True)
xx, yy = np.meshgrid(np.arange(S), np.arange(S))
X = np.arange(S)

def save(name, a):
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGB").save(os.path.join(out, name), optimize=True)

def base(rgb):
    a = np.zeros((S, S, 3), np.float64)
    for c in range(3):
        a[:, :, c] = rgb[c]
    return a

def weave(a, strength, fx, fy, phase=0.0):
    w = strength * np.sin(2*np.pi*fx*xx/S + phase) * np.sin(2*np.pi*fy*yy/S + phase*0.5)
    a[:, :, 0] += w*0.7; a[:, :, 1] += w*0.9; a[:, :, 2] += w*0.7
    return a

def blend_px(a, x, y, rgb, alpha):
    """Alpha-blend one pixel, wrapping the coordinate so the tile stays seamless."""
    X_, Y_ = x % S, y % S
    for c in range(3):
        a[Y_, X_, c] = a[Y_, X_, c]*(1-alpha) + rgb[c]*alpha

def motif_ellipse(a, cx, cy, rx, ry, angle, rgb, alpha):
    ca, sa = math.cos(angle), math.sin(angle)
    R = int(max(rx, ry)) + 1
    for dy in range(-R, R+1):
        for dx in range(-R, R+1):
            u = dx*ca + dy*sa
            v = -dx*sa + dy*ca
            if (u/rx)**2 + (v/ry)**2 <= 1.0:
                blend_px(a, cx+dx, cy+dy, rgb, alpha)

# ---------------- cotton: warm cream, fine weave, red-pink flowers, faint quilt lines
a = weave(base((233, 224, 205)), 3.2, 16, 16)
diag = (xx + yy) % 16
a[diag == 0] *= 0.93
for (fx_, fy_) in ((8, 8), (40, 8), (8, 40), (40, 40)):
    for (px, py) in ((fx_, fy_), (fx_+3, fy_-3), (fx_-3, fy_+3), (fx_+3, fy_+3), (fx_-3, fy_-3)):
        motif_ellipse(a, px, py, 3.4, 2.0, 0.0, (188, 106, 104), 0.85)
    motif_ellipse(a, fx_, fy_, 1.6, 1.6, 0.0, (228, 190, 96), 0.95)
# tiny stems
for (fx_, fy_) in ((8, 8), (40, 8), (8, 40), (40, 40)):
    for k in range(4, 7):
        blend_px(a, fx_+k, fy_+k, (150, 168, 120), 0.5)
save("cotton.png", a)

# ---------------- synthetic: pale blue-grey, fine fibre, diamond stitching
a = weave(base((224, 230, 236)), 2.0, 8, 8, 0.7)
fib = 2.6 * np.sin(2*np.pi*(xx + yy)*8/S)
a[:, :, 0] += fib*0.8; a[:, :, 1] += fib*0.8; a[:, :, 2] += fib*1.1
stitch = ((xx + yy) % 16 == 0) | ((xx - yy) % 16 == 0)
a[stitch] *= 0.86
save("synthetic.png", a)

# ---------------- down: cassette squares, bulge + stitched seams
a = base((245, 242, 233))
cx = ((xx + 16) % 32) - 16
cy = ((yy + 16) % 32) - 16
r2 = np.clip((cx*cx + cy*cy) / 256.0, 0, 1)
bulge = 1.0 + 0.10*(1.0 - r2) - 0.10*r2
a *= bulge[:, :, None]
seam = (np.abs(cx) >= 15) | (np.abs(cy) >= 15)
a[seam] = a[seam]*0.80 + np.array([214, 208, 196])*0.20
# a soft sheen streak inside each cassette
sheen = 3.0 * np.cos(2*np.pi*cy/32.0) * (np.abs(cx) < 12)
a[:, :, 0] += sheen*0.6; a[:, :, 1] += sheen*0.7; a[:, :, 2] += sheen*0.6
save("down.png", a)

# ---------------- silk: ivory satin, horizontal sheen, faint slubs
a = base((247, 242, 228))
sheen = 7.0 * np.sin(2*np.pi*3*yy/S + 1.3*np.sin(2*np.pi*2*xx/S))
slub = 3.0 * np.sin(2*np.pi*(7*xx + yy)/S + 0.4) + 2.0*np.sin(2*np.pi*(3*xx + 5*yy)/S)
a[:, :, 0] += sheen*0.55 + slub*0.5
a[:, :, 1] += sheen*0.95 + slub*0.5
a[:, :, 2] += sheen*0.95 + slub*0.9
save("silk.png", a)

# ---------------- summer: pale mint cotton with bamboo leaves
a = weave(base((230, 241, 232)), 2.4, 16, 16, 2.0)
leaves = ((9, 12, -0.6), (34, 9, 0.5), (21, 35, -0.2), (47, 40, 0.8), (3, 45, 0.2), (52, 22, -0.9))
for (lx, ly, ang) in leaves:
    motif_ellipse(a, lx, ly, 7.0, 2.6, ang, (150, 188, 156), 0.85)
    motif_ellipse(a, lx, ly, 5.0, 1.0, ang, (176, 206, 180), 0.55)
    for k in (-1, 1):
        motif_ellipse(a, lx + int(4*math.sin(ang))*k, ly + int(4*math.cos(ang))*k, 3.0, 1.4, ang, (162, 196, 166), 0.5)
# thin vertical weave lines
a[xx % 8 == 0] *= 0.965
save("summer_cool.png", a)

names = ["cotton", "synthetic", "down", "silk", "summer_cool"]
sheet = Image.new("RGB", (2*S*4*2 + 8, 3*S*4*3 + 16), (40, 40, 44))
for i, n in enumerate(names):
    t = np.asarray(Image.open(os.path.join(out, n + ".png")).convert("RGB"))
    tile3 = np.tile(t, (3, 3, 1))
    im = Image.fromarray(tile3).resize((S*4, S*4), Image.NEAREST)
    sheet.paste(im, ((i % 2)*(S*4+8), (i // 2)*(S*4+8)))
prev = r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\quilt_materials_preview.png"
sheet.save(prev)
print("ok")
