import os
from PIL import Image
Q = r"D:\Game\MineCraft\模组开发\车万女仆：睡个好觉\src\main\resources\assets\sleep_well\textures\quilt"
SRC = r"D:\Game\MineCraft\模组开发\车万女仆：睡个好觉\素材"
pairs = {"春之被.png": "spring_cherry.png", "夏之被.png": "summer_sunflower.png",
         "秋之被.png": "autumn.png", "冬之被.png": "winter.png"}
TARGET = (192, 307)   # 5:8, the cloth's own shape
for src, dst in pairs.items():
    path = os.path.join(SRC, src)
    if not os.path.exists(path):
        print("missing", src); continue
    im = Image.open(path).convert("RGBA")
    if im.size == TARGET:
        continue
    # Stretch to the cloth's shape, then bring it down to 192 wide at exactly 4:1 so pixels stay crisp.
    tall = im.resize((768, 1228), Image.BILINEAR)
    out = tall.resize(TARGET, Image.NEAREST)
    out.save(os.path.join(Q, dst))
    print(dst, im.size, "->", out.size)
