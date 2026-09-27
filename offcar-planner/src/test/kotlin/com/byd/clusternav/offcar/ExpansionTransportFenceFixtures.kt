package com.byd.clusternav.offcar

/**
 * ═══ Hằng + bảng băm của [ExpansionTransportFenceTest] — tách THUẦN khỏi `companion object` (906 dòng → trần 500) ═══
 *
 * L6-debt 2026-09-27: nội dung giữ nguyên byte (kể cả nhật ký 27 lần cập nhật hằng `T11_HASHES` — nó là vết duyệt của owner, không
 * phải chú thích trang trí). Chỉ đổi `private` → `internal` để lớp test nhập từng tên (`import …Fixtures.<tên>`); không tên mới, không
 * luật mới. Mọi bài canh vẫn nằm trong `ExpansionTransportFenceTest` để cổng `GATE-X-O10` của verifier chạy đúng lớp đó.
 *
 * ⚠ Tệp này KHÔNG có trong `SOURCE_SEAL_INPUT` của `offcar-boundary-revisions.json` (155 đường dẫn, niêm phong theo revision) —
 * đưa vào là một revision mới của authority, việc của owner; ghi ở backlog L6.
 */
internal object ExpansionTransportFenceFixtures {
    internal const val SPEC_PATH = "docs/specs/seal-hud-sign-candidate-expansion.html"
    internal const val OUTPUT_DIRECTORY = "docs/diagnostics/hud-sign-re/expansion"
    internal const val VERIFIER_PATH = "scripts/verify-hud-sign-candidate-expansion.sh"

    internal val IMPLEMENTATION_SOURCES = listOf(
        "offcar-planner/src/main/kotlin/com/byd/clusternav/offcar/ExpansionRegistry.kt",
        "offcar-planner/src/main/kotlin/com/byd/clusternav/offcar/DiscoveryProbe.kt",
        "offcar-planner/src/main/kotlin/com/byd/clusternav/offcar/ExpansionPackRenderer.kt",
        "offcar-planner/src/main/kotlin/com/byd/clusternav/offcar/ExpansionMain.kt",
    )
    internal val SOURCE_ARTIFACT_PATHS = IMPLEMENTATION_SOURCES + listOf(
        "offcar-planner/src/main/resources/expansion-contracts.schema.json",
        "scripts/re/expand-candidate-coverage.py",
        VERIFIER_PATH,
    )
    internal val CURRENT_PATHS = IMPLEMENTATION_SOURCES + listOf(
        "offcar-planner/src/main/resources/expansion-contracts.schema.json",
        "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/LegacyBaselineIdentityTest.kt",
        "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionPromotionTest.kt",
        "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/SameSessionQuarantineTest.kt",
        "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/AdaptivePruningTest.kt",
        "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionDeterminismTest.kt",
        "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionTransportFenceTest.kt",
        "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/DerivationClosureTest.kt",
        "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionTraceabilityTest.kt",
        "scripts/re/expand-candidate-coverage.py", "scripts/re/tests/test_expand_candidate_coverage.py", VERIFIER_PATH,
        "$OUTPUT_DIRECTORY/legacy-baseline.json", "$OUTPUT_DIRECTORY/candidate-registry.json",
        "$OUTPUT_DIRECTORY/evidence-map.json", "$OUTPUT_DIRECTORY/candidate-diff.json",
        "$OUTPUT_DIRECTORY/candidate-expansion-report.html", "$OUTPUT_DIRECTORY/vehicle-session-plan.json",
        "$OUTPUT_DIRECTORY/vehicle-session-plan.txt", "$OUTPUT_DIRECTORY/vehicle-session-checklist.html",
        "$OUTPUT_DIRECTORY/result-ledger.schema.json", "$OUTPUT_DIRECTORY/pack-manifest.json",
        "$OUTPUT_DIRECTORY/traceability.json", "$OUTPUT_DIRECTORY/corpus-coverage.json", SPEC_PATH,
    )
    internal const val BOUNDARY_PATH = "docs/diagnostics/hud-sign-re/offcar-boundary-revisions.json"
    internal const val REVISION_ONE_SHA256 = "a05be7e4d6a521a81a285321754e8370dec4402e4406f2969727cd3863c46301"
    internal const val HISTORICAL_PARENT_BASELINE = "8f636f508aaf89592ca676d85a8d13dbb8c7e9225112957d281dc1368901e1d4"
    internal val PARENT_ARTIFACT_HASHES = linkedMapOf(
        "docs/diagnostics/hud-sign-re/README.md" to "f854683acbda36c69f899a5d3a3bdc326c0df966dc49b24059eb2d94ccb1ee46",
        "docs/diagnostics/hud-sign-re/candidate-report.html" to "af3c3db29e29b5c4cd4ebd0a0d4ef863de5a7a2d7f60c527bbe989596ce3eb36",
        "docs/diagnostics/hud-sign-re/corpus-completeness.json" to "87610a0e7a54e7d634dbcad8a423906a494e2add2e6e50b61828e1ee7217db79",
        "docs/diagnostics/hud-sign-re/evidence-index.json" to "ac3fd27701e6b05c5037594b35d314b49ddadaeb0315d8a64c3a3da0bef980b9",
        "docs/diagnostics/hud-sign-re/first-launch-emulator-result.json" to "e60e63dced72dbc0d742476883930088385c5812b29d176f145f42430540ae39",
        "docs/diagnostics/hud-sign-re/m1-nav-hud-plan.json" to "c8eff7210e093fa1b2e32328abeddf37fa51d919d524df851e4ecd634d289bf9",
        "docs/diagnostics/hud-sign-re/m2-hud-road-plan.json" to "dffb04d3e26beadd9fd7f813870fca6712d5f213f4145787280ddb4718437faf",
        "docs/diagnostics/hud-sign-re/m3-cluster-sign-plan.json" to "ac2de2631de73ab2ddd2ad4efa2f33e805fae2d8007091e2ae3497c64625fb7e",
        "docs/diagnostics/hud-sign-re/m4-hud-sign-plan.json" to "51fc71db1050baa816e9df2e1250a196c1d6e608a48df228e5318d6077c910d1",
        "docs/diagnostics/hud-sign-re/native/libbydcluster-diff.json" to "d2d7f63ee1916905e8ef21ea58242f22f4e2c02a5a1e0b3854766341f77e9464",
        "docs/diagnostics/hud-sign-re/traceability.json" to "332ae311ed642441c7ec8640fc4c389e1e7865813eafef8b1442645ff3791e60",
        "docs/diagnostics/hud-sign-re/zero-hit-report.txt" to "55acd8bf51a0baf9f397b8765162f063fc1686d9544b3fdbc83eaa96955f273f",
        "docs/specs/seal-nav-hud-speed-sign-offcar.html" to "781ff2b47f38d51deec66a47464ab78f37d781970499fdc64b86386423a28f87",
    )
    internal fun pathSet(raw: String) = raw.split('|').toSet()
    internal val T10_SOURCE_PATHS = pathSet(".gitignore|app/build.gradle.kts|app/src/test/java/com/byd/clusternav/BuildArtifactNamingTest.kt|app/src/test/java/com/byd/clusternav/MainProbeSurfaceAbsenceTest.kt|app/src/testVehicleTest/java/com/byd/clusternav/VehicleTestSurfaceContractTest.kt|app/src/vehicleTest/AndroidManifest.xml|app/src/vehicleTest/java/com/byd/clusternav/HudSignProbeActivity.kt|app/src/vehicleTest/java/com/byd/clusternav/HudSignProbeReceiver.kt|car-integration/build.gradle.kts|car-integration/src/main/kotlin/com/byd/clusternav/vehicleprobe/DadbVehicleTransport.kt|car-integration/src/main/kotlin/com/byd/clusternav/vehicleprobe/HudSignSessionRunner.kt|car-integration/src/main/kotlin/com/byd/clusternav/vehicleprobe/T10LocalAuthorization.kt|car-integration/src/main/kotlin/com/byd/clusternav/vehicleprobe/T10ResultStore.kt|car-integration/src/main/kotlin/com/byd/clusternav/vehicleprobe/T10RunnerMain.kt|car-integration/src/test/kotlin/com/byd/clusternav/vehicleprobe/DadbVehicleTransportTest.kt|car-integration/src/test/kotlin/com/byd/clusternav/vehicleprobe/HudSignSessionRunnerTest.kt|core/src/main/kotlin/com/byd/clusternav/carexec/CarExecCatalog.kt|core/src/main/kotlin/com/byd/clusternav/carexec/CarExecClusterDiagnosticsCatalog.kt|core/src/main/kotlin/com/byd/clusternav/carexec/CarExecClusterLifecycleCatalog.kt|core/src/main/kotlin/com/byd/clusternav/carexec/CarExecClusterProjectionCatalog.kt|core/src/main/kotlin/com/byd/clusternav/carexec/CarExecHudCatalog.kt|core/src/main/kotlin/com/byd/clusternav/carexec/CarExecModels.kt|core/src/main/kotlin/com/byd/clusternav/carexec/CarExecNavigationCatalog.kt|core/src/main/kotlin/com/byd/clusternav/carexec/CarExecSpeedSignCatalog.kt|core/src/main/kotlin/com/byd/clusternav/carexec/T10FixedOperationCatalog.kt|core/src/main/kotlin/com/byd/clusternav/carexec/T10RollbackExecutor.kt|core/src/main/kotlin/com/byd/clusternav/carexec/T10SessionEngine.kt|core/src/test/kotlin/com/byd/clusternav/carexec/CarExecCatalogTest.kt|core/src/test/kotlin/com/byd/clusternav/carexec/T10SessionSafetyTest.kt|docs/_handoff/session-2026-08-10-hud-sign-t10-offcar-complete.md|docs/_handoff/session-2026-08-10-hud-sign-t10-preparation.md|gradle/authorized-apk-tasks.gradle.kts|gradle/exact-source-tasks.gradle.kts|scripts/evidence/gen-exact-source.py|scripts/evidence/tests/test_hud_sign_t10_evidence.py|scripts/evidence/verify-hud-sign-t10.py|scripts/vehicle/run-seal-hud-sign-matrix.sh|scripts/verify-seal-hud-sign-vehicle-test-t10.sh|vehicle-contracts/src/main/kotlin/com/byd/clusternav/vehicle/t10/T10Canonical.kt|vehicle-contracts/src/main/kotlin/com/byd/clusternav/vehicle/t10/T10Identity.kt|vehicle-contracts/src/main/kotlin/com/byd/clusternav/vehicle/t10/T10Ledger.kt|vehicle-contracts/src/main/kotlin/com/byd/clusternav/vehicle/t10/T10Session.kt|vehicle-contracts/src/main/kotlin/com/byd/clusternav/vehicle/t10/T10Transport.kt|vehicle-contracts/src/main/resources/t10-contracts.schema.json|vehicle-contracts/src/test/kotlin/com/byd/clusternav/vehicle/t10/T10ContractsTest.kt")
    internal val POST_BUILD_PATHS = pathSet("docs/_handoff/hud-sign-t10-exact-source.json|docs/_handoff/hud-sign-vehicle-test-candidate.json|docs/diagnostics/hud-sign-re/vehicle/d-h0-hud-physical-temp-result.json|docs/diagnostics/hud-sign-re/vehicle/d-m1-nav-hud-result.json|docs/diagnostics/hud-sign-re/vehicle/d-m2-hud-road-result.json|docs/diagnostics/hud-sign-re/vehicle/d-m3-cluster-sign-result.json|docs/diagnostics/hud-sign-re/vehicle/d-m4-hud-sign-result.json")
    internal val T11_PATHS = pathSet("app/src/main/java/com/byd/clusternav/vehicle/BydPropertyGateway.kt|app/src/main/java/com/byd/clusternav/vehicle/ClusterSpeedSignPort.kt|app/src/main/java/com/byd/clusternav/vehicle/HudSignSettingsController.kt|app/src/main/java/com/byd/clusternav/vehicle/HudSpeedSignPort.kt|app/src/main/java/com/byd/clusternav/vehicle/HudVehicleProfile.kt|app/src/main/java/com/byd/clusternav/vehicle/SpeedSignVehicleProfile.kt|app/src/main/res/layout/activity_main.xml|app/src/main/res/values/strings.xml|docs/_handoff/hud-sign-release-candidate.json|docs/diagnostics/hud-sign-re/vehicle/p-m1-nav-hud-result.json|docs/diagnostics/hud-sign-re/vehicle/p-m2-hud-road-result.json|docs/diagnostics/hud-sign-re/vehicle/p-m3-cluster-sign-result.json|docs/diagnostics/hud-sign-re/vehicle/p-m4-hud-sign-result.json")
    /**
     * ⚠ CẬP NHẬT 2026-08-24 — owner duyệt, KHÔNG phải sửa lén.
     *
     * `activity_main.xml` đổi vì gỡ ô chọn "Nguồn tốc độ" khỏi mục biển-báo-tốc-độ-trên-cụm: sau khi
     * B3.30 gỡ hẳn kênh Waze Mod (HLP) — đo thật, `logcat -s WazeHudLink` 0 dòng khi Waze đang dẫn, không
     * HUD BLE — ô đó chỉ còn ĐÚNG MỘT mục (widget VietMap), tức một nút bấm-không-làm-gì.
     * Owner chốt 08-22: *"cái nào work thì để, không thì remove hẳn, cả code + UI để khỏi nhầm"*
     * và xác nhận lại 08-24: *"bỏ là đúng, chỉ có vietmap, và không phải chọn gì nữa"*.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 4738ceb6…4946ec.
     * Chức năng KHÔNG mất: badge vẫn lấy từ widget VietMap, dòng trạng thái `txt_speed_source_bind` giữ nguyên.
     *
     * ⚠ CẬP NHẬT 2026-08-24 (lần 2) — owner duyệt, KHÔNG phải sửa lén.
     *
     * `activity_main.xml` đổi lần nữa vì **F3 — gán NHIỀU phím cho NHIỀU app**. Owner yêu cầu nguyên văn:
     * *"có thể binding nhiều nút vào nhiều app được không? … nên có giao diện kiểu sau khi chọn nút +
     * chọn app xong → add, thì ra 1 dòng đã binding nút và app, xong có thể chọn thêm add thêm, mình
     * listen thì listen theo cái danh sách đã save đó thôi"*.
     * Thay đổi trong mục "Nút vật lý → mở app": thêm `btn_voicekey_add` (Thêm gán), `list_voicekey_bindings`
     * (nơi bơm từng dòng đã gán) và `txt_voicekey_empty` (nói rõ danh sách rỗng ⇒ KHÔNG có gì chạy);
     * đánh số lại nhãn hai dropdown sẵn có thành "1 · Chọn nút" / "2 · Chọn app sẽ mở".
     * Dòng đã gán nằm ở layout MỚI `row_voicekey_binding.xml` (không thuộc danh sách canh này).
     * `strings.xml` KHÔNG đổi — nhãn đặt lúc chạy qua `Lang.t` để giữ song ngữ, đúng lối đang dùng.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = fddc1ef5…a694d.
     *
     * ⚠ CẬP NHẬT 2026-08-25 (lần 3) — owner duyệt ("làm maximum có thể, không cần hỏi"), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi lần nữa vì **B3.20 — chip cảnh báo/camera VietMap trên cụm**. Thêm MỘT toggle
     * `switch_alert_chip` ("Hiện cảnh báo/camera VietMap", **mặc định TẮT** — opt-in, không phá bố trí badge
     * hiện có) ngay dưới `switch_upcoming_badge`, ở CẢ hai biến thể layout (portrait + `layout-w960dp` xe
     * render — bài học F3 P0). Chip đọc VietMap sticky ALERTS slot → `RoadAlertChipDecision` (:core) →
     * `AlertChipView` (cửa sổ overlay thứ 3, đặt PHẢI badge chính). `strings.xml` KHÔNG đổi (nhãn đặt lúc chạy).
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 25fa8836…f202.
     *
     * ⚠ CẬP NHẬT 2026-08-25 (lần 4) — owner duyệt ("sửa hết luôn đi"), KHÔNG sửa lén.
     *
     * `strings.xml` đổi vì **B3.57 — status per-nguồn** (owner báo on-car: status "Chưa có phiên dẫn đường"
     * chỉ đúng cho GMaps, không phản ánh khi đọc VietMap/Waze). Sửa `status_need_perm`: "Cần cấp quyền
     * notification trước" → "Cần quyền truy cập thông báo để đọc dẫn đường" (nói rõ quyền để ĐỌC dẫn đường,
     * KHÔNG ngụ ý notification là đường duy nhất). Nhãn nguồn per-kênh (GMaps=thông báo · VietMap/Waze=đọc
     * màn hình) đặt lúc chạy qua `NavSourceLabels` (:core) + `Lang.t`, KHÔNG vào strings.xml.
     *
     * Hằng cũ (giữ lại để trace): strings.xml = 4b068200…6fa1.
     *
     * ⚠ CẬP NHẬT 2026-08-28 (lần 5) — owner duyệt (task ui-closing-cleanup), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **closing UI cleanup** (sau khi gỡ nav VietMap/Waze — chỉ còn Google Maps):
     *   1) GỠ selector "chọn nguồn dẫn đường" (`spinner_nav_source` + nhãn) — chỉ còn một nguồn nên không
     *      cần chọn; `Prefs.sourceMode` giữ mặc định AUTO. Dòng trạng thái `txt_nav_source_active` GIỮ.
     *   3) THÊM panel "Vị trí bong bóng VietMap trên cụm" (`btn_vm_pos_left/right/up/down/right_half/apply`
     *      + `txt_vm_pos_hint`) ở CẢ hai biến thể layout (portrait + `layout-w960dp`), gate theo Cluster Cast.
     * `strings.xml` KHÔNG đổi (nhãn đặt lúc chạy qua `Lang.t`), nên hằng strings.xml giữ nguyên.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 20dca831…4412.
     *
     * ⚠ CẬP NHẬT 2026-08-28 (lần 6) — owner duyệt (task diag-remove-and-placement-ui), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **UI vị trí bong bóng VietMap chuyển sang KÉO-THẢ**: gỡ 4 nút mũi tên
     * `btn_vm_pos_{left,right,up,down}` (owner chê), thay bằng khung proxy cụm kéo-thả
     * `vm_bubble_placement_container` (VmBubblePlacementView — giống UI đặt biển báo tốc độ) + thêm nút
     * `btn_vm_pos_reset` ("Đặt lại"); giữ `btn_vm_pos_right_half` + `btn_vm_pos_apply` + `txt_vm_pos_hint`.
     * Đổi ở CẢ hai biến thể layout (LayoutVariantIdParityTest giữ parity id). `strings.xml` KHÔNG đổi
     * (nhãn đặt lúc chạy qua `Lang.t`), nên hằng strings.xml giữ nguyên.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = f13eeda5…d39f7.
     *
     * ⚠ CẬP NHẬT 2026-08-28 (lần 7) — owner duyệt (task move-vm-block-up), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **chuyển khối UI "Vị trí bong bóng VietMap trên cụm" LÊN TRÊN mục
     * "Khắc phục sự cố"** (`cast_recovery_toggle`): khối vm (comment + `vm_bubble_placement_container` +
     * `btn_vm_pos_right_half` / `btn_vm_pos_reset` / `btn_vm_pos_apply` + `txt_vm_pos_hint`) trước nằm DƯỚI
     * card Cast, nay DÁN ngay TRƯỚC `cast_recovery_toggle` (trong `cast_body`). CHỈ DI CHUYỂN vị trí + thụt
     * lề cho khớp ngữ cảnh chèn — KHÔNG đổi id/nội dung (số id bất biến, LayoutVariantIdParityTest xanh).
     * Đổi ở CẢ hai biến thể layout; chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc.
     * `strings.xml` KHÔNG đổi.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 5744260b…81da0a.
     *
     * ⚠ CẬP NHẬT 2026-08-28 (lần 8) — owner duyệt (task remove-diag-logging-toggle), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **gỡ công tắc "Thu thập dữ liệu chẩn đoán (log + ảnh)"**
     * (`switch_diag_logging` + comment kèm theo) khỏi CẢ hai biến thể layout. Công tắc này đã vô nghĩa:
     * nguồn dữ liệu nó thu (screen-capture/log dẫn đường VietMap/Waze) đã bị gỡ — chỉ còn Google Maps.
     * Wiring MainActivity (`setDiagLogging` + nhấn-giữ ẩn trên nhãn phiên bản), `NavLogExport`, receiver
     * `EXPORT_LOGS` (NavAccessibilityService) và `Prefs.setNavVerboseLog` gỡ theo vì thành orphan.
     * GIỮ log chẩn đoán GMaps hợp lệ: `NavLog.verbose` + `Prefs.navVerboseLog` getter (nay chỉ do cờ build
     * `-PdiagLog=true`/`BuildConfig.DIAG_LOG` điều khiển) + `NavNotifLog`/`NavNotifRawLog`/`DiagStorageCap`.
     * `strings.xml` KHÔNG đổi (text công tắc hardcode trong layout, không phải `@string`) nên hằng
     * strings.xml giữ nguyên. Đổi ở CẢ hai biến thể (LayoutVariantIdParityTest giữ parity id); chỉ bản dọc
     * bị pin hash ở đây nên chỉ cập nhật hằng bản dọc.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 183dcd38…3e77dd.
     *
     * ⚠ CẬP NHẬT 2026-08-28 (lần 9) — owner duyệt (task vm-toggles-autostart), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **thêm công tắc bong bóng VietMap trên cụm** (`switch_vm_bubble_enabled`,
     * **mặc định TẮT** — opt-in) vào ĐẦU card "Vị trí bong bóng VietMap trên cụm", ở CẢ hai biến thể layout
     * (LayoutVariantIdParityTest giữ parity id). Công tắc gate panel kéo-thả vị trí bong bóng và, khi bật,
     * tự khởi động VietMap MỘT LẦN (giống badge tốc độ; dedup pidof). Đi kèm việc đổi mặc định badge tốc độ
     * VietMap sang TẮT (`Prefs.badgeEnabled` default false) — thuần code, không đụng layout. `strings.xml`
     * KHÔNG đổi (nhãn công tắc + nhắc đặt lúc chạy qua `Lang.t`), nên hằng strings.xml giữ nguyên. Chỉ bản
     * dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 7d283550…b5ae15.
     *
     * ⚠ CẬP NHẬT 2026-09-03 (lần 10) — owner duyệt (task seat-comfort-auto), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **thêm mục "Ghế: làm mát / sưởi tự động"** (spec `seat-comfort-auto`) NGAY
     * SAU khối voice-key ở cột trái, ở CẢ hai biến thể layout (portrait + `layout-w960dp`; xe render bản
     * rộng — bài học F3 P0). Thêm 29 id (parity giữ bởi LayoutVariantIdParityTest): `txt_seat_comfort_title`
     * · `txt_seat_comfort_hint` · `switch_seat_comfort_enabled` · `seat_comfort_mode`+`seat_mode_cool`/
     * `seat_mode_heat` · `seat_comfort_grid` · `seat_rear_row` · `seatN_label`/`seatN_group`/`seatN_off`/
     * `seatN_l1`/`seatN_l2` (N=0..3) · `btn_seat_comfort_apply`. Chế độ TOÀN CỤC làm mát ↔ sưởi (loại trừ) +
     * mức từng ghế (Tắt/Mức 1/Mức 2); hiện 2 ghế (Seal) hay 4 ghế (Han). `strings.xml` KHÔNG đổi (nhãn đặt
     * lúc chạy qua `Lang.t`), nên hằng strings.xml giữ nguyên. Chỉ bản dọc bị pin hash ở đây nên chỉ cập
     * nhật hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 6369e497…b2dfb2.
     *
     * ⚠ CẬP NHẬT 2026-09-04 (lần 11) — owner duyệt (task pm25-auto-filter), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **thêm mục "Tự lọc bụi mịn (PM2.5)"** (spec `pm25-auto-filter`) NGAY SAU
     * card ghế ở cột trái, ở CẢ hai biến thể layout (portrait card riêng · `layout-w960dp` inline sau
     * divider; xe render bản rộng). Thêm 3 id (parity giữ bởi LayoutVariantIdParityTest):
     * `txt_pm25_title` · `switch_pm25_filter` · `txt_pm25_level`. Một công tắc bật/tắt (mặc định TẮT) +
     * nhãn mức bụi hiện tại (đọc `getPM2p5Level` device 1008 → nhãn VI; off-car "—"). Bật → xe tự lọc
     * LIÊN TỤC không popup qua `BYDAutoAcDevice` (reflection: enablePurificationFunctionPrompt(0) +
     * setAutoCleanAirState(1)). `strings.xml` KHÔNG đổi (nhãn đặt lúc chạy qua `Lang.t`), nên hằng
     * strings.xml giữ nguyên. Chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng
     * (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = c53e73c2…5c4c.
     *
     * ⚠ CẬP NHẬT 2026-09-04 (lần 12) — owner duyệt (task impl-vk-status), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **thêm CHỈ BÁO TRẠNG THÁI phím-thoại + nút "Kiểm tra / Sửa ngay"** trong
     * khối voice-key ở cột trái, NGAY SAU `switch_voicekey_enabled`, ở CẢ hai biến thể layout (portrait +
     * `layout-w960dp`; xe render bản rộng). Thêm 2 id (parity giữ bởi LayoutVariantIdParityTest):
     * `txt_voicekey_status` (dòng trạng thái đọc `NavAccessibilitySource.connected` — service Hỗ trợ đã
     * BOUND chưa; sau reboot thường ENABLED-nhưng-CHƯA-bound ⇒ phím rơi về chức năng gốc) · `btn_voicekey_recheck`
     * (nút REUSE `NavConnect.grantAccessibility(reset=true)` — đúng đường heal của toggle OFF→ON, KHÔNG đổi
     * grant logic). Nhãn + màu (xám/xanh/đỏ) đặt lúc chạy qua `Lang.t` + `setTextColor`. `strings.xml` KHÔNG
     * đổi (đang byte-seal) nên hằng strings.xml giữ nguyên. Chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật
     * hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 27730837…b6ff.
     *
     * ⚠ CẬP NHẬT 2026-09-04 (lần 13) — owner duyệt (task impl-ui-redesign-b), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **tái tổ chức UI sang "Option B"** (docs/specs/ui-redesign-options.html):
     * gom mọi tính năng thành 9 card có HÀNG TIÊU ĐỀ luôn hiện + THÂN GẬP được trên MỘT màn ngang. Chỉ đổi
     * TRÌNH BÀY + auto-apply, KHÔNG đụng pipeline/grant/runtime: (1) BỎ hai nút "Áp dụng"
     * `btn_seat_comfort_apply` + `btn_vm_pos_apply` (owner: "chỉnh xong là lưu, nút Áp dụng vô nghĩa") — ghế
     * auto-apply trong listener đổi mức/chế độ, bóng VietMap gửi ngay khi thả kéo-thả ⇒ bộ id 97→95, parity
     * giữ ở CẢ hai biến thể (LayoutVariantIdParityTest); (2) DỜI card: Biển báo tốc độ + Bóng VietMap ra khỏi
     * `cast_body` thành card riêng, `cb_headless_autostart` + `btn_check_update` sang card "Hệ thống", recovery
     * (cast_recovery_*) + `btn_nav_stop` sang card "Nâng cao"; (3) cơ chế gập tái dùng mẫu
     * `cast_recovery_toggle` nhưng dùng `android:tag` (findViewWithTag) để KHÔNG thêm @+id nào. `strings.xml`
     * KHÔNG đổi (mọi chữ mới đặt lúc chạy qua `Lang.t`), nên hằng strings.xml giữ nguyên. Chỉ bản dọc bị pin
     * hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = f0124377…856c.
     *
     * ⚠ CẬP NHẬT 2026-09-05 (lần 14) — owner duyệt (task ui-visual-upgrade-l2 · Stage 2a), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **Level-2 UI Stage 2a — ô icon gradient cho mỗi thẻ tính năng**: thêm 9
     * `ImageView` "tile" (34dp, nền gradient bo góc 10dp `tile_*` + icon vector trắng `ic_*_g`, `scaleType`
     * center, padding 6dp) làm CON ĐẦU TIÊN của hàng tiêu đề mỗi thẻ — `ic_nav` · `ic_cast` · `ic_mic` ·
     * `ic_badge` · `ic_bubble` · `ic_seat` · `ic_dust` · `ic_sys` · `ic_adv`; hai thẻ "Hệ thống"/"Nâng cao"
     * (tiêu đề trần, không có hàng ngang) được bọc thêm một `LinearLayout` ngang để chứa tile. Đồng thời sửa
     * STYLE hai nút phím-thoại: `btn_voicekey_learn` (thêm nền `btn_outline` + chữ `@color/brand` +
     * `textAllCaps=false`) và `btn_voicekey_add` (chữ `@color/brand` — đã có sẵn). CHỈ THÊM id mới (thuần
     * cộng), KHÔNG gỡ/đổi id nào ⇒ bộ id 95→104, parity giữ ở CẢ hai biến thể (LayoutVariantIdParityTest).
     * Không đụng `.kt`/pipeline (tile thuần hiển thị, MainActivity không findViewById các id này).
     * `strings.xml` KHÔNG đổi. Chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng
     * (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 1623d16d…485a.
     *
     * ⚠ CẬP NHẬT 2026-09-05 (lần 15) — owner duyệt (task ui-visual-upgrade-l2 · Stage 2b — gỡ emoji thừa), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **Level-2 UI Stage 2b — gỡ tiền tố EMOJI thừa ở tiêu đề thẻ**: sau khi
     * Stage 2a (lần 14) thêm ô icon gradient cho mỗi thẻ, emoji dẫn đầu trong `android:text` bị TRÙNG với
     * icon tile ⇒ gỡ emoji + khoảng trắng đứng đầu 7 tiêu đề thẻ (🧭 Navigation + HUD · 📺 Cluster Cast ·
     * 🎙 Nút vật lý → Trợ lý giọng nói · 🚦 Biển báo tốc độ · 🫧 Bóng VietMap trên cụm · ⚙ Hệ thống ·
     * 🛠 Nâng cao), chỉ còn phần chữ. Làm ở CẢ hai biến thể layout (portrait + `layout-w960dp`). CHỈ sửa
     * GIÁ TRỊ `android:text`; comment XML giữ nguyên (không render). KHÔNG thêm/gỡ/đổi id nào ⇒ bộ id vẫn
     * 104, parity giữ ở CẢ hai biến thể (LayoutVariantIdParityTest). Không đụng `.kt`/pipeline. `strings.xml`
     * KHÔNG đổi. Chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 0dedd38e…8fcb97.
     *
     * ⚠ CẬP NHẬT 2026-09-05 (lần 16) — owner duyệt (task ui-visual-upgrade-l2 · hero + seat diagram + pm25 gauge), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **Level-2 UI — hoàn thiện cockpit** (3 phần):
     *   (b) THÊM dải HERO trạng-thái-sống ở ĐẦU nội dung (trên thẻ tính năng đầu) — thẻ CHỈ-ĐỌC gồm
     *       `hero_nav_icon` · `hero_dist` · `hero_road` · `hero_speed` · `hero_cast` · `hero_vk` (nối read-only
     *       trong `MainActivity.refresh`/`updateHeroStrip` từ state sẵn có; giá trị không có accessor sạch → "—").
     *   (c) THAY khối radio ghế bằng SƠ ĐỒ GHẾ vẽ Canvas: GỠ 22 id radio (`seat0_group`/`seat0_off`/`seat0_l1`/
     *       `seat0_l2` … `seat3_*` · `seatN_label` · `seat_comfort_grid` · `seat_rear_row`) và THÊM
     *       `com.byd.clusternav.comfort.SeatDiagramView` id `seat_diagram`; GIỮ nhóm chế độ `seat_comfort_mode`/
     *       `seat_mode_cool`/`seat_mode_heat` + `switch_seat_comfort_enabled` + `txt_seat_comfort_title`/
     *       `txt_seat_comfort_hint` (feature ghế NGUYÊN VẸN: mức mỗi ghế vẫn persist `Prefs.seatComfortLevel` +
     *       `SeatComfortApplier.applyNow`; 2/4 ghế theo mẫu qua `setSeatCount`).
     *   (d) THÊM `com.byd.clusternav.comfort.Pm25GaugeView` id `pm25_gauge` vào thẻ PM2.5 (giữ `txt_pm25_level`
     *       làm chữ phụ; `refreshPm25Level` cập nhật cả hai).
     * Bộ id 104→90 (−22 radio ghế, +8: `seat_diagram` + `pm25_gauge` + 6 `hero_*`), parity giữ ở CẢ hai biến
     * thể (LayoutVariantIdParityTest). `strings.xml` KHÔNG đổi (nhãn đặt lúc chạy qua `Lang.t`). Chỉ bản dọc bị
     * pin hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = ad570d4d…e0f2.
     *
     * ⚠ CẬP NHẬT 2026-09-05 (lần 17) — owner duyệt (task steer-batch · 4 tinh chỉnh UI cockpit), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **4 tinh chỉnh UI owner yêu cầu** (thuần trình bày, KHÔNG đụng pipeline):
     *   (A) GỠ 2 nút vị trí bong bóng VietMap `btn_vm_pos_right_half` + `btn_vm_pos_reset` — bong bóng nay
     *       CHỈ kéo-thả (giữ `vm_bubble_placement_container` + `switch_vm_bubble_enabled` + `txt_vm_pos_hint`;
     *       thả kéo-thả vẫn tự lưu + gửi như cũ).
     *   (B) GỠ 2 nút biển báo `btn_badge_preview` + `btn_badge_reset` — badge nay chỉ kéo-thả
     *       (`badge_placement_container`) + cỡ (`seek_badge_size`); persistence không đổi.
     *   (D) ĐỔI 4 `<CheckBox>` → `<Switch>` GIỮ NGUYÊN @+id + android:text (`cb_marquee`,
     *       `cb_headless_autostart`, `cb_autostart`, `cb_autostart_split`) cho khớp hàng công tắc cockpit
     *       (nhãn trái text_secondary, công tắc phải — như `switch_upcoming_badge`); glow xanh do theme
     *       colorControlActivated. (C) HERO km/h nối tốc-độ thật + hero_dist là THUẦN `.kt`, KHÔNG đụng layout.
     * Bộ id 90→86 (−4 nút; KHÔNG thêm id nào), parity giữ ở CẢ hai biến thể (LayoutVariantIdParityTest).
     * `strings.xml` KHÔNG đổi (nhãn/emoji đặt lúc chạy hoặc hardcode trong layout). Chỉ bản dọc bị pin hash
     * ở đây nên chỉ cập nhật hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 7b05ba46…093b.
     *
     * ⚠ CẬP NHẬT 2026-09-05 (lần 18) — owner duyệt (task ui-visual-upgrade-l2 · Stage 2 — dựng lại layout theo cockpit), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **tái dựng TOÀN BỘ layout theo Level-2 "cockpit"** (docs/specs/ui-visual-upgrade-l2.html):
     * compose hệ @style Cockpit (Group · Row · RowDivider · HeroCard · HeroLead · HeroDist · HeroStreet ·
     * Button · StatusPill · Text) + custom view (SegmentedControlView · SpeedDialView · ClusterPreviewView ·
     * SeatDiagramView · Pm25GaugeView).
     * HERO trạng-thái-sống 3 thẻ (NAV · CAST · QUICK) + BẢNG các Group. ĐỔI CÓ CHỦ Ý bộ id (86→85): GỠ
     * `spinner_cluster_mode` → `seg_cluster_mode` (SegmentedControlView); GỠ `seat_comfort_mode`/`seat_mode_cool`/
     * `seat_mode_heat` → `seg_seat_mode` (SegmentedControlView, warm); THÊM `hero_cast_preview` (ClusterPreviewView);
     * `hero_speed` đổi TextView→SpeedDialView (id giữ). Parity id + tag gập (15 tag toggle_/body_/sum_) giữ ở CẢ
     * hai biến thể (LayoutVariantIdParityTest + CollapseTagParityContractTest). MainActivity rewire tương ứng
     * (seg_cluster_mode/seg_seat_mode/hero_speed.setSpeed/hero_cast_preview.setSplit-setFull read-only);
     * ClusterModeSelectorContractTest + L2CockpitUiWiringContractTest cập nhật GIỮ NGUYÊN intent. `strings.xml`
     * KHÔNG đổi. Chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 70b57ce9…0700d.
     *
     * ⚠ CẬP NHẬT 2026-09-05 (lần 19) — owner duyệt (task ui-visual-upgrade-l2 · compare-pass-1 khép chênh lệch thị giác), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **khép các chênh lệch thị giác so với mockup L2** (docs/specs/ui-visual-upgrade-l2.html):
     *   (1) CHIỀU SÂU — root LinearLayout thêm `clipChildren=false` + `clipToPadding=false` để bóng đổ của thẻ
     *       (elevation) không bị cắt. Nền cửa sổ 2 vầng sáng (window_bg) + bump elevation Group/HeroCard là
     *       THUẦN resource (không đụng layout).
     *   (2) Nút chính `cast_zone_full` — set THẲNG `android:background=@drawable/btn_primary` +
     *       `android:textColor=@android:color/white` trên phần tử (không chỉ qua style) để chắc chắn hiện
     *       gradient xanh→chàm khi bật (framework Button có thể bỏ qua nền chỉ-khai-trong-style).
     *   (4) Nút nhanh — thay glyph TOFU (▣/◧/◨, font không có) bằng icon vector trắng `drawableStart`
     *       (`ic_cast_g` cho full, `ic_left_g`/`ic_right_g` mới cho trái/phải) + `drawablePadding`; bỏ glyph
     *       khỏi `android:text`, chỉ giữ chữ ("Chiếu full cụm"/"Trái"/"Phải"). MainActivityCastController bỏ
     *       glyph trong các chuỗi text runtime tương ứng (thuần trình bày — KHÔNG đổi logic/enabled/dispatch).
     *   (5) Thêm 2 tiêu đề mục TĨNH `Cockpit.Text.SectionHeader` ("Trạng thái sống" trên HERO · "Bảng tính năng"
     *       trên bảng) — KHÔNG @+id, KHÔNG tag.
     * CHỈ THÊM thuộc tính + text tĩnh, KHÔNG thêm/gỡ/đổi @+id nào ⇒ bộ id vẫn 85, parity giữ ở CẢ hai biến
     * thể (LayoutVariantIdParityTest + CollapseTagParityContractTest). `strings.xml` KHÔNG đổi. Đổi ở CẢ hai
     * biến thể; chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 75c8616b…0973.
     *
     * ⚠ CẬP NHẬT 2026-09-05 (lần 20) — owner duyệt (task ui-visual-upgrade-l2 · polish Stage A), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **3 tinh chỉnh UI thuần TRÌNH BÀY (KHÔNG đụng @+id/pipeline)** — trong đó
     * CHỈ FIX 2 chạm bản dọc này:
     *   FIX 2 — DESIGN SYSTEM nút GỌN: 10 nút hành động đổi sang `@style/Cockpit.Button.Compact.*`
     *   (Secondary/Warning/Text mới thêm trong `styles.xml`: minHeight 38dp · padding ngang 14dp/dọc 7dp ·
     *   textSize `@dimen/text_label` = 13sp) + đổi `android:layout_width` match_parent→wrap_content + thêm
     *   `android:layout_gravity="start"` để nút ÔM chữ thay vì lấp cả hàng: `btn_reconnect_nav` ·
     *   `btn_voicekey_recheck` · `btn_voicekey_learn` · `btn_voicekey_add` · `btn_check_update`
     *   (Compact.Secondary) · `btn_nav_stop` · `cast_stop` · `cast_clear_cluster` · `cast_deep_rescue`
     *   (Compact.Warning) · `cast_diagnostics` (Compact.Text). Nút hero `cast_zone_full/left/right` GIỮ NGUYÊN.
     *   (FIX 1 — chiều cao HERO bằng nhau — CHỈ ở bản rộng `layout-w960dp`, KHÔNG đụng file này; FIX 3 — hình
     *   ghế SVG — CHỈ ở `SeatDiagramView.kt`.) KHÔNG thêm/gỡ/đổi @+id nào ⇒ bộ id vẫn 85, parity giữ ở CẢ hai
     *   biến thể (LayoutVariantIdParityTest). `strings.xml` KHÔNG đổi (nhãn nút đặt lúc chạy qua `Lang.t`).
     *   Chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = 79729842…2f9e.
     *
     * ⚠ CẬP NHẬT 2026-09-05 (lần 21) — owner duyệt (task polish-stageB-i18n · language selector), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **thêm hàng chọn NGÔN NGỮ "Ngôn ngữ / Language"** vào nhóm "Hệ thống"
     * (ngay dưới `cb_headless_autostart`), ở CẢ hai biến thể layout (portrait + `layout-w960dp`; xe render
     * bản rộng). Thêm 1 id `seg_language` (SegmentedControlView 3 đoạn: Theo xe / VI / EN) ⇒ bộ id 85→86,
     * parity giữ ở CẢ hai biến thể (LayoutVariantIdParityTest). MainActivity `setupLanguageSelector` seed
     * `selectedIndex` từ `Lang.choice()` (không bắn onSelected) rồi cài onSelected → `Lang.setChoice` +
     * `recreate()`; kèm bản dịch song ngữ đầy đủ nhãn TĨNH qua `BilingualLabels.localizeTree` (id-free
     * tree-walk, KHÔNG sửa layout ngoài hàng mới này). Icon hàng dùng glyph mới `ic_lang_g` + nền `tile_sys`.
     * `strings.xml` KHÔNG đổi (mọi chữ đặt lúc chạy qua `Lang.t` / `BilingualLabels`), nên hằng strings.xml
     * giữ nguyên. Chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ (giữ lại để trace): activity_main.xml = fc5424d5…eefd39.
     *
     * ⚠ CẬP NHẬT 2026-09-06 (lần 22) — owner duyệt (task polish · gọn sơ đồ ghế + bỏ câu hint header), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **bỏ dòng hint "Mỗi tính năng là một thẻ — chạm tiêu đề để mở/gập"** ở đầu
     * trang (owner: câu này khó hiểu, vô nghĩa) + thêm `layout_marginBottom` cho `txt_app_title` giữ khoảng
     * cách trước section header, ở CẢ hai biến thể. KHÔNG thêm/bớt id (parity giữ 86); chỉ text + spacing.
     * (Sơ đồ ghế `SeatDiagramView` đẩy ghế lên + gọn chiều cao là sửa .kt, không đụng layout.) Bản rộng không pin.
     * Hằng cũ lần-21 (giữ trace): activity_main.xml = 9c864be5…237eff.
     *
     * ⚠ CẬP NHẬT 2026-09-06 (lần 23) — owner duyệt (task light-mode · Stage 2 — theme selector), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **thêm hàng chọn GIAO DIỆN "Giao diện / Theme"** vào nhóm "Hệ thống"
     * (ngay dưới hàng `seg_language`), ở CẢ hai biến thể layout (portrait + `layout-w960dp`; xe render bản
     * rộng — bài học F3 P0). Thêm 1 id `seg_theme` (SegmentedControlView 3 đoạn: Theo xe / Sáng / Tối) ⇒
     * bộ id 86→87, parity giữ ở CẢ hai biến thể (LayoutVariantIdParityTest). MainActivity `setupThemeSelector`
     * seed `selectedIndex` từ `ThemeMode.choice` (SYSTEM=0/LIGHT=1/DARK=2, KHÔNG bắn onSelected) rồi cài
     * onSelected → `ThemeMode.setChoice` + `recreate()` để cả Activity resolve lại values/ (LIGHT) hoặc
     * values-night/ (DARK). Nhãn hàng "Giao diện" localize qua `BilingualLabels`; nhãn đoạn qua `Lang.t`.
     * `strings.xml` KHÔNG đổi (mọi chữ đặt lúc chạy), nên hằng strings.xml giữ nguyên. Chỉ bản dọc bị pin
     * hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ lần-22 (giữ trace): activity_main.xml = d4118a4b…bfc724.
     *
     * ⚠ CẬP NHẬT 2026-09-06 (lần 24 · thu UI 70%) — owner duyệt (task stage2-ui · T2), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **thu NHỎ toàn bộ giao diện xuống ~70%** (owner: phần tử quá to trên màn
     * rộng của xe). Cách làm: (1) scale token dùng chung `res/values/dimens.xml` (text/padding/gap/radius;
     * `touch_min` giữ SÀN 40dp cho tap target) + hằng literal trong `@style/Cockpit.*` (`res/values/styles.xml`)
     * + cỡ mặc định các custom view (SpeedDial/ClusterPreview/SegmentedControl/SeatDiagram/Pm25Gauge); (2)
     * scale các cỡ INLINE `dp/sp` trong CẢ hai biến thể layout ~0.7 (script `scripts/shrink-ui-70.py`,
     * bỏ qua `0dp` + comment; `1dp` divider giữ nguyên). CHỈ đổi CỠ hiển thị — KHÔNG thêm/gỡ/đổi @+id nào ⇒
     * bộ id vẫn 87, parity giữ ở CẢ hai biến thể (LayoutVariantIdParityTest). `strings.xml` KHÔNG đổi (hằng
     * giữ nguyên). Chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng (`layout-w960dp`) không pin.
     *
     * Hằng cũ lần-23 (giữ trace): activity_main.xml = dfd93e64…b37230.
     *
     * ⚠ CẬP NHẬT 2026-09-06 (lần 25 · text về gốc, chỉ giảm chiều cao) — owner duyệt (task v134-fixes · FIX 2), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **khôi phục CỠ CHỮ về bản gốc v1.32** (owner: v1.33 thu 70% làm chữ quá nhỏ,
     * khó đọc trên màn xe), **CHỈ giữ giảm ~70% ở chiều DỌC**. Cách làm: tái sinh layout từ nền v1.32 rồi
     * scale ×0.7 CHỈ thuộc tính DỌC (paddingTop/Bottom · marginTop/Bottom · minHeight-dp · layout_height-dp
     * của phần tử KHÔNG vuông); cỡ chữ (sp), dp NGANG (rộng · paddingStart/End · marginStart/End ·
     * drawablePadding), padding tất-cả-cạnh, và cỡ icon/dot VUÔNG giữ nguyên v1.32; bốn custom view
     * gauge/dial/sơ-đồ/preview (SpeedDial/Pm25Gauge/SeatDiagram/ClusterPreview) scale CẢ rộng+cao (giữ vuông
     * — thuộc "chiều cao custom view → giữ 70%"). Kết quả: chữ về cỡ gốc, layout ngắn hơn rõ. GIỮ fix viền
     * 2 nét của v1.33 (ở drawable — KHÔNG đụng). KHÔNG thêm/gỡ/đổi @+id nào ⇒ bộ id vẫn 87, parity giữ ở CẢ
     * hai biến thể (LayoutVariantIdParityTest). `styles.xml`/`dimens.xml`/custom view sửa theo cùng nguyên
     * tắc. `strings.xml` KHÔNG đổi. Chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc.
     *
     * Hằng cũ lần-24 (giữ trace): activity_main.xml = 1a7c90f7…d636f921.
     *
     * ⚠ CẬP NHẬT 2026-09-06 (lần 26 · nới spacing dọc — hết dính) — owner duyệt (task loosen-spacing), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **nới lại KHOẢNG CÁCH DỌC** (owner báo on-car sau v1.34: padding / margin /
     * line-height quá chật, các phần tử "dính dính vào nhau"). Gốc: v1.34 thu MỌI khoảng dọc còn ~70% trong
     * khi chữ đã về cỡ gốc v1.32 ⇒ chữ đủ to nhưng khe hở quá hẹp + không giãn dòng. Cách sửa: nâng
     * padding / margin DỌC từ ~70% lên ~88-92% cỡ gốc v1.32 (thoải mái, vẫn NGẮN HƠN v1.32 một chút vì v1.32
     * vốn quá cao). Trong file này (chỉ inline dp DỌC — marginTop/Bottom · paddingTop/Bottom · minHeight
     * toggle) theo map 3->4 · 4->5 · 6->7 · 7->9 · 8->11 · 10->13 · (minHeight) 34->44 = 57 giá trị mỗi biến
     * thể; dp NGANG (rộng · paddingStart/End · marginStart/End · drawablePadding) + cỡ chữ (sp) + kích thước
     * custom-view VUÔNG (SpeedDial/Pm25Gauge/…) + chiều cao vùng vẽ (140/98/45dp) GIỮ NGUYÊN. Khoảng thẻ↔thẻ
     * và tap target nâng qua token `@dimen/gap` 10->13 + `@dimen/touch_min` 40->46 (dimens.xml); giãn dòng
     * `android:lineSpacingMultiplier` 1.2 thêm vào style chữ NHIỀU DÒNG (Cockpit.Text.Hint · Cockpit.Row.Subtitle)
     * — KHÔNG thêm cho tiêu đề/nhãn một dòng (styles.xml); các style Row/HeroCard/Compact/StatusPill/Segment/
     * ListRow/Spinner/Pill nâng metric dọc tương ứng. GIỮ fix viền 2 nét của v1.33 (ở drawable — KHÔNG đụng).
     * KHÔNG thêm/gỡ/đổi @+id nào ⇒ bộ id vẫn 87, parity giữ ở CẢ hai biến thể (LayoutVariantIdParityTest).
     * `strings.xml` KHÔNG đổi. Chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng
     * (`layout-w960dp`) không pin.
     *
     * Hằng cũ lần-25 (giữ trace): activity_main.xml = 046d23b0…03fd8d98.
     *
     * ⚠ CẬP NHẬT 2026-09-08 (lần 27) — owner duyệt (task pm25-clean-now + auto-poll), KHÔNG sửa lén.
     *
     * `activity_main.xml` đổi vì **thêm nút "Lọc ngay" vào card PM2.5** (owner 2026-09-08: cần nút bấm lọc
     * luôn + vá "popup hiện mà không auto-lọc"), NGAY SAU row PM2.5 trong Group, ở CẢ hai biến thể layout
     * (portrait + `layout-w960dp`). Thêm 1 id (parity giữ bởi LayoutVariantIdParityTest): `btn_pm25_clean_now`
     * ⇒ bộ id 87→88. Nút gọi `Pm25FilterApplier.cleanNow` (`setQuickCleanAirState(1)` NGAY, bất kể công tắc).
     * Kèm sửa .kt (KHÔNG đụng seal): applier thêm vòng poll ~45s bắn lọc-ngay khi bụi ≥ ngưỡng — vì on-car
     * `setAutoCleanAirState` một mình KHÔNG tự lọc. `strings.xml` KHÔNG đổi (nhãn "Lọc ngay" đặt lúc chạy qua
     * `Lang.t`+`BilingualLabels`). Chỉ bản dọc bị pin hash ở đây nên chỉ cập nhật hằng bản dọc; bản rộng
     * (`layout-w960dp`) không pin.
     *
     * Hằng cũ lần-26 (giữ trace): activity_main.xml = 0678a33d…650091.
     *
     * ⚠ CẬP NHẬT 2026-09-08 (strings.xml · lần 1 — đổi tên launcher "Kachi") — owner duyệt
     * (task rename-launcher-kachi; repo fork byd-launcher, tên launcher = Kachi), KHÔNG sửa lén.
     *
     * `strings.xml` đổi LẦN ĐẦU kể từ khi lập seal: `app_name` "Cluster Nav 2.0" → **"Kachi"**. Đây là
     * nhãn launcher hiện trên icon / recents / danh sách app (manifest `android:label="@string/app_name"`
     * cho CẢ `<application>` lẫn `MainActivity`). CHỈ đổi `app_name`; `nav_listener_label` + `acc_label`
     * GIỮ NGUYÊN (nhãn bề mặt dịch vụ của TÍNH NĂNG dẫn đường ClusterNav — README giữ ClusterNav làm nhóm
     * tính năng). Tiêu đề hiện trên màn (`MainActivity` ghi `txt_app_title` lúc chạy) + tiêu đề thông báo
     * foreground (`BootSetupService` · `VietMapAutostartService`) đổi sang "Kachi" ở `.kt` — KHÔNG đụng seal.
     * Layout `android:text="ClusterNav"` là placeholder design-time (bị `MainActivity` ghi đè lúc chạy) nên
     * KHÔNG đổi → hằng `activity_main.xml` GIỮ NGUYÊN (556c78e4…785c). Chỉ cập nhật hằng `strings.xml` dưới đây.
     *
     * Hằng cũ (giữ trace): strings.xml = 8300437c…e8fe4cdd.
     *
     * ⚠ CHỈ được cập nhật hằng ở đây khi thay đổi là CHỦ Ý và có vết trong backlog. Cập nhật theo phản xạ
     * "cho test xanh" là **phá seal** — đúng thứ cơ chế này sinh ra để bắt.
     * ⚠ CẤM gỡ `activity_main.xml` khỏi `T11_PATHS` để né việc cập nhật hằng — nó có mặt trong danh sách
     * vì giao diện biển-báo-tốc-độ nằm trong file này (xem đính chính 08-24 ở `PROJECT-BACKLOG.md` E9).
     */
    internal val T11_HASHES = mapOf(
        "app/src/main/res/layout/activity_main.xml" to "556c78e4c46c34f5263d63a2e797df192c458f2ae28542e2bfa648793945785c",
        "app/src/main/res/values/strings.xml" to "45fa51a8e23fff153fd262792cddbda0ad40ddd129bee73f9d2200606657fa04",
    )
    internal val T10_PREFIXES = "app/src/vehicleTest/|app/src/testVehicleTest/|car-integration/|core/src/main/kotlin/com/byd/clusternav/carexec/|core/src/test/kotlin/com/byd/clusternav/carexec/|gradle/|scripts/evidence/|scripts/vehicle/|vehicle-contracts/src/main/kotlin/com/byd/clusternav/vehicle/t10/|vehicle-contracts/src/test/kotlin/com/byd/clusternav/vehicle/t10/".split('|')
    internal val T10_FIXED = pathSet(".gitignore|app/build.gradle.kts|app/src/test/java/com/byd/clusternav/BuildArtifactNamingTest.kt|app/src/test/java/com/byd/clusternav/MainProbeSurfaceAbsenceTest.kt|docs/_handoff/session-2026-08-10-hud-sign-t10-offcar-complete.md|docs/_handoff/session-2026-08-10-hud-sign-t10-preparation.md|scripts/verify-seal-hud-sign-vehicle-test-t10.sh|vehicle-contracts/src/main/resources/t10-contracts.schema.json")
    internal fun isT10InventoryPath(path: String) = path in T10_FIXED || T10_PREFIXES.any(path::startsWith)
    internal val IGNORED_DIRECTORY_NAMES = setOf(".git", ".gradle", ".idea", ".authorized-build", ".t10-local", "build", "node_modules")
    internal val COMPILED_BANS = listOf(
        Regex("java/lang/Process(?:Builder)?(?:[^A-Za-z0-9_]|$)"), Regex("java/lang/Runtime(?:[^A-Za-z0-9_]|$)"),
        Regex("java/net/|java/nio/channels/(?:Server)?Socket", RegexOption.IGNORE_CASE),
        Regex("(?i)(?:^|[/.$;_<>()-])(?:network|socket|adb|dadb|device|callback|execute)(?:$|[/.$;_<>()-])"),
        Regex("(?i)carexec|android/|dalvik/|dadbvehicle|adbtransport"),
    )
}
