package com.byd.clusternav.launcher.testbridge

/**
 * ═══ T-BRIDGE · DANH SÁCH TRẮNG khoá prefs mà `prefs_set` được phép GHI ═══════════════════════════════════════
 *
 * Tách khỏi [TestBridgeCommands] ở 2.74 (R8-B) vì lý do cơ học: tệp đó đã **464 dòng** và mỗi lượt thêm một bộ núm
 * đo-trên-xe lại bồi thêm ~10 dòng danh sách + chú thích ⇒ trần 500 của CLAUDE.md §4.1 vỡ đúng ở lượt sau. Danh sách
 * là một **vai riêng** (*"khoá nào được ghi"*) khác hẳn vai của [TestBridgeCommands] (*"cú pháp một lệnh"*), nên
 * tách đúng đường khớp chứ không phải cắt bừa cho vừa trần.
 *
 * Ba ràng buộc của danh sách này nằm ở KDoc [TestBridgeCommands.PREFS_SET] — **đọc ở đó trước khi thêm một khoá**.
 * Nhắc lại đúng một điều quan trọng nhất: `KachiTestBridge` là receiver `exported=true`, nên đây là **cửa duy nhất**
 * vào `SharedPreferences` từ ngoài tiến trình, và mọi khoá trong đây phải đảo lại được bằng một cú chạm trong Cài đặt.
 */
object TestBridgeWritableKeys {

    /**
     * Toàn bộ khoá ghi được. [TestBridgeCommands.WRITABLE_PREFS_KEYS] trỏ thẳng vào đây (một tên, một chỗ khai).
     *
     * Bốn khoá đầu là khoá THEO XE của đường giọng nói (`PrefsVoiceV3.kt` + `Prefs.voiceAskAloud`); khoá
     * `top_strip_labels` là công tắc nhãn chip **theo hồ sơ** (`WorkspacePrefs.setTopStrip`) — đường duy nhất đo
     * được R14 bằng máy thay vì bằng một ảnh chụp màn hình.
     *
     * ## H5 (2026-09-16) — **bốn núm chỉnh bộ nghe**, tất cả đều mặc định = hằng đang chạy
     * `voice_endpoint_silence_ms` · `voice_endpoint_min_speech_ms` ([VoiceEndpointer]) và `voice_beam` ·
     * `voice_hotword_score` ([SherpaModelCatalog]). Chúng vào đây vì đúng câu hỏi chúng sinh ra để trả lời —
     * *"cabin 80 km/h thì 800 ms im là sớm hay muộn"*, *"beam 8 có nghe ra hơn không"* — chỉ đo được bằng cách
     * đổi giá trị **giữa hai lượt `wav`/`listen` trên xe**, tức bằng máy, không phải bằng một lượt build lại APK
     * cho mỗi con số. Mặc định của cả bốn **bằng đúng hằng hôm nay** ⇒ danh sách này dài ra mà hành vi không đổi
     * một ly; và cả bốn vẫn nằm trong đường GIỌNG NÓI, không chạm cast/cụm/phím (ràng buộc (2) của KDoc trên).
     */
    val ALL: Set<String> = setOf(
        "voice_confirm_ids",
        "voice_ask_aloud",
        "voice_follow_up_ms",
        "voice_mic_source",
        "top_strip_labels",
        "voice_endpoint_silence_ms",
        "voice_endpoint_min_speech_ms",
        "voice_endpoint_floor_cap",
        // Ba núm của Silero VAD — đường ngắt câu CHÍNH từ 1.69 (docs/diagnostics/voice-stream-eval-2026-09-16.md
        // §8). Cùng lý do với ba khoá trên: bộ tham số chốt bằng lưới trên host, còn cabin thật thì chỉ đo được
        // bằng cách đổi số **giữa hai lượt nói** trên xe.
        "voice_vad_threshold",
        "voice_vad_min_speech_ms",
        "voice_vad_min_silence_ms",
        "voice_beam",
        "voice_hotword_score",
        // Tốc độ đọc Piper (owner 2026-09-17 "nói nhanh quá") — chỉnh mức chậm đúng ý trên xe không cần build.
        "voice_tts_speed",
        // owner 2026-09-21 (bản release production) — công tắc GIỮ NHẬT KÝ lượt nói. Vào đây vì ô tích của nó vừa
        // bị gỡ khỏi Cài đặt cùng mọi bề mặt dev/log: không có dòng này thì khoá thành **bất khả chỉnh**, tức dọn
        // bề mặt hoá ra dọn luôn khả năng. Đây cũng là khoá DUY NHẤT của danh sách này không còn đường đảo lại
        // bằng một cú chạm trong Cài đặt (xem ràng buộc (3) ở KDoc trên) — nó vẫn nằm trong đường GIỌNG NÓI và vẫn
        // chỉ ghi được khi chế độ kiểm thử đang mở, nên hai ràng buộc còn lại không đổi.
        "voice_keep_log",
        // Camera theo xi-nhan (findings 2026-09-23) — bật/tắt + chọn cam + chọn GÓC hiện từng bên, test nhanh
        // trên xe. `camera_lvds_option` đã GỠ cùng mười option LVDS (spec camera-turn-signal-hal-socket R6).
        "camera_signal_enabled",
        "camera_on_cluster",
        "camera_cam_left",
        "camera_cam_right",
        "camera_pos_left",
        "camera_pos_right",
        // R7 (owner 2026-09-26): góc xoay video TỪNG BÊN (2.71 — owner trên xe: "2 line setting độc lập cho camera
        // trái và phải") — cần đổi trên xe giữa hai lượt xi-nhan để chốt chiều đúng (mắt owner), không build lại.
        // Mỗi khoá có một hàng chip đảo lại được ở Cài đặt › Tiện nghi xe ⇒ ràng buộc (3) giữ. Khoá đơn cũ
        // `camera_rotation` (2.67–2.70) GỠ khỏi đây: `Prefs.cameraRotation` migrate nó một lần rồi xoá.
        "camera_rot_left",
        "camera_rot_right",
        // CLOSE-14 (CAM-LAG): đường KẾT XUẤT khung hình (`TV`/`SV`). Vào đây vì đúng câu hỏi nó sinh ra để trả lời —
        // *"TextureView có phải nguồn giật không"* — chỉ đo được bằng cách đổi đường **giữa hai lượt xi-nhan trên xe
        // đang chạy** rồi so `gfxinfo`, không phải bằng một lượt build lại APK cho mỗi bên. Có hàng chip đảo lại được
        // ở Cài đặt › Tiện nghi xe ⇒ ràng buộc (3) của KDoc trên vẫn giữ.
        "camera_render",
        // R8-A (2.74 · RE `electro-camera-RE-2026-09-26.md` §5 K10 · §6.1): VÙNG GƯƠNG + HÌNH KHUNG + KÊNH HAL.
        // Đây là bộ khoá **sinh ra để đo trên xe**: dải nào là hướng nào vẫn [CHƯA BIẾT] (§7 Q1/Q2), đường kính vòng
        // ảnh fisheye là [ĐOÁN], và `VIEW_CHANNEL_1..4` chưa ai gọi thử — cả ba chỉ chốt được bằng cách đổi giá trị
        // **giữa hai lượt xi-nhan** rồi chụp `camera_frame`, không phải bằng một lượt build lại APK cho mỗi con số.
        // Bốn khoá đầu có hàng chip đảo lại được ở Cài đặt › Tiện nghi xe ⇒ ràng buộc (3) của KDoc trên giữ.
        // 2.75 — NGUỒN ảnh: khung GHÉP (mặc định, y 2.74) hay MỘT KÊNH camera. [ĐO xe 27/09] kênh 1..4 trả
        // `rc=true` và cho trọn khung fisheye của một camera (kéo ngang ×4) — nguồn tốt hơn hẳn cho gương.
        "camera_source",
        "camera_span",
        "camera_strip_left",
        "camera_strip_right",
        "camera_shape",
        // Núm tinh chỉnh cạnh ô vuông của hình TRÒN (%). Không có chip riêng — nhưng **tác dụng** của nó đảo được
        // bằng một cú chạm: về chip "Chữ nhật" là hết ảnh hưởng, và `prefs_set camera_circle_scale 100` trả mặc định.
        "camera_circle_scale",
        "camera_hal_mode",
        // ── R8-B (2.74) · SÁU NÚM NẮN MÉO + công tắc `uTexMatrix` của đường kết xuất `GL` ──────────────────
        // `docs/diagnostics/offcar-2026-09-26/camera-dewarp-gl.md`. Cùng một lý do với cả bộ `camera_*` ở trên, chỉ
        // sắc hơn: bốn con số `F`/`K`/`SCALE`/`AMOUNT` của Electro nằm trong bytecode VMP ⇒ **[CHƯA BIẾT]** (RE §7
        // Q6), nên Kachi không copy số mà suy một bộ mặc định từ hình học rồi để owner **chỉnh bằng mắt trên xe**.
        // Không có đường nào khác: một khung fisheye thật chỉ có trên xe, và mỗi con số thử một lượt bằng build lại
        // APK là một buổi xe cho bốn giá trị. Cả chín đều có hàng −/+ (hoặc ô tích) đảo lại được trong Cài đặt ›
        // Tiện nghi xe ⇒ ràng buộc (3) của KDoc trên giữ nguyên; miền hợp lệ ở `:core` [CameraDewarpPrefs].
        "camera_dewarp_amount",
        "camera_dewarp_focal",
        "camera_dewarp_k",
        "camera_dewarp_scale",
        "camera_dewarp_cx",
        "camera_dewarp_cy",
        // 2.75 — DỊCH CỬA SỔ, KHÔNG phải dời tâm quang: đường duy nhất *"dịch khung ra sau"* mà ảnh vẫn thẳng
        // ([ĐO] xe 27/09 bác cả `scale` 140–145 % lẫn `cx −10 %`). Xem KDoc `CameraDewarp.panLocal`.
        "camera_dewarp_pan_x",
        "camera_dewarp_pan_y",
        "camera_gl_texmatrix",
    )
}
