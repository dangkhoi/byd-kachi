# balance.py <dir> — per widget frame: horizontal offset of single-cluster rows + vertical gap top/bottom of content
import re, sys, glob, os
sys.path.insert(0, os.path.dirname(__file__))
from analyze_lib import GRIDS, frames, widgets
out=[]
for f in sorted(glob.glob(sys.argv[1]+"/*.xml")):
    name=os.path.basename(f)[:-4]; lay=name.split("_")[0]
    if lay not in GRIDS: continue
    x=open(f,encoding="utf-8").read(); fr=frames(GRIDS[lay]); ws=widgets(name)
    nodes={}
    for m in re.finditer(r'text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x):
        t=m.group(1).replace("&amp;","&").strip(); a,b,c,d=map(int,m.groups()[1:])
        if not t: continue
        cx,cy=(a+c)/2,(b+d)/2
        for i,(l,tp,r,bt) in enumerate(fr):
            if l<=cx<r and tp<=cy<bt and i<len(ws): nodes.setdefault(i,[]).append((t,a,b,c,d))
    for i,ns in nodes.items():
        l,tp,r,bt=fr[i]; fcx=(l+r)/2
        # rows: cluster by vertical overlap
        rows=[]
        for n in sorted(ns,key=lambda n:n[2]):
            for row in rows:
                if min(row[-1][4],n[4])-max(row[-1][2],n[2])>0.5*min(n[4]-n[2],row[-1][4]-row[-1][2]): row.append(n); break
            else: rows.append([n])
        flags=[]
        for row in rows:
            if len(row)!=2: continue
            row=sorted(row,key=lambda n:n[1]); a=row[0][1]; c=row[-1][3]
            gap=row[1][1]-row[0][3]
            if gap>12 or (c-a)>0.8*(r-l): continue   # not a value+unit cluster
            off=(a+c)/2-fcx
            if abs(off)>6: flags.append(f"HOFF {row[0][0]!r}+{row[1][0]!r} {off:+.0f}px")
        top=min(n[2] for n in ns); bot=max(n[4] for n in ns)
        # content below first row (header) — gap under content
        out.append((ws[i],lay,r-l,bt-tp,top-tp,bt-bot,flags))
for w,lay,fw,fh,gt,gb,fl in sorted(out):
    vf="VEMPTY" if gb>max(0.35*fh,gt+0.25*fh) else ""
    print(f"{w:28s} {lay:6s} {fw}x{fh} top={gt} bottom={gb} {vf} {'; '.join(fl)}")
