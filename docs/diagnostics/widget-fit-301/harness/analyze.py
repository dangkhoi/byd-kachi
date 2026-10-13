import re, sys, glob, os, json
GRIDS={"NARROW":"0,0,2,6;2,0,2,6;4,0,2,6;6,0,2,6;8,0,2,6;10,0,2,6","SMALL":"0,0,2,3;2,0,2,3;4,0,2,3;6,0,2,3;8,0,2,3;10,0,2,3",
"WIDE":"0,0,6,2;6,0,6,2;0,2,6,2;6,2,6,2;0,4,6,2;6,4,6,2","LARGE":"0,0,4,6;4,0,4,6;8,0,4,6","owner":"0,0,10,6;10,0,2,3;10,3,2,3"}
SETS=["g_trip g_energy g_battery g_climate g_windows g_lights","g_tyres g_doors w_energy w_tire w_pm25 w_clock","w_media w_car w_speed w_board w_photos w_apps","soc speed motor_power ev_range_km gear window_lf","soh_oem trunk trip_hours,odometer,ev_range_km,soc g_trip g_climate g_energy"]
AX,AY,AW,AH,G=19,89,1882,826,13
def edge(i,n,t): inner=t-(n-1)*G; return i*inner//n+i*G
def frames(g):
    out=[]
    for f in g.split(";"):
        c,r,w,h=map(int,f.split(",")); out.append((AX+edge(c,12,AW),AY+edge(r,6,AH),AX+edge(c+w,12,AW)-G if c+w<12 else AX+AW,AY+edge(r+h,6,AH)-G if r+h<6 else AY+AH))
    return out
def widgets(name):
    lay=name.split("_")[0]
    if lay=="owner": return ["w_clock","g_tyres","g_trip"]
    k=name.split("_")[1]; idx=int(k[0])-1; ws=SETS[idx].split()
    if lay=="LARGE": ws=ws[:3] if k.endswith("a") else ws[3:]
    return ws
res={}
for f in sorted(glob.glob(sys.argv[1]+"/*.xml")):
    name=os.path.basename(f)[:-4]; lay=name.split("_")[0]
    x=open(f,encoding="utf-8").read(); fr=frames(GRIDS[lay]); ws=widgets(name)
    for m in re.finditer(r'text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x):
        t=m.group(1).replace("&amp;","&"); a,b,c,d=map(int,m.groups()[1:]); cx,cy=(a+c)/2,(b+d)/2
        for i,(l,tp,r,bt) in enumerate(fr):
            if l<=cx<r and tp<=cy<bt and i<len(ws):
                w,h=c-a,d-b; L=len(t.strip())
                flags=[]
                if L>=3 and w/L<4.5: flags.append("SHRED")
                if h<22 and t.strip(): flags.append("CLIPH")
                if w<=0 or h<=0: flags.append("ZERO")
                res.setdefault((ws[i],lay,fr[i][2]-fr[i][0],fr[i][3]-fr[i][1]),[]).append((t,w,h,flags))
for (wid,lay,fw,fh),nodes in sorted(res.items()):
    bad=[n for n in nodes if n[3]]
    print(f"{wid:28s} {lay:6s} {fw}x{fh}  nodes={len(nodes)} bad={len(bad)}  " + "; ".join(f"{t!r}:{w}x{h}{'/'.join(fl)}" for t,w,h,fl in bad[:6]))
