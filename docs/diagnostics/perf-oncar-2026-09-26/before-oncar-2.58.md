### before-oncar-2.58 — cửa sổ 300s, 8 lõi, 2026-09-26 17:59

| Tiến trình | PID | CPU % (1 lõi) | PSS KB | RSS KB | Luồng |
|---|---|---|---|---|---|
| com.byd.launcher | 3885 | 1.20 | 245627 | 349920 | 58 |
| com.byd.launcher:wake | 4260 | 3.96 | 188643 | 299692 | 26 |
| com.byd.launcher:tts | 12814 | 0.00 | 129834 | 221964 | 15 |

- Khung vẽ (gfxinfo com.byd.launcher): tổng/janky/%: 2303 1289 (55.97%)
- Dòng `com.byd.launcher` trong dumpsys power (wakelock): 0 · alarm đăng ký: 5 · job: 0
- Logcat trong cửa sổ: tổng 4262 dòng · của app 478 dòng
- KachiPerf: --------- beginning of system
--------- beginning of main
09-26 17:58:58.953  3885  3885 I KachiPerf: cửa sổ 60s · HAL đọc=312/phút · bỏ-không-hiện=474 · bỏ-xe-không-có=0 · shell=19.0/phút · log=0.0 KB/phút

_JSON: docs/diagnostics/perf-oncar-2026-09-26/before-oncar-2.58.json_
== KachiPerf
== meminfo main
== meminfo wake
== avc loadavg: 0
== top -H
== HAL raw/min (BYDAuto lines in last logcat)
0
0

## Đọc thêm sau snapshot
== KachiPerf
--------- beginning of system
--------- beginning of main
09-26 17:58:58.953  3885  3885 I KachiPerf: cửa sổ 60s · HAL đọc=312/phút · bỏ-không-hiện=474 · bỏ-xe-không-có=0 · shell=19.0/phút · log=0.0 KB/phút
== meminfo main
  Native Heap   219517   219488        0        0   329152   317574    11577
  Dalvik Heap     2721     2696        0        0     5825     2913     2912
         Native Heap:   219488
== meminfo wake
  Native Heap   165706   165676        0        0   187888   174071    13816
         Native Heap:   165676
== meminfo tts
  Native Heap   113126   113092        0        0   169516   167675     1840
         Native Heap:   113092
== avc loadavg: 0
== top -H launcher (15 s)
800%cpu 105%user  26%nice  79%sys 573%idle   2%iow  13%irq   3%sirq   0%host
800%cpu  85%user  26%nice  56%sys 619%idle   0%iow  15%irq   0%sirq   0%host
 3885  3910  0.3 HeapTaskDaemon
 3885  4237  0.2 DefaultDispatch
 3885  3885  0.2 om.byd.launcher
== top -H wake (15 s)
800%cpu 104%user  25%nice  71%sys 583%idle   2%iow  13%irq   2%sirq   0%host
800%cpu 127%user  23%nice 100%sys 535%idle   0%iow  12%irq   4%sirq   0%host
 4260 12059  0.3 kachi-wake
== app logcat tags/5min
 269 BYDAutoAcDevice
  89 BYDAutoSettingDevice
  81 BYDAutoBodyworkDevice
  60 ViewRootImpl[KachiHome]
  40 SimpleCast
  32 BYDAutoInstrumentDevice
  26 BYDAutoPM2p5Device
  16 BYDAutoStatisticDevice
   5 VmOverlayPos
   4 NavConnect
   2 Pm25Filter
   2 om.byd.launche
== wake logcat tags
   2 d.launcher:wak
   1 ---------
== gfxinfo
Total frames rendered: 2316
Janky frames: 1289 (55.66%)
50th percentile: 17ms
90th percentile: 28ms
99th percentile: 77ms
