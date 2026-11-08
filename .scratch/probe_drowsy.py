
from PIL import Image
import numpy as np
p = r"D:\Game\MineCraft\模组开发\睡个好觉\素材\困倦.png"
im = Image.open(p)
print(im.mode, im.size)
a = np.asarray(im.convert("RGBA"))
print("corner", a[0,0], "opaque", round(float((a[:,:,3]>0).mean()),3))
def block_ok(a,k):
    h,w,_=a.shape; H,W=h//k,w//k
    t=a[:H*k,:W*k].reshape(H,k,W,k,4); ref=np.broadcast_to(t[:,0:1,:,0:1,:],t.shape)
    return bool((t==ref).all())
for k in (32,16,8,4,2,1):
    try:
        if block_ok(a,k): print("block",k,"native",a.shape[1]//k); break
    except Exception: pass
