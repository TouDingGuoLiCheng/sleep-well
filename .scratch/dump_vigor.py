
from PIL import Image
import numpy as np
img = Image.open(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor18.png").convert("RGBA")
a = np.asarray(img)
cols = {}
def code(c):
    if c[3] == 0: return "."
    key = tuple(int(v) for v in c[:3])
    if key not in cols:
        cols[key] = "0123456789abcdefghijklmnopqrstuvwxyz"[len(cols)]
    return cols[key]
for y in range(18):
    print("".join(code(a[y, x]) for x in range(18)))
for k, v in cols.items():
    print(v, k)
