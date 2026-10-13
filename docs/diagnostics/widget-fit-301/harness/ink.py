# ink.py <png> <x0> <y0> <x1> <y1> : ink bbox in rect (pixels far from rect's dominant bg)
import sys
from PIL import Image
from collections import Counter
im=Image.open(sys.argv[1]).convert("RGB"); x0,y0,x1,y1=map(int,sys.argv[2:6])
c=im.crop((x0,y0,x1,y1)); px=c.load(); w,h=c.size
bg=Counter(px[i,j] for i in range(0,w,2) for j in range(0,h,2)).most_common(1)[0][0]
xs=[];ys=[]
for i in range(w):
    for j in range(h):
        p=px[i,j]
        if sum(abs(p[k]-bg[k]) for k in range(3))>90: xs.append(i); ys.append(j)
if xs: print(x0+min(xs),y0+min(ys),x0+max(xs)+1,y0+max(ys)+1)
else: print("none")
