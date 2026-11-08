
from PIL import Image
import numpy as np
img = Image.open(r"D:\Game\MineCraft\模组开发\睡个好觉\.scratch\vigor18_crop.png").convert("RGBA")
a = np.asarray(img).copy()
for y in range(18):
    print("".join("." if a[y,x,3]==0 else ("#" if a[y,x,:3].sum()<260 else ("S" if abs(int(a[y,x,0])-250)<12 and abs(int(a[y,x,1])-220)<14 else ("G" if a[y,x,2]<130 and a[y,x,0]>200 else "g"))) for x in range(18)))
