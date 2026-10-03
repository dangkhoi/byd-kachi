# Tương phản ⇄ ô trống so với NỀN THẬT dưới icon: ảnh tham chiếu cùng cấu hình ở bố cục TWO_COL (vị trí đó là lòng ô trống
# trong suốt, không có nút) ⇒ đúng điểm ảnh WallView dưới icon. Màu icon = màu binding ghi ở log (đối chiếu với ảnh THREE).
import sys, json
from PIL import Image
def lin(c):
    c=c/255.0
    return c/12.92 if c<=0.03928 else ((c+0.055)/1.055)**2.4
def lum(p): return 0.2126*lin(p[0])+0.7152*lin(p[1])+0.0722*lin(p[2])
def ratio(a,b):
    hi,lo=max(a,b),min(a,b); return (hi+0.05)/(lo+0.05)
shot=Image.open(sys.argv[1]).convert('RGB').load(); ref=Image.open(sys.argv[2]).convert('RGB').load()
inks=json.loads(sys.argv[3])     # {"1":"#eaf0f8",...}
cent={"1":(588,130),"2":(1531,130),"3":(1531,538)}
out={}
for k,(cx,cy) in cent.items():
    ink=inks.get(k)
    if not ink: continue
    e=tuple(int(ink[i:i+2],16) for i in (1,3,5)); li=lum(e)
    box=[(x,y) for x in range(cx-15,cx+15) for y in range(cy-15,cy+15)]
    bg=[lum(ref[p]) for p in box]
    crs=sorted(ratio(li,b) for b in bg)
    # icon thật trên ảnh THREE: điểm gần màu log nhất phải gần như trùng (dmax nhỏ) ⇒ màu log = màu vẽ
    near=min(box,key=lambda p:sum(abs(shot[p][j]-e[j]) for j in range(3)))
    dmax=max(abs(shot[near][j]-e[j]) for j in range(3))
    mean=sum(bg)/len(bg)
    out[k]={'ink':ink,'ink_on_screen_dmax':dmax,'bg_mean':round(mean,4),'cr_vs_mean':round(ratio(li,mean),2),
            'cr_p10':round(crs[len(crs)//10],2),'cr_min':round(crs[0],2),'pct_ge3':round(100*sum(c>=3 for c in crs)/len(crs),1)}
print(json.dumps(out,ensure_ascii=False))
