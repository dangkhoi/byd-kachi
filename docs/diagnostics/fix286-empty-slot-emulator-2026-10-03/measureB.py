import sys,json,math
from PIL import Image
def lin(c):
    c=c/255.0
    return c/12.92 if c<=0.03928 else ((c+0.055)/1.055)**2.4
def lum(p): return 0.2126*lin(p[0])+0.7152*lin(p[1])+0.0722*lin(p[2])
def ratio(a,b): hi,lo=max(a,b),min(a,b); return (hi+0.05)/(lo+0.05)
px=Image.open(sys.argv[1]).convert('RGB').load(); ink=sys.argv[2]
e=tuple(int(ink[i:i+2],16) for i in (1,3,5)); li=lum(e)
out={}
for k,(cx,cy) in {"1":(588,130),"2":(1531,130),"3":(1531,538)}.items():
    ring=[lum(px[cx+dx,cy+dy]) for dx in range(-26,27) for dy in range(-26,27) if 22<=math.hypot(dx,dy)<=25]
    m=sum(ring)/len(ring); crs=sorted(ratio(li,b) for b in ring)
    out[k]={'disc_mean':round(m,4),'cr_mean':round(ratio(li,m),2),'cr_min':round(crs[0],2),'pct_ge3':round(100*sum(c>=3 for c in crs)/len(crs),1)}
print(json.dumps(out))
