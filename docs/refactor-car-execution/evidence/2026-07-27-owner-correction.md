# Owner correction, 2026-07-27 09:52 (authoritative)

The physical cluster NEVER showed VietMap at any point this morning. It showed the native gauges
throughout.

This contradicts what the app believed. The captured fixture proves the Android side of it:

    Stack id=26 bounds=[0,0][1920,720] displayId=1 userId=0
      taskId=26: vn.vietmap.live/vn.vietmap.live.MainActivity ... visible=true

So the task really was parented to the cluster's virtual display, and the app's observer reported
`coarseState=ACTIVE_SINGLE, target=vn.vietmap.live, displayId=1` — while the driver saw gauges.

## What this invalidates
- "Task on display 1" is NOT evidence that the cluster is showing that app. The app's success
  criterion is therefore unverifiable, and every state built on it is suspect: this morning's
  "Đang chờ xác nhận từ cụm" and the adoption path I added at 09:40 would have declared success for
  something invisible.
- `bydAdd-<pkg>` layer presence is NOT a projection signal either. With the projection closed and
  gauges on screen, both `bydAdd-com.byd.clusternav#0` and `bydAdd-vn.vietmap.live#0` are present.
- Display 1 existence/state is not a signal: it is always `type VIRTUAL, state ON,
  owner com.xdja.containerservice`, whether or not anything is projected.

## What is still unknown, and must be answered before any further wiring
Which observable distinguishes "the cluster is showing our app" from "the cluster is showing the
native gauges". Candidates to evaluate off-car against paired dumps: composition on layerStack 1 in
the full SurfaceFlinger dump, and whether FissionHostSvc/AutoContainer expose projection state.

Until that observable exists, Cluster Cast cannot honestly report success, and no amount of state
machine repair will fix it. This is the first question for the car-execution core (Track 1).

## Candidate observable found (09:55, closed state)

With the projection closed and gauges on screen, the cluster's own display device composites nothing:

    + DisplayDevice{virtual, "fission_bg_xdjaVirtualSurface"}
       powerMode=2, activeConfig=0, numLayers=0
       isEnabled=true isSecure=false layerStack=1 layerStackInternal=false

`numLayers=0` is the first signal seen so far that reflects what the driver actually sees, rather than
what the window manager was told. It is one line, cheap to read, and independent of which display a
task is parented to — which is precisely the property the current success criterion lacks.

Read it with:

    dumpsys SurfaceFlinger | grep -A2 'DisplayDevice{virtual, "fission_bg_xdjaVirtualSurface"}'

### How to confirm, next car session, in two minutes
1. Cluster showing gauges: record `numLayers` (expected 0, already captured here).
2. Place a task on display 1 WITHOUT the OEM cast sequence: record `numLayers`. If it stays 0 while a
   task sits on display 1, that alone proves the current criterion is wrong and this one is better.
3. Issue the OEM cast sequence (castSeq 30,16,35) so the cluster visibly shows the app: record
   `numLayers` (expected > 0).
4. Issue teardown 18,0: `numLayers` must return to 0 and the gauges must return.

Note on quoting, learned today: `service call ... s16 ""` loses the empty string through adb and the
call fails with `Parcel(fffffffc ...)`. It must be sent as a single quoted remote command:

    adb shell "service call AutoContainer 2 i32 1000 i32 18 s16 ''"

Both opcodes then return `Parcel(00000000 00000000)`.
