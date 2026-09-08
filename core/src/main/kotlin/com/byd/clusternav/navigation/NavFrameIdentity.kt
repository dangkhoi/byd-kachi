package com.byd.clusternav.navigation

/**
 * **Bất biến MỘT-PACKAGE-MỘT-KHUNG** (B-I): một khung dẫn đường bắn ra cụm/HUD chỉ được ghép từ các kênh
 * thuộc **CÙNG một package**.
 *
 * ── VÌ SAO (đo trên source, 2026-08-22) ───────────────────────────────────────────────────────────────────
 * Ba kênh ảnh (mũi tên / làn / camera) được publish ĐỘC LẬP vào `ScreenCaptureSignal`, mỗi kênh tự chấm tươi
 * riêng với `ScreenCaptureSignal.STALE_MS` = 6000ms (`ScreenCaptureSignal.kt`). Khi người lái đổi app dẫn
 * (Waze → VietMap) hoặc hai app dẫn cùng hiển thị, kênh của app CŨ còn "tươi" thêm tới 6 giây ⇒ nếu quyết
 * định đầu ra không so danh tính thì **dải làn của một ngã ba KHÁC nằm ngay cạnh mũi tên đang báo**. Với
 * `LaneBoundsSource`, rect đo trong cửa sổ app A còn crop ra pixel HỢP LỆ trong ảnh app B ⇒ dữ liệu BỊA
 * (nhãn A, pixel B) mà tầng sau không có cách nào phát hiện.
 *
 * ── LUẬT ──────────────────────────────────────────────────────────────────────────────────────────────────
 *  • **So khớp NGUYÊN chuỗi**, KHÔNG gộp theo họ [NavApps]: `com.waze` (zin) và `com.chisadin.wazemod` là
 *    HAI app cài SONG SONG, chỉ một bản đang dẫn (đúng ca đã đo 08-22 — lý do `NavWindowPicker.rank` ra đời).
 *    Gộp họ ⇒ mũi tên bản này ghép với làn bản kia.
 *  • **Rỗng/null KHÔNG bao giờ là danh tính hợp lệ**: thiếu dữ liệu ⇒ im lặng (degrade-safe, CLAUDE.md §13),
 *    tuyệt đối không dựng khung từ một package không biết là ai.
 *
 * ── LỊCH SỬ (đọc trước khi tưởng hàm này thừa) ────────────────────────────────────────────────────────────
 * Bất biến này từng có MỘT call site thứ hai ở đường DATA: `CaptureArrowFallback` (08-22) mượn mũi tên
 * screen-capture vào khung notification và chốt `sameFrame` trước khi mượn. Cơ chế mượn đã bị **GỠ 08-23**
 * (owner chốt VietMap đi capture-only; xem `NavRepository.createCoordinator` + backlog B3.42) — nhưng
 * [NavFrameIdentity] thì **GIỮ NGUYÊN**: nó là luật của đường ẢNH (`NavOutputDecision.decide`,
 * `ScreenCaptureNavSource`, `LaneBoundsSource`), nơi mới là chỗ bất biến này thật sự gác cửa.
 */
object NavFrameIdentity {

    /**
     * [candidate] có thuộc đúng khung của [frame] không.
     *
     * @param frame     package DANH TÍNH của khung đang dựng (null/rỗng = chưa có khung ⇒ luôn false).
     * @param candidate package của kênh đang xét (null/rỗng = không rõ chủ ⇒ luôn false).
     */
    fun sameFrame(frame: String?, candidate: String?): Boolean =
        !frame.isNullOrEmpty() && frame == candidate
}
