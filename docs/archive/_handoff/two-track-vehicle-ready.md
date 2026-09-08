# ClusterNav Two-Track Vehicle-Ready Handoff

Owner: **Đăng Khôi · `dangkhoi`**  
State: **INVALIDATED_BY_SENIOR_REVIEW — DO NOT INSTALL OR TEST**  
Vehicle install/test/sign-off: **BLOCKED PENDING PATCH VALIDATION + NEW BUILD AUTHORIZATION**  
Stage 12 legacy cleanup: **DEFERRED POST-SOAK**

> **Invalidation notice:** Senior source review found blocking P0/P1 issues. Exact source `d808db00313c9ca6ae5ddd88068859cbe8719af1d853d4886d861b971362093a` and APK SHA-256 `1b9c016273296454c9fd0ac88bb51dd8c7447b8b7d60b113d689eb7eb9d6b184` are stale and prohibited for vehicle installation/testing. The 1/1 build authorization remains consumed; a replacement build requires separate owner authorization after patches and validation.

## Exact source

- Branch: `release/v0.60-cast-hardening`
- HEAD: `fd4890c1ffabf4b8cb37f5ccbd5cdb93f0343ae6`
- Exact source ID: `d808db00313c9ca6ae5ddd88068859cbe8719af1d853d4886d861b971362093a`
- Manifest: `docs/_handoff/two-track-final-exact-source.json`
- Tracked diff: 265,930 bytes
- Tracked diff SHA-256: `2488bd40d1c61933a60f7200f9f45ba5c226bb269a14d155a6a2bd254869f8e2`
- Intended untracked inputs: 86
- Identity exclusions: 12; generated final manifest/handoff are path-only self exclusions.

## Exact build

- Authorized invocation count: **1 of 1 consumed**
- Variant: `release`
- Slice: `vehicle-test`
- Flags: Cast UI V2 ON; normal V2 ON; KEEP_SESSION V2 ON; projection-sink V2 ON; Navigation UI/runtime independent.
- APK: `apk/ClusterNav-0.67-vehicle-test-d808db00313c-release.apk`
- Bytes: `3,095,497`
- APK SHA-256: `1b9c016273296454c9fd0ac88bb51dd8c7447b8b7d60b113d689eb7eb9d6b184`
- Package: `com.byd.clusternav`
- versionCode/versionName: `67` / `0.67`
- compile/min/target SDK: `34` / `29` / `34`
- Signing: APK Signature Scheme v2 verified
- Certificate DN: `CN=ClusterNav, OU=Mod, O=ClusterNav, L=HCM, ST=VN, C=VN`
- Certificate SHA-256: `1d300db7d9190f72595ef7005f5f05157f009e4f7676c5a321cb69ce785ff85a`
- Toolchain: OpenJDK `17.0.19`; Gradle `8.7`; Kotlin `1.9.22`; Groovy `3.0.17`; Android SDK/build-tools `34`.

The authorized build used `.authorized-build/d808db00313c/app`, so it did not overwrite historical `app/build` bytes. Exactly one candidate matching `ClusterNav-*-vehicle-test-*-release.apk` exists.

## Off-car gates

- Full JVM suite on final source: **301 tests, 0 failures, 0 errors, 0 skipped** across 43 reports. The earlier review snapshot recorded 300; the final source adds one isolated authorized-build-directory regression test.
- Focused Navigation/Cast/Home/retirement/build-artifact suites: PASS.
- Release compile/resources/package/signing: PASS; 43 build tasks executed.
- Static boundaries: PASS — target files ≤500 LOC; no cross-track control imports; no V2 `am display move-stack`; no active legacy Cast mutation routes; no V2 `catch(Throwable)`.
- Historical APK immutability: PASS. Both historical `app/build/outputs/apk/release/app-release.apk` and `apk/ClusterNav-0.67-release.apk` remain SHA-256 `8f5901c2c15cf513b5e64609258726ddaac11ab49a21ff66fc099d33e213f002`.
- Sensitive-data scan: 209 candidate text files, 0 findings.
- Vehicle scripts: syntax PASS; none executed.
- Android lint: not executed to completion because offline cache lacks pinned `com.android.tools.lint:lint-gradle:31.5.2`. This is recorded as an environment/cache limitation; compile/resources/full JVM suite are green.

## Vehicle execution kit

1. `docs/diagnostics/VEHICLE-TEST-V2.md`
2. `scripts/vehicle/preflight.sh`
3. `scripts/vehicle/install-test-apk.sh`
4. `scripts/vehicle/run-navigation-matrix.sh`
5. `scripts/vehicle/run-cast-matrix.sh`
6. `scripts/vehicle/capture-evidence.sh`
7. Shared helper: `scripts/vehicle/common.sh`

Required environment:

> **DO NOT USE THE BLOCK BELOW.** It is preserved as a record of the invalidated 0.67 pair only.
> That APK SHA is blocklisted in `scripts/vehicle/common.sh` and installing it is prohibited. The
> current candidate is resolved automatically from `docs/_handoff/vehicle-candidate.json`; no
> `APK`/`EXPECTED_SHA256` export is needed.

```bash
export APK="apk/ClusterNav-0.67-vehicle-test-d808db00313c-release.apk"
export EXPECTED_SHA256="1b9c016273296454c9fd0ac88bb51dd8c7447b8b7d60b113d689eb7eb9d6b184"
```

Run `preflight.sh` first. Install requires explicit `CONFIRM_VEHICLE_INSTALL=YES`. No script has been run against a vehicle in this session.

## Stage 11 mandatory evidence

- Record approved vehicle model, ROM fingerprint, profile, operator and test window.
- Install only the exact APK SHA above.
- Execute independent Navigation lane/HUD toggles and whole-session Stop.
- Execute normal cold/warm Cast, CarPlay, Android Auto, protected pairwise/residue and unknown-effect recovery cases.
- Execute sleep/wake and **physical power-button reboot**. `adb reboot` is not accepted.
- Capture local evidence under ignored `oncar-v2-*`; review/redact before sharing.
- Owner signs off only after every required matrix row passes.

## Prohibitions

Do not rebuild, rename/substitute the APK, install any historical APK, run vehicle tests before the car/profile window is approved, commit, push, merge, or delete legacy Cast source under this handoff. Any source change invalidates this exact-source/exact-build pair and requires new authorization.
