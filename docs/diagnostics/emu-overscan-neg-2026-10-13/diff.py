import sys
from PIL import Image, ImageChops
a=Image.open(sys.argv[1]).convert('RGB'); b=Image.open(sys.argv[2]).convert('RGB')
d=ImageChops.difference(a,b).getbbox()
# best vertical shift estimate (b = a shifted by s)
import itertools
def score(s):
    w,h=a.size; tot=0;n=0
    for y in range(max(0,-s)+5,min(h,h-s)-5,4):
        for x in range(0,w,8):
            p=a.getpixel((x,y)); q=b.getpixel((x,y+s)); tot+=sum(abs(i-j) for i,j in zip(p,q)); n+=1
    return tot/max(n,1)
best=min(range(-70,71,1),key=score)
print(sys.argv[1],'vs',sys.argv[2],'diffbbox=',d,'bestShift(px@960w)=',best,'score0=%.1f best=%.1f'%(score(0),score(best)))
