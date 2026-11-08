
from PIL import Image
import numpy as np
p = r"D:\Pic\icon1.png"
im = Image.open(p).convert("RGBA")
a = np.asarray(im)
# detect the pixel-art block size: largest k where the image is built of k x k blocks
def block_ok(a, k):
    h, w, _ = a.shape
    b = a[:h//k*k, :w//k*k].reshape(h//k, k, w//k, k, 4)
    return bool((b == b[:, :1, :, :1].repeat(k, axis=1).repeat(k, axis=2)).all())
for k in (32,16,8,4,2,1):
    if block_ok(a, k):
        print("block", k, "native", a.shape[1]//k); break
