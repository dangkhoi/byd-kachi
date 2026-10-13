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
