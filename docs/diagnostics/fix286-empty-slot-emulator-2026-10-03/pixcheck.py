# ES1: khung trống trong suốt ⇒ điểm ảnh trong ô sát mép = điểm ảnh khe (WallView) sát mép, và (ảnh đồng màu) = màu kỳ vọng.
import sys, json
from PIL import Image
img=Image.open(sys.argv[1]).convert('RGB'); px=img.load()
pairs={'o1_trai':((12,500),(34,500)),'o3_trai':((1159,750),(1176,750)),'o3_tren':((1531,500),(1531,520)),'o1_giua':((1159,600),(588,600))}
out={}
for k,(o,i) in pairs.items():
    a,b=px[o],px[i]; out[k]={'ngoai':'#%02x%02x%02x'%a,'trong':'#%02x%02x%02x'%b,'dmax':max(abs(a[j]-b[j]) for j in range(3))}
exp=sys.argv[2] if len(sys.argv)>2 else ''
if exp:
    e=tuple(int(exp[i:i+2],16) for i in (1,3,5)); c=px[1531,750]
    out['ky_vong']={'ky_vong':exp,'giua_o3':'#%02x%02x%02x'%c,'dmax':max(abs(c[j]-e[j]) for j in range(3))}
print(json.dumps(out,ensure_ascii=False))
