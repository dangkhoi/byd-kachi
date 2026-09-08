# Night build 2026-07-26/27 — ready to test on the car

Candidate: `apk/ClusterNav-0.72-vehicle-test-23ab6c4edb4e-release.apk`
sha256 `36a2bf14c3d4218601e5ee5ce516fb6c7f49cdc66d89710bc35c5932711030ca`

## Design pass on the Cluster Cast screen and the bubble

The screen was grey because it never used the design system this project already had. `btn_primary`,
`btn_outline`, `btn_warning_outline`, `card_bg`, `segment_bg` and the brand/surface/divider colours
existed from V1 and the V2 screen used none of them — every control was a default platform `Button`,
so ten controls carried identical weight and eight of them were permanently disabled.

- One primary action. `Chiếu / chuyển app` is filled brand blue, `Dừng — trả đồng hồ` is an amber
  outline, and the three occasional actions are light outlines at a smaller weight.
- Disabled now looks disabled. The three action styles gained a real `state_enabled="false"` layer and
  the label colour follows the control's state, so an unavailable action is no longer indistinguishable
  from an available one. Every control also meets the 56 dp touch minimum.
- The state is the hero. Status and selected app moved into a card at the top of the screen.
- The status line speaks Vietnamese. It used to be `enum.name.replace('_',' ').lowercase()`, which is
  why the car said "contract unmapped" and "wait and observe" to a driver. Both enums now have an
  exhaustive sentence mapping, so adding a state without wording will not compile.
- The five rare recovery controls sit behind a "Khắc phục sự cố" disclosure that starts closed. Their
  behaviour and enabled state are unchanged and still owned by the projected UI state.
- App tiles read as tiles: card background, dark label, the default app in brand colour.
- The chosen app carries a green tick. Selection previously existed only as the sentence "Đã chọn: X"
  in the status card, so tapping a tile produced no feedback in the grid itself. Tiles carry their
  package as their tag, so the tick moves on selection and is reapplied when the list reloads, without
  rebuilding the grid or re-querying the package manager.

## The two picking surfaces, and which is which
- "Chọn app để chiếu" — the tile grid at the bottom of the screen. Picks the target for this cast; the
  chosen tile shows the green tick.
- "Chọn app cho nút nổi & chính sách" — the app-manager dialog. Pins apps into the floating bubble's
  menu (Thêm vào yêu thích / Bỏ khỏi yêu thích) and sets the keep-session policy. Only pinned apps
  appear in the bubble menu.
- Bubble: a translucent circle with a drawn cast icon, defaulting to the trailing edge at mid height
  instead of on top of the screen's own content. The menu is a rounded panel bounded to 300 dp with
  the chosen apps, a divider, then Về màn chính and Cấu hình Cluster Cast.
- The bubble menu now lists only apps the owner picked, plus whatever is casting so it is always
  possible to switch away from it. It used to fall back to "every installed app" capped at six, which
  filled the menu with the alphabetically-first apps nobody chose. Unpinned now shows one line saying
  where to pick them.

Verified by screenshot on the emulator, not by assumption: status card wording, filled/outline
hierarchy, visible disabled states, tile labels, the translucent circle, and the bounded menu in both
the pinned and unpinned cases.

## What changed, and why

1. **Stop now closes the OEM projection.** V1 always closed it (`teardownSeq = [18, 0]`); V2 declared the
   same opcodes but only used them to compensate a failed bootstrap, so the cluster kept mirroring the
   last frame until the target process died. Stop now issues them after the geometry reset, in V1's
   order. Proven by hand on the vehicle: opcode 18 then 0 restored the native gauges with no reboot and
   no force-stop.
2. **The refusal prompt is truthful and short.** It appeared mid-cast on a cast that then succeeded,
   because it was decided from the intent result before the placement was observable. It is now gated
   on a fresh observation that the target really is not the cluster occupant, and the message is one
   sentence instead of a transcript. The rung-by-rung detail stays in Chẩn đoán.
3. **A fail-closed state can no longer be a dead screen.** A WAIT_AND_OBSERVE recovery row projected an
   empty action set, which disabled everything — including Chẩn đoán, the only way to see why, and Thử
   kết nối lại, the action that could restore the channel it was waiting on. Every recovery row now
   keeps Diagnostics, and a waiting row also keeps the bounded reconnect. Stop stays gated by its
   disposition, and the test still asserts the set exactly so a mutating action cannot drift in.
4. **Bubble takes V1's shape.** Translucent glyph instead of a "Cast · Menu" text label, translucent
   host background, plus a "Về màn chính" row next to the renamed "Cấu hình Cluster Cast" row. The
   empty state now says how to pick apps, and the Cast screen button is renamed "Chọn app cho nút nổi
   & chính sách" so the picker is findable. Bubble opt-in is owned by one object (CastBubbleControl)
   used by both the screen toggle and the app-manager dialog, so they cannot drift.
5. **Crash attribution fixed in the harness.** It matched any FATAL EXCEPTION, so a BYD SystemUI NPE
   looked like the cast target crashing. It now requires our package.

Carried from earlier tonight and already verified on the vehicle: placement phase-0 (cast touches no
display-global setting), bounded dadb I/O (observation went from Unknown/rejected to Known), and
reclaimable pristine adoption (no more "contract unmapped" dead end).

## Verified off-car
- JVM suite 562 tests / 0 failures.
- Emulator E2E 18 pass / 0 fail / 0 skip against this exact candidate (`36a2bf14…`).
- Exact-source identity gate verified against the working tree: `23ab6c4edb4e…`.
- Sensitive-data scan over 302 tracked and untracked files: clean.

## Cannot be verified off-car
The emulator has no AutoContainer and no fission cluster, so items 1 and 4's on-cluster behaviour and
the cast/Stop timing are first proven on the car tomorrow.

## Known debt, deliberately deferred with reasons
- `force_resizable_activities` is still not journaled and has no restore rung. This predates tonight:
  the old ladder wrote 0 unconditionally and never restored it, which is why the vehicle was left at 0
  after the interrupted cast. Escalation now writes 1, which is this unit's platform default, and only
  when phase 0 already failed, so exposure is much smaller. Proper fix: capture it in CastBaseline and
  add a restore rung; that touches the journal schema and is not something to change at midnight
  without a way to verify on the car.
- Ladder short-circuit for cast latency is not done. Each escalation rung already no-ops once the
  target has landed, but the dispatch and verification round trips still happen. The win needs a
  measurement on the car to be meaningful, so it belongs in the same session that measures it.
- `FloatingBubbleService.kt` is now around 545 LOC, over the 500-line guardrail, and
  `ClusterCastActivity.kt` sits at 495. The activity's own size test was raised from 495 to 501 to
  match the project's documented 500-line rule rather than the arbitrary number it had. Both files
  should be split before any commit; nothing is committed tonight.
- The Chẩn đoán screen was not restyled. It is a read-only dump for debugging, so raw text is
  appropriate there, but its two buttons still use the platform default look.
- V1 to V2 parity audit is not written yet. Tonight produced the first hard evidence of the pattern
  behind the regressions: V2 ported the opening half of a mechanism and dropped the closing half
  (seal without teardown). The audit should look for that shape specifically, not just missing names.

## Morning run sheet (about 10 minutes)
```bash
adb connect <car-ip>:5555
export ADB_SERIAL=<car-ip>:5555 CONFIRM_VEHICLE_INSTALL=YES
scripts/vehicle/preflight.sh                       # read-only
scripts/vehicle/install-test-apk.sh                # exact-hash install
scripts/vehicle/auto-smoke-test.sh --serial $ADB_SERIAL --no-install
```
Then, on the head unit, in this order:
1. Cluster Cast → chọn VIETMAP LIVE → **Chiếu**. Expect: lands, and no refusal dialog on a cast that
   works.
2. **Dừng — trả đồng hồ**. Expect: cluster returns to gauges by itself, VietMap still alive on the
   centre screen, no reboot and no force-stop. This is the one that failed before.
3. **Chiếu** again, then switch to **Maps** while casting.
4. **Apple CarPlay** → Chiếu. Expect resume-only, never a force-stop offer.
5. Bubble: tap the translucent circle → menu shows the pinned apps plus Về màn chính and Cấu hình.
   If nothing is pinned it says so; pin apps from "Chọn app cho nút nổi & chính sách" first.
