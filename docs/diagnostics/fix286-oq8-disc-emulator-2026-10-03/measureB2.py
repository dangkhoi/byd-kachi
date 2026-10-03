# OQ8 · B — tương phản icon ⇄ (MUT) trên ĐĨA KÍNH, đo trên ảnh chụp màn THẬT (1920×1080, 240 dpi ⇒ đĩa ⌀54 px, icon 30 px).
# Nền của icon = chính đĩa ⇒ đo thẳng trên ảnh THREE, không cần ảnh tham chiếu như phương án A.
# Vòng đĩa: 16 ≤ r ≤ 24 px quanh tâm nút, BỎ hộp icon 30×30 (nét ⇄ có thể chạm góc hộp); mép khử răng cưa của đĩa ở r≈27.
import sys, json, math
from PIL import Image
def lin(c):
    c=c/255.0
    return c/12.92 if c<=0.03928 else ((c+0.055)/1.055)**2.4
def lum(p): return 0.2126*lin(p[0])+0.7152*lin(p[1])+0.0722*lin(p[2])
def ratio(a,b): hi,lo=max(a,b),min(a,b); return (hi+0.05)/(lo+0.05)
px=Image.open(sys.argv[1]).convert('RGB').load(); ink=sys.argv[2]
cent=json.loads(sys.argv[3])   # {"1":[x,y],...}
e=tuple(int(ink[i:i+2],16) for i in (1,3,5)); li=lum(e)
out={}
for k,(cx,cy) in cent.items():
    ring=[px[cx+dx,cy+dy] for dx in range(-24,25) for dy in range(-24,25)
          if 16<=math.hypot(dx,dy)<=24 and not (abs(dx)<=15 and abs(dy)<=15)]
    out_ring=[lum(px[cx+dx,cy+dy]) for dx in range(-36,37) for dy in range(-36,37) if 31<=math.hypot(dx,dy)<=35]
    rl=[lum(p) for p in ring]; crs=sorted(ratio(li,b) for b in rl)
    box=[px[x,y] for x in range(cx-15,cx+15) for y in range(cy-15,cy+15)]
    near=min(box,key=lambda p:sum(abs(p[j]-e[j]) for j in range(3)))
    m=sum(rl)/len(rl); mo=sum(out_ring)/len(out_ring)
    out[k]={'n':len(rl),'icon_dmax':max(abs(near[j]-e[j]) for j in range(3)),'disc_L':[round(min(rl),4),round(m,4),round(max(rl),4)],
            'cr_min':round(crs[0],2),'cr_mean':round(ratio(li,m),2),'pct_ge3':round(100*sum(c>=3 for c in crs)/len(crs),1),
            'pct_ge45':round(100*sum(c>=4.5 for c in crs)/len(crs),1),'out_L_mean':round(mo,4)}
print(json.dumps(out,ensure_ascii=False))
