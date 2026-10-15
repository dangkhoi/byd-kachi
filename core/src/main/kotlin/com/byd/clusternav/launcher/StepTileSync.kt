package com.byd.clusternav.launcher

/**
 * ═══ 3.1.7 · FAN-STEP — ô STEPPER vẽ lại theo HÌNH ĐANG HIỆN, không theo bảng lệnh dùng chung (thuần `:core`) ═══════════════
 *
 * Owner 15/10 (bản 3.1.6, các bản trước cũng thế): *"đang từ 2, lăn nút lên 3, thì vẫn chỉ hiện 2 mà không hiện 3, nhưng từ 1 lăn
 * xuống thì hiện auto ngay"*. Chẩn đoán: `docs/diagnostics/fan-step-2026-10-15.md`.
 *
 * ## Bệnh [ĐO log xe 14/10 16:54:29 + đọc nguồn]
 * Con lăn vô-lăng (`KachiKey k=291` ⇒ `KeyCtl ctl:fan:+1`) ghi `fan=3` (`KachiCtl … args=[3] before=2`, `ctl-after … after=3`).
 * Đường phím/giọng nói ([VoiceControlDispatch] `finish`) ghi số vừa gửi vào **bảng dùng chung** `ControlTileState.shared` — nhưng
 * KHÔNG vẽ ô. Tới lượt đọc xe, `refresh` của ô so số đọc (3) với **chính bảng đó** (đã là 3) ⇒ *"không đổi"* ⇒ chữ vẫn "2". Cờ AUTO
 * thì ô so với biến RIÊNG của ô (cái nó đang hiện) ⇒ 1 → AUTO hiện ngay. Hai nửa của một ô dùng hai mốc so khác nhau.
 *
 * ## Luật
 * So với cái ô **đang VẼ** ([drawn]/[drawnAuto]) — đúng cách ô TOGGLE đã làm từ 2.87 (`TileResync.stale`). Ai ghi bảng (chạm · phím
 * · giọng nói · gói lệnh) không quan trọng: số xe khác hình ⇒ vẽ lại. Generic cho MỌI ô STEP (nhiệt · gió · âm lượng), không
 * `if (id == "fan")`.
 */
object StepTileSync {

    /** `true` ⇔ ô đang hiện [drawn] (cờ AUTO [drawnAuto]) mà xe vừa đọc ra [read] ([readAuto]) ⇒ phải vẽ lại. */
    fun needsRedraw(drawn: Int?, drawnAuto: Boolean?, read: Int, readAuto: Boolean?): Boolean =
        drawn != read || drawnAuto != readAuto
}
