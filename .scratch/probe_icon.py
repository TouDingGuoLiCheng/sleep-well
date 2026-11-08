
from PIL import Image
import numpy as np
p = r"D:\Pic\icon1.png"
im = Image.open(p)
print("mode", im.mode, "size", im.size)
a = np.asarray(im.convert("RGBA"))
print("corner", a[0,0], a[0,-1], a[-1,0], a[-1,-1])
cols, counts = np.unique(a.reshape(-1,4), axis=0, return_counts=True)
order = np.argsort(-counts)[:12]
for i in order:
    c = cols[i]; print(tuple(int(v) for v in c), int(counts[i]))
alpha = a[:,:,3]
print("alpha min/max/mean", int(alpha.min()), int(alpha.max()), round(float(alpha.mean()),1))
print("opaque frac", round(float((alpha>10).mean()),3))
