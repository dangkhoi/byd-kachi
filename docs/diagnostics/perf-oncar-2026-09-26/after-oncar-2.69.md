### after-oncar-2.69 — cửa sổ 300s, 8 lõi, 2026-09-26 18:10

| Tiến trình | PID | CPU % (1 lõi) | PSS KB | RSS KB | Luồng |
|---|---|---|---|---|---|
| com.byd.launcher | 17149 | 5.68 | 67063 | 162380 | 43 |
| com.byd.launcher:wake | 17215 | 3.09 | 180114 | 269764 | 23 |

- Khung vẽ (gfxinfo com.byd.launcher): tổng/janky/%: 510 146 (28.63%)
- Dòng `com.byd.launcher` trong dumpsys power (wakelock): 0 · alarm đăng ký: 4 · job: 0
- Logcat trong cửa sổ: tổng 4725 dòng · của app 593 dòng
- KachiPerf: --------- beginning of system
--------- beginning of main
09-26 18:10:30.680 17149 17149 I KachiPerf: cửa sổ 60s · HAL đọc=312/phút · bỏ-không-hiện=474 · bỏ-xe-không-có=0 · shell=19.0/phút · log=41.2 KB/phút

_JSON: docs/diagnostics/perf-oncar-2026-09-26/after-oncar-2.69.json_

## Đọc thêm
== services
  VoiceKeyKeepAliveService}
  NavNotificationListener}
  launcher.voice.VoiceWakeService}
  modules.navaccess.NavAccessibilityService}
  modules.clustercast.FloatingBubbleService}
== KachiPerf
09-26 18:10:30.680 17149 17149 I KachiPerf: cửa sổ 60s · HAL đọc=312/phút · bỏ-không-hiện=474 · bỏ-xe-không-có=0 · shell=19.0/phút · log=41.2 KB/phút
== meminfo main
  Native Heap    29464    29436        0        0    35920    34146     1773
  Dalvik Heap     4702     4676        0        0     7994     3997     3997
         Native Heap:    29436
== meminfo wake
  Native Heap   157162   157108        0        0   177964   168508     9455
         Native Heap:   157108
== meminfo tts
== avc loadavg: 0
== top -H launcher
800%cpu 128%user  20%nice 132%sys 502%idle   2%iow  14%irq   3%sirq   0%host
800%cpu 133%user  15%nice 159%sys 474%idle   0%iow  15%irq   4%sirq   0%host
17149 17149  3.7 om.byd.launcher
17149 17149  2.0 om.byd.launcher
17149 17197  0.4 DefaultDispatch
17149 17164  0.3 HeapTaskDaemon
== top -H wake
800%cpu 128%user  21%nice 126%sys 505%idle   4%iow  13%irq   3%sirq   0%host
800%cpu 115%user  12%nice 154%sys 504%idle   0%iow  15%irq   0%sirq   0%host
17215 17241  0.4 kachi-wake
== app logcat tags/5min
 213 BYDAutoAcDevice
  71 BYDAutoSettingDevice
  65 BYDAutoBodyworkDevice
  38 ViewRootImpl[KachiHomeActivity]
  33 SimpleCast
  24 ViewRootImpl[]
  24 BYDAutoInstrumentDevice
  18 BYDAutoPM2p5Device
  12 BYDAutoStatisticDevice
   4 VmOverlayPos
   2 om.byd.launche
   2 NavRebind
== gfxinfo
Total frames rendered: 515
Janky frames: 146 (28.35%)
50th percentile: 6ms
90th percentile: 300ms
99th percentile: 400ms
== NavConnect/Rebind
09-26 18:10:54.327 17149 17149 I NavRebind: rebind trigger: com.byd.clusternav.REBIND_WATCHDOG
09-26 18:10:54.329 17149 17149 D NavRebind: watchdog alarm no-op: FGS keep-alive đang chạy watchdog in-process (hoặc phím-thoại tắt)
