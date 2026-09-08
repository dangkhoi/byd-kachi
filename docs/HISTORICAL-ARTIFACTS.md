# ClusterNav Historical Artifact Quarantine

> [!IMPORTANT]
> **(VI) ĐÃ ĐƯỢC THAY THẾ BỞI BẢN 1.30 · SUPERSEDED BY 1.30 (2026-08-16).** Tài liệu này là **ảnh chụp lịch sử ngày 2026-07-24** và **không còn phản ánh repo hiện tại**. Trạng thái hiện hành:
> - `README.md` + `docs/HUONG-DAN.md` **đang sống và cập nhật (1.30)** — link cài đặt/hướng dẫn **KHÔNG** bị rút; cả hai đều song ngữ VI/EN.
> - `apk/` **chỉ giữ bản release mới nhất** (`ClusterNav-1.30-release.apk`); các bản cũ (0.59–1.27) đã được cất khỏi `apk/` (lịch sử git giữ nguyên).
> - Handoff phiên làm việc + review lịch sử nay nằm dưới `docs/archive/`.
> - Bảng kiểm kê APK 0.59–0.67 bên dưới là **hồ sơ lịch sử**, không phải trạng thái `apk/` hiện tại.
>
> **(EN) SUPERSEDED BY 1.30 (2026-08-16).** This document is a **historical snapshot dated 2026-07-24** and **no longer reflects the current repo**. Current state:
> - `README.md` + `docs/HUONG-DAN.md` are **live and current (1.30)** — install links/guidance are **NOT** withdrawn; both are bilingual VI/EN.
> - `apk/` **holds only the latest release** (`ClusterNav-1.30-release.apk`); older builds (0.59–1.27) were shelved out of `apk/` (git history preserved).
> - Historical session handoffs + reviews now live under `docs/archive/`.
> - The 0.59–0.67 APK inventory below is the **historical record**, not the current `apk/` state.
>
> Everything beneath this header is kept **as-is as the historical record** (2026-07-24 inventory) — read it in that light. · Phần bên dưới được giữ **nguyên trạng làm hồ sơ lịch sử**.

> Owner: Đăng Khôi · `dangkhoi`  
> Inventory date: 2026-07-24  
> Status: **HISTORICAL / UNSUPPORTED / NOT RELEASE EVIDENCE**

No artifact below is a supported current release. Filename/version does not prove source commit, dirty diff, feature flags, toolchain, vehicle/ROM/profile, test coverage or on-car validation. Do not overwrite, relabel, publish as current, or use these bytes to close V2/UX/release gates.

## APK inventory

Read-only metadata was collected with SHA-256 plus Android SDK 34 manifest/signature inspection. All listed APKs report minSdk 29, targetSdk 34 and compileSdk 34. Release certificate subject observed: `CN=ClusterNav, OU=Mod, O=ClusterNav, L=HCM, ST=VN, C=VN`; debug subject: Android Debug. Certificate identity does not establish source provenance.

| Path | Repository state | Bytes | SHA-256 | Package / versionCode / versionName | Signing certificate SHA-256 | Classification |
|---|---|---:|---|---|---|---|
| `apk/ClusterNav-0.59-release.apk` | tracked clean | 2962369 | `69f444632a11ebfe313c3f75b1501dbf82f59371bc0a19d12a9932a5a367bb73` | `com.byd.clusternav` / 59 / `0.59` | `1d300db7d9190f72595ef7005f5f05157f009e4f7676c5a321cb69ce785ff85a` | historical/unsupported |
| `apk/ClusterNav-0.63-debug.apk` | ignored untracked | 3658588 | `14dd5eae674470153d7f4138c5ceba4599dd56e4653ead036476811d8280c05f` | `com.byd.clusternav.debug` / 63 / `0.63-debug` | `dc521dad5c8c7c79da421ccb196fd6a52df932e3c17ead8dbbe5e4b0a5a74e00` | historical/unsupported |
| `apk/ClusterNav-0.63-release.apk` | visible untracked | 2832233 | `80cc9bbac957a617d47be35e3730307333c56529132bf2ce66ff56be9395cb1d` | `com.byd.clusternav` / 63 / `0.63` | `1d300db7d9190f72595ef7005f5f05157f009e4f7676c5a321cb69ce785ff85a` | historical/unsupported |
| `apk/ClusterNav-0.64-release.apk` | visible untracked | 2833121 | `b38f4c7ce2151bf1eed48ea31bc054b6e3d4b7bad878409af433c805c21f6d04` | `com.byd.clusternav` / 64 / `0.64` | `1d300db7d9190f72595ef7005f5f05157f009e4f7676c5a321cb69ce785ff85a` | historical/unsupported |
| `apk/ClusterNav-0.65-release.apk` | visible untracked | 2833641 | `99491860ebc9b7a16e5eea8ecdfdaab3788f25253d1899c25f2d54c3ee132ea3` | `com.byd.clusternav` / 65 / `0.65` | `1d300db7d9190f72595ef7005f5f05157f009e4f7676c5a321cb69ce785ff85a` | historical/unsupported |
| `apk/ClusterNav-0.66-release.apk` | visible untracked | 2837569 | `789ccbc066fc4f84066a266649f6a8adba25ca5e25ca2a3cd2a71ded450da0b6` | `com.byd.clusternav` / 66 / `0.66` | `1d300db7d9190f72595ef7005f5f05157f009e4f7676c5a321cb69ce785ff85a` | historical/unsupported |
| `apk/ClusterNav-0.67-release.apk` | visible untracked | 2837357 | `8f5901c2c15cf513b5e64609258726ddaac11ab49a21ff66fc099d33e213f002` | `com.byd.clusternav` / 67 / `0.67` | `1d300db7d9190f72595ef7005f5f05157f009e4f7676c5a321cb69ce785ff85a` | historical/unsupported |
| `app/build/outputs/apk/release/app-release.apk` | ignored build output; byte-identical to v0.67 release | 2837357 | `8f5901c2c15cf513b5e64609258726ddaac11ab49a21ff66fc099d33e213f002` | `com.byd.clusternav` / 67 / `0.67` | `1d300db7d9190f72595ef7005f5f05157f009e4f7676c5a321cb69ce785ff85a` | historical/unsupported |

`apk/ClusterNav-0.67-release.apk` and `app/build/outputs/apk/release/app-release.apk` are byte-identical. This is historical duplication, not proof that the current dirty source produced them.

### Known unknowns for every APK

- Exact branch/HEAD and full tracked-diff bytes.
- Intended untracked source inputs.
- Active feature/engine/UI flags and durable schema.
- Complete toolchain/config/signing-key provenance.
- Install history and exact vehicle/ROM/profile.
- Direct current-source off-car evidence and exact-build on-car evidence.
- Whether a file was manually copied or renamed.

The historical Gradle collection task uses a version-only destination and can overwrite `apk/ClusterNav-<version>-release.apk`; a unique path written only in a token is not sufficient. Stage 2 must first replace the unconditional assemble finalizer with an explicit collision-failing collector that requires slice + exactSourceId, selects only the newly built requested variant, writes `apk/ClusterNav-<version>-<slice>-<exactSourceId12>-release.apk`, and aborts if the destination exists. Every `BUILD_AUTH_*` remains blocked until that guard passes static/configuration review. Existing APK bytes remain denied and immutable.

## Screenshot inventory

These images are immutable historical UI references. They were captured from emulator/demo material and do not prove current source or vehicle behavior.

| Path | Bytes | SHA-256 | Historical label |
|---|---:|---|---|
| `docs/images/cai-dat-chieu.png` | 126401 | `b703840c9af5e6fabbca70bb4daacc81d0a94451649679bed622cca9a210c20a` | legacy Cast setup/T1-T3 surface; target-invalid |
| `docs/images/chinh-scale.png` | 101018 | `952a640b9737241d79e8e1e859805c651c909e6d6fdc62fc60caa764e75054b8` | legacy inline scale controls; target-invalid |
| `docs/images/man-hinh-chinh.png` | 103979 | `9861be1b50fcdcb1ca67a657baec8a765daf9831f41dae2b6f4465bcd2a39f6b` | legacy mixed dashboard; not two-card target UX |
| `docs/images/nav-card.png` | 34366 | `5b34d8a41916c03dfee4313eca21b086e568e4c5eb386c64d4b12a31e659169c` | legacy/emulator Nav card; not exact-build car evidence |
| `docs/images/nut-noi.png` | 1755122 | `6dd4420aad774c417465f76c697d0f8aa84905e79a9485d3e0f63553739c53c3` | legacy toggle/long-press Bubble; target-invalid |

## Public-document quarantine

- `README.md`: current NO-GO/two-track landing page; installation links withdrawn.
- `docs/HUONG-DAN.md`: archived historical screenshot guide; operational T1/T3/DR/install guidance withdrawn.
- `docs/diagnostics/CARTEST.md`: archived checklist; do not execute. DR C1–C4 remain NOT STARTED.
- `docs/reference/dashcast-projection-recipe.md`: archived research recipe; not a current safety or implementation contract.
- `docs/review/**`, older `docs/specs/**`, remaining `docs/diagnostics/**` and `docs/reference/**`: dated historical context unless a current approved spec explicitly promotes an item.

## Current release gate

A future public APK/link requires all of:

1. Approved `EXACT_SOURCE` identity.
2. A verified Stage 2 collision-failing collector, followed by separate build authorization naming its unique output path.
3. APK SHA-256, signing certificate, version and exact flags bound to that source.
4. Exact-build off-car evidence.
5. Exact-build on-car PASS on an approved vehicle/ROM/profile and case set.
6. Public docs/screenshots updated atomically to that exact build.
7. Mandatory sensitive-data scan before any commit/push.
8. Explicit commit/push/merge authorization; no `main` merge before car PASS.
