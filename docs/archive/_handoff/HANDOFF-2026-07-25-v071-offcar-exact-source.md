# HANDOFF — Cluster Cast `0.71` closed as OFF-CAR EXACT-SOURCE ONLY

> **Checkpoint:** 2026-07-25 · Owner: Đăng Khôi · `dangkhoi`
> **Status:** owner-approved scope implemented, reviewed clean, source identity created, security scan clean.
> **Hard stop:** no APK build, install, ADB/DADB, vehicle, commit, push, merge, reset or clean was performed, and none is authorized by this handoff.

---

## 1. What shipped into source

All five features deferred by 0.70 are implemented behind the existing single Cast control plane:

1. **Installed application icons** — launcher label/icon loaded off-main through `PackageManager` into one bounded immutable entry shared by Activity tiles, App Manager list/details/default selector and Bubble rows, with deterministic fallback that never drops or disables an eligible app.
2. **One durable default application** — envelope-owned `AutomationConfig(revision, defaultPackage, autoCastEnabled, consentVersion)` with distinct Set/Replace/Clear controls, a visibly unavailable stale default and preselection that dispatches zero Cast operations.
3. **Typed protected-package policy** — total four-class model (`NORMAL`, `SYSTEM_PROJECTION`, `USER_KEEP_SESSION`, `UNKNOWN_PROTECTED`) where system and unknown entries are locked and unknown exports no Cast or destructive action.
4. **Canonical V2 floating Bubble** — same `CastRenderModel`/action enablement as the Activity, one typed Stop with bounded local acknowledgement and no duplicate Activity Stop intent, icon+text default/favorite menu, deterministic Stop → apps → settings focus, Back/outside dismissal, clamped and persisted drag, lifecycle-generation fencing, independent presentation-only opt-in.
5. **Guarded boot auto-cast** — disabled by default, explicit versioned consent, post-unlock `BOOT_COMPLETED` only, `BOOT_COUNT` identity with one-shot rollover, durable record before host enqueue, non-exported `specialUse` foreground host with a 60 s budget, exactly one claim and one revalidation per boot, and orchestration only through the existing manual-intent owner.

---

## 2. Off-car evidence

| Item | Value |
|---|---|
| Version | `versionCode = 71`, `versionName = "0.71"` |
| Forced full JVM run | `--rerun-tasks :app:testDebugUnitTest` → 59 suites, **540 tests, 0 failures / 0 errors / 0 skips** |
| New suites | `CastAutomationTest`, `CastAutomationStoreTest`, `CastAutomationSettingsTest`, `CastAppPresentationTest`, `CastAppManagerWiringTest`, `CastBubbleProjectionTest`, `CastAutomationHostTest`, `CastUiAutomationProjectionTest` |
| Envelope schema | 2 → **3** (additive automation config/request/outcome, origin-tagged `PendingCastIntent`, v1/v2 decode as `USER`) |
| Canonical UI schema | 4 → **5**, hash `3b53e6bf62d9ad0af5922f18ac2e8ab2a3047edab7b3a259aa9378225db13ada` |
| Bounded reviews | catalog/config, Bubble/actions, automation/orchestration — all `ZERO_ACTIONABLE_FINDINGS / APPROVED` after remediation |
| LOC | every changed source/test ≤500; Activity 487, Bubble 483, host 313, App Manager 362; only pre-existing legacy `ClusterCast.kt` (1286) and `DeadReckonService.kt` (635) exceed the limit and were not modified |
| Whitespace | `git diff --check` clean |
| Exact source | `docs/_handoff/cluster-cast-v071-product-completion-exact-source.json` (records its own source ID; self-excluded to prevent recursion) |
| Security scan | 265 paths (257 text / 8 binary), 0 blocked filenames, 0 secret/credential/PII/internal-infrastructure matches; two semantic scans `CLEAN`, 0 BLOCK, 0 WARN |

Both frozen 0.70 attestations still recompute to `92e972b9…` and `43efd3c96a43…`, proving the predecessor evidence is unmodified.

---

## 3. Canonical documents amended

- `docs/specs/cluster-cast-v070-manual-cold-intent.html` — R11 narrowly superseded for an origin-tagged `BOOT_AUTO` request only (Pass 9).
- `docs/specs/cluster-cast-rebaseline.html` — new binding **D9**; the stale D8 schema prose was corrected so the artifact-derived baseline (v4 / `c7d0a589…`) is authoritative and 0.71 targets v5 (Pass 12).
- `docs/specs/clusternav-uxui-rebaseline.html` — durable default plus separate consented auto-cast enable replaces the older favorites-only radio; independent Bubble opt-in; U13/U24 updated (Pass 12).
- `docs/specs/cluster-cast-v071-product-completion.html` — reviewer log Passes 5 and 6 record implementation, the review loop and this closure.

---

## 4. Explicitly NOT STARTED

- exact APK/source/signature/version/flag provenance binding;
- exact-build behavior manifest and screenshots;
- APK build, install and device connection;
- case-30 boot auto-cast, App Manager, Bubble, accessibility and migration on-car evidence;
- physical vehicle testing and owner sign-off;
- commit, push, merge, release or support claim.

A later authorization must name this candidate's exact source ID and an isolated output path before any APK task. No `.authorized-build` path exists for it.

---

## 5. Resume points

- **Build authorization:** owner names the source ID + output path, then a single release assemble may run.
- **Publication:** a commit/push requires the mandatory staged-diff and full-tree sensitive-data scan again at that moment.
- **Vehicle:** the prepared Stage 11 / `VEHICLE-TEST-V2.md` matrix stays unexecuted until an exact-build candidate exists.
