# VietMap 3.3.4 same-device HUD research — 2026-08-04

## Verdict

**VietMap and ClusterNav cannot form a supported BLE GATT self-connection on the same Android Bluetooth adapter.** Android's public BLE contract is central → remote peripheral; it exposes no local/self GATT-loopback API. A vendor-modified Bluetooth stack, virtual controller, root hook, or second physical adapter could synthesize one, but none is an acceptable no-root product path.

However, VietMap 3.3.4 exposes two useful **same-device, no-root data surfaces that do not use BLE**:

1. **AppWidgetHost — recommended:** speed limit, current speed, road alerts and alert distances.
2. **Accessibility — proven fallback:** current speed, speed limit, next-turn distance and road text while VietMap semantics remain visible/accessibile.

Full HUD parity (maneuver image, all route fields, ETA, etc.) is not available through a public cross-app API. It would require patching/re-signing VietMap or a privileged/root hook into its process-local HUD channel.

## Exact VietMap 3.3.4 findings

- Confirmed package version: `vn.vietmap.live` 3.3.4.
- HUD Flutter channel: `vietmap_hud_sdk`.
- Native channel handler converts Flutter method calls into internal HUD SDK calls and remote GATT writes.
- H50 BLE transport:
  - service `0000fff0-0000-1000-8000-00805f9b34fb`
  - notify `fff1`
  - write `fff2`
- Important correction: `fff4`/`fff3` are selected only when the connected device name contains `TPMS`; they are not the normal H50 pair.
- `VMBluetoothService` is non-exported.
- `RunningStatusProvider` exposes only running/overlay status, not navigation data.
- `VIETMAPLiveAndroidAutoService` carries full Android Auto `Trip` state, but it is a Car App host contract, not a public arbitrary-client feed.
- The Flutter MethodChannel is process-local; another APK cannot call it directly.

## Best path: VietMap AppWidgetHost bridge

VietMap deliberately publishes live data through exported widget providers:

- `VMOnlySpeedLimitWidgetProvider`
- `VMAlertWidgetProvider`

Runtime `dumpsys appwidget` confirms both providers are registered. VietMap's updater writes:

- current speed: `osw_current_speed_tv` / `current_speed_textview`
- speed limit: `speed_limit_widget_text_view`
- first/second alert speed limit
- first/second alert distance
- alert bitmaps/icons and visibility state

### Proposed bridge

1. ClusterNav implements a small `AppWidgetHost`.
2. Allocate and persist widget IDs.
3. Bind the VietMap speed-limit and alert widgets using `bindAppWidgetIdIfAllowed()`.
4. If binding is not already allowed, show the standard `ACTION_APPWIDGET_BIND` consent screen once.
5. Keep an `AppWidgetHostView` listening to RemoteViews updates.
6. After `updateAppWidget`, inspect the applied child views using resource IDs resolved dynamically from package `vn.vietmap.live`.
7. Debounce each update batch, validate values, and publish a typed ClusterNav snapshot.

Do not reflect private `RemoteViews` action internals. Inspect the host view after `RemoteViews` has been applied.

### Expected data

- Reliable target: current speed and speed limit.
- Likely target: up to two road/camera alert distances and numeric limits.
- Bitmap alert type requires versioned image hashing/template matching if a structured enum is needed.
- Not exposed by these widgets: next-road text, maneuver direction, full route ETA.

### Risks

- One-time user/system widget-binding consent is required for a normal app.
- BYD firmware must provide the bind confirmation activity; verify on-car.
- Widget resource names/layouts can change between VietMap versions; resolve by package/version and fail closed.
- Updates are presentation-shaped and not atomic; inspect after main-loop settling/debounce.
- Bindings can require renewal after app-data clear, uninstall, provider replacement or profile changes.

## Accessibility fallback — already proven

VietMap 3.3.4 navigation semantics on the emulator exposed:

- `100m Hẻm 7/8 Thành Thái`
- `0\nkm/h\n50`

This provides:

- next-turn distance
- road text
- current speed
- speed limit

It did not expose the visual maneuver icon in the observed tree. Accessibility may also weaken when VietMap is fully backgrounded or its window is no longer exposed, so it is secondary to widgets for numeric data.

## Other options

| Option | Fidelity | Privilege | Verdict |
|---|---:|---:|---|
| Same-adapter BLE self-link | None supported | Public API | Reject |
| AppWidgetHost | Speed/current speed/alerts | User bind consent | Best no-root experiment |
| Accessibility | Speed/limit/distance/road text | User Accessibility grant | Viable fallback |
| Android Auto host impersonation | Potentially full Trip | Trusted host contract | Impractical/unproven |
| HCI snoop | Raw packets only, requires real HUD | ADB/privileged | Forensics only |
| Patch/re-sign VietMap | Potentially full HUD payload | Replace official APK | Technically possible, high maintenance/risk |
| Root/Xposed/Frida hook | Full internal data possible | Root/injection | Out of scope |

## Recommended next experiment

Build only a **minimal visible AppWidgetHost proof** before any further BLE work:

1. Bind `VMOnlySpeedLimitWidgetProvider` on emulator, then on the BYD target.
2. Display it visibly first; log applied speed/current-speed fields with timestamps.
3. Bind `VMAlertWidgetProvider`; log two alert distances, limit fields, visibility and image hashes.
4. Compare values against VietMap during a real route.
5. Test foreground/background, screen off/on, ClusterNav process restart, VietMap restart and navigation stop/resume.
6. If the widget path stays current, use it for numeric limits/alerts and Accessibility only for road/turn text.

Do not spend another experiment on BLE self-loopback.

## Independent review

A bounded senior Android/Bluetooth review using `claude-opus-4.8` agreed with the BLE verdict and ranked AppWidgetHost first, Accessibility second. It found no factual P0–P3 issue in the evidence, while explicitly noting that widget bindability and background freshness still require target-device proof.
