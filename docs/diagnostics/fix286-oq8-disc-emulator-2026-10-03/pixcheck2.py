# ES1 dưới B: lòng ô trống ngoài đĩa vẫn = điểm ảnh WallView. Cặp điểm tránh đĩa (đĩa ô 3 ở y 511–565 ⇒ cặp mép trên dời sang x=1400).
import sys, json
from PIL import Image
px=Image.open(sys.argv[1]).convert('RGB').load()
pairs={'o1_trai':((12,500),(34,500)),'o3_trai':((1159,750),(1176,750)),'o3_tren':((1400,500),(1400,520)),'o1_giua':((1159,600),(588,600))}
out={}
for k,(o,i) in pairs.items():
    a,b=px[o],px[i]; out[k]=max(abs(a[j]-b[j]) for j in range(3))
exp=sys.argv[2] if len(sys.argv)>2 else ''
if exp:
    e=tuple(int(exp[i:i+2],16) for i in (1,3,5)); c=px[1531,750]
    out['ky_vong_dmax']=max(abs(c[j]-e[j]) for j in range(3))
print(json.dumps(out,ensure_ascii=False))
