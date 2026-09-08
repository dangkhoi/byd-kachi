# Stage `rawnotif` — DONE

RAW notification capture: a diagnostic drive now records **every** notification from the five nav
packages (not just the nav-parsed ones), tagged by package, so post-drive analysis can empirically
see per-app notif-channel yield — without touching the cluster feed or the existing parsed log.

- **Working dir:** `byd-cluster-2-wt-speed-limit-badge-hal-hud`
- **Branch:** `feat/speed-limit-badge-hal-hud`
- **Gate:** `./gradlew :app:testDebugUnitTest --console=plain` → **BUILD SUCCESSFUL** (JDK 17)

## Files changed

| File | Location | What |
|------|----------|------|
| `app/src/main/java/com/byd/clusternav/NavNotifRawLog.kt` | **new**, 121 LOC | New object mirroring `NavNotifLog`: verbose-gated, own single-thread daemon Executor (`navnotifrawlog`), writes `nav_notif_raw_<ts>.csv`, degrade-safe, pure `buildRow` for off-car testing. |
| `app/src/main/java/com/byd/clusternav/NavNotificationListener.kt` | `:76` (field), `:290–310` (capture block) | Added `private val lastRaw = HashMap<String,String>()` collapse map + the RAW-capture block in `handle()`. |
| `app/src/test/java/com/byd/clusternav/NavNotifRawLogTest.kt` | **new**, 90 LOC | 4 pure JUnit-5 tests: header arity, header names/order, comma+quote escaping, newline stripping. |

### `NavNotifRawLog.kt` key line references
- `:37` — `const val HEADER = "t_ms,pkg,category,isNav,hasDist,hasLargeIcon,title,text,subText,bigText"`
- `:33–35` — own daemon Executor `navnotifrawlog` (lazy → created only when verbose turns on)
- `:39–47` — `ensureLocked` opens `getExternalFilesDir(null)/nav_notif_raw_<ts>.csv` (= `/sdcard/Android/data/com.byd.clusternav2/files/…`), wrapped in `runCatching`
- `:54–71` — pure `buildRow(...)` (10 fields, no Android/no I/O)
- `:73–74` — `oneLine()` newline strip
- `:80–97` — `record(...)`: `if (!NavLog.verbose) return`, timestamp on caller thread, work on Executor
- `:99–121` — `recordLocked(...)`: `ensureLocked` → `buildRow` → `appendLine` + `flush`, all degrade-safe

### `NavNotificationListener.handle()` wiring (`:296`)
Inserted immediately **after** `if (title.isEmpty() && text.isEmpty()) return` — i.e. **before** the
`NavArrivalGuard.isArrivalText(...)` arrival guard **and before** the `if (!isNav && !hasDist) return`
drop — so ALL notifs are captured:

```kotlin
if (NavLog.verbose) {
    val rawKey = "$title\u0001$text\u0001$sub\u0001$big"
    if (lastRaw[sbn.packageName] != rawKey) {
        lastRaw[sbn.packageName] = rawKey
        runCatching {
            NavNotifRawLog.record(
                applicationContext, sbn.packageName, n.category ?: "",
                n.category == Notification.CATEGORY_NAVIGATION,
                DIST_TOKEN.containsMatchIn(title) || DIST_TOKEN.containsMatchIn(text),
                n.getLargeIcon() != null, title, text, sub, big,
            )
        }.onFailure { Log.w(TAG, "raw notif log failed", it) }
    }
}
```

The existing `NavNotifLog.record(...)` call and all cluster-feed logic are **unchanged**. The later
`val isNav`/`val hasDist` (`:328–329`) were left intact — the capture block uses inline expressions,
not `val`, so there is no redeclaration collision.

## New CSV — `nav_notif_raw_<ts>.csv`

Header (10 columns, exact): `t_ms,pkg,category,isNav,hasDist,hasLargeIcon,title,text,subText,bigText`

| col | meaning |
|-----|---------|
| `t_ms` | `System.currentTimeMillis()` captured on the listener thread |
| `pkg` | `sbn.packageName` — the source app (one of the five nav packages) |
| `category` | `notification.category ?: ""` (e.g. `navigation`, or empty for status notifs) |
| `isNav` | `category == Notification.CATEGORY_NAVIGATION` |
| `hasDist` | distance token (`\d+(m\|km\|ft\|mi)`) found in title/text |
| `hasLargeIcon` | `notification.getLargeIcon() != null` (maneuver arrow presence) |
| `title` / `text` / `subText` / `bigText` | raw notification extras |

`isNav` + `hasDist` are exactly the two predicates the parsed path uses to DROP non-nav notifs; logging
them here lets post-drive analysis see which apps post status-only notifs (`isNav=false,hasDist=false`)
that `NavNotifLog` never records — e.g. Waze "Waze is running", VietMap "Ứng dụng đang chạy", WazeMod status.

Fields are newline-stripped (CR/LF/CRLF → single space) then CSV-escaped via the shared `:core`
`CsvEscape` (wrap in `"…"`, double internal `"`), so a quoted comma/ETA never breaks column alignment
and each record stays on one physical line. Pull:
`adb pull /sdcard/Android/data/com.byd.clusternav2/files/nav_notif_raw_*.csv`

## How the collapse works

- `lastRaw: HashMap<String,String>` keyed by **package**, value = `title\u0001text\u0001sub\u0001big`.
- On each notif: if the composed value equals `lastRaw[pkg]`, the row is **skipped**; otherwise the map
  is updated and the row is recorded. This kills GMaps' ~1/s identical redraws while still capturing
  every genuine content change per app.
- Touched **only** on the listener callback thread (`onNotificationPosted` + the `onListenerConnected`
  `activeNotifications` scan both run on the main looper), so a plain `HashMap` with no lock is safe.
- Bounded to the five nav packages (≤5 entries) → no unbounded growth. Not reset at session boundaries
  (it never feeds the cluster; purely a flood guard).

## Isolation / safety

- Purely diagnostic + **additive**: never touches `SourceArbiter`, `ClusterBroadcaster`, `NavRepository`,
  `ClusterNavLaneWidget`, or nav state. All work is verbose-gated (default OFF), off-main, degrade-safe.
- Constraints honored: no `keystore.properties` / `release.keystore` / `local.properties` touched; no
  git commit/push; no APK build; `main` untouched. All files ≤ 500 LOC
  (`NavNotificationListener.kt`=435, `NavNotifRawLog.kt`=121, `NavNotifRawLogTest.kt`=90).

## Test counts

- `NavNotifRawLogTest`: **4 tests, 0 failures, 0 errors**.
- Full app unit suite (`:app:testDebugUnitTest`): **392 tests, 0 failures, 0 errors, 0 skipped** → GREEN.
