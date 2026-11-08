
from PIL import Image
import numpy as np
im = Image.open(r"D:\Game\MineCraft\模组开发\睡个好觉\素材\困倦.png").convert("RGBA")
a = np.asarray(im)
ys, xs = np.where(a[:, :, 3] > 8)
crop = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
crop.save(r"D:\Game\MineCraft\模组开发\睡个好觉\src\main\resources\assets\sleep_well\textures\mob_effect\drowsy.png")
print("drowsy", crop.size)
v = Image.open(r"D:\Game\MineCraft\模组开发\睡个好觉\src\main\resources\assets\sleep_well\textures\mob_effect\vigor.png")
print("vigor", v.size)
