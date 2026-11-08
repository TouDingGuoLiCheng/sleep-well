
from PIL import Image
import numpy as np
im = Image.open(r"D:\Pic\干劲.png")
print(im.mode, im.size)
a = np.asarray(im.convert("RGBA"))
print("opaque", round(float((a[:,:,3]>0).mean()),3))
def block_ok(a, k):
    h, w, _ = a.shape
    H, W = h//k, w//k
    t = a[:H*k, :W*k].reshape(H, k, W, k, 4)
    ref = np.broadcast_to(t[:, 0:1, :, 0:1, :], t.shape)
    return bool((t == ref).all())
for k in (32,16,8,4,2,1):
    try:
        if block_ok(a, k):
            print("block", k, "native", a.shape[1]//k); break
    except Exception as e:
        pass
