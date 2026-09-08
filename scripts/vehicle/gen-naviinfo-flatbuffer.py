#!/usr/bin/env python3
"""Generate a NaviInfo flatbuffer (byd.fbs.naviInfo) for the cluster-centre render probe.
Slots from RE: ~/Library/Caches/clusternav-re/diagnostic-amap/fallback/sources/byd/fbs/naviInfo/NaviInfo.java
Requires: pip install flatbuffers  (use a venv on PEP-668 systems).
Prints the sizedByteArray as hex for  `navopen ac2 4 <hex>`  (AutoContainerManager.sendInfo2(4, bytes))."""
import sys, flatbuffers
def build(navi_state=1, road="Nguyen Hue", seg_dist=250, turn_icon=2,
          remain_time=600, remain_dist=3000, eta="12:05", seg_str="250"):
    b = flatbuffers.Builder(0)
    s_road, s_eta, s_seg = b.CreateString(road), b.CreateString(eta), b.CreateString(seg_str)
    b.StartObject(18)                                  # 18 fields (slots 0..17)
    b.PrependInt32Slot(0, navi_state, 0)               # 0  naviState (0/1 nav, 9 end)
    b.PrependUOffsetTRelativeSlot(1, s_road, 0)        # 1  nextRouteName
    b.PrependInt32Slot(2, seg_dist, 0)                 # 2  curToSegmentDist (m)
    b.PrependInt32Slot(4, turn_icon, 0)                # 4  nextTurnIcon (AMAP 0..28)
    b.PrependInt32Slot(5, remain_time, 0)              # 5  routeRemainTime (s)
    b.PrependInt32Slot(6, remain_dist, 0)              # 6  routeRemainDist (m)
    b.PrependUOffsetTRelativeSlot(7, s_eta, 0)         # 7  stringEtaArrivalTime
    b.PrependUOffsetTRelativeSlot(12, s_seg, 0)        # 12 SegRemainDisAuto
    b.Finish(b.EndObject())
    return bytes(b.Output())
if __name__ == "__main__":
    print(build().hex())
