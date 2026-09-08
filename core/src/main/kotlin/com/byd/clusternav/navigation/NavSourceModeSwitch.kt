package com.byd.clusternav.navigation

import com.byd.clusternav.navigation.screencapture.ScreenCaptureSignal

/**
 * B3.49 — ÁP LỰA CHỌN NGUỒN MỚI **TỨC THÌ** khi người dùng đổi menu "Nguồn dẫn đường".
 *
 * ── BỆNH (cơ chế đọc thẳng từ source 2026-08-23, chưa đo trên xe) ─────────────────────────────────────
 * Trước bản này, đổi menu chỉ ghi `Prefs.setSourceMode` rồi vẽ lại màn hình. Hai cổng nguồn
 * ([SourceArbiter.shouldFeed] ở `NavNotificationListener` và ở `ScreenCaptureNavSource`) chặn được ngay,
 * nhưng chúng chỉ chặn khung **MỚI**. Người tiêu thụ của đường ẢNH — `NavOutputOwner.tick` — quyết định
 * bắn **chỉ** theo độ tươi của ba sample trong [ScreenCaptureSignal]; nó không đọc `Prefs`, không hỏi
 * [SourceArbiter]. Mẫu **CŨ** vì thế còn tươi tới `ScreenCaptureSignal.STALE_MS` = 6 000 ms ⇒ tài xế đang
 * dẫn bằng Waze mà chọn "VietMap" (VietMap không dẫn) thì cụm **vẫn vẽ mũi tên Waze thêm tới 6 giây**.
 * Lời hứa của owner ở B3.48 — *"chọn đích danh app thì nếu app đó không dẫn thì không hiện gì"* — khi đó
 * đúng-CÓ-ĐỘ-TRỄ, không tức thì.
 *
 * ⚠ **CA HIỆN TRƯỜNG LÀ Waze/VietMap, KHÔNG PHẢI GMaps** (đính chính 08-23 vòng 2 — bản đầu của KDoc này
 * lấy GMaps làm ví dụ, [ĐO] `PROBE-B` bác): GMaps đang dẫn thì noti ~1 Hz giữ `isDataFresh(gmaps)` = true,
 * mà `SourceArbiter.shouldFeed(…, IMAGE)` từ chối kênh ẢNH của gói có mốc DATA còn tươi ⇒ lúc đang dẫn,
 * GMaps gần như không bao giờ là chủ kênh ảnh. Với GMaps, cửa đóng cụm đã là `stopClusterOwnedBy` của
 * B3.48. Hai app **chỉ có kênh ảnh** (họ Waze, VietMap) mới là người hưởng lợi thật của hạng mục này.
 *
 * ── CÁCH CHỮA (chỉ THU HẸP) ──────────────────────────────────────────────────────────────────────────
 * Ngay tại chỗ đổi mode, bỏ các kênh ảnh mà cổng [NavSourceMode] mới **không cho phép**
 * ([ScreenCaptureSignal.dropDisallowed]). Không cổng nào được nới, không mốc nào được đặt thêm, không app
 * nào được lên thay ⇒ không thể làm đường GMaps/Waze đã proven ngoài hiện trường ra thêm một khung nào
 * (CLAUDE.md §6).
 *
 * ⚠ **GỠ SAMPLE KHÔNG PHẢI LÀ XONG** (vòng 2, [ĐO] `PROBE-A`): gỡ sample chỉ chặn nhịp bắn SAU. Nội dung đã
 * ghi vào register HAL nằm lại tới khi khung được **nhả**, mà `NavOutputOwner` chỉ nhả khi **cả ba** kênh
 * hết tươi (`NavOutputDecision` — `clear = !anyFresh`). Nếu một app khác (vẫn được phép) còn giữ một kênh
 * tươi thì khung sống tiếp và mũi tên của app vừa bị loại **ở lại vô hạn**, không phải 6 giây. Vì thế
 * [ScreenCaptureSignal.dropDisallowed] còn đặt yêu cầu nhả khung mà owner đọc mỗi nhịp
 * ([ScreenCaptureSignal.consumeFrameRelease]) — và owner **chỉ** nhả khi danh tính khung đang hiện đúng là
 * gói vừa bị loại (nhả oan = nháy khung của chính app vừa được chọn).
 *
 * ── HAI ĐIỀU **KHÔNG** ĐƯỢC LÀM Ở ĐÂY ────────────────────────────────────────────────────────────────
 * 1. **KHÔNG gọi [SourceArbiter.clear]** — dù backlog B3.49 đề xuất vậy lúc đầu. Ba lý do đọc được từ
 *    source: (a) nó đặt `activeSource = null`, mà `NavNotificationListener` phát lệnh dừng cụm ĐÚNG khi
 *    `SourceArbiter.release(pkg)` = true (B3.48) ⇒ xoá trước là nuốt luôn lệnh dừng, để nhịp tim ghim khung
 *    cuối tới 180 s (`HudKeepAlivePolicy.DEFAULT_MAX_AGE_MS` / `ClusterBroadcaster.STALE_MS`); (b) nhánh
 *    AUTO của [SourceArbiter.allows] là `h == null || h == pkg || stale`, nên `activeSource = null` **mở
 *    cổng cho MỌI gói** — nới, không phải siết; (c) `lastDataByPkg.clear()` gỡ mốc DATA đang chặn kênh ẢNH
 *    của cùng gói (`shouldFeed`, tầng kênh R6) — cũng là nới.
 * 2. **KHÔNG xoá cả ba kênh vô điều kiện** ([ScreenCaptureSignal.clear]). Đổi sang AUTO, hoặc chọn đúng app
 *    đang dẫn, thì khung hợp lệ sẽ bị nhả rồi dựng lại sau ~500 ms ⇒ cụm nháy. Nháy là "hiện SAI".
 *
 * Thuần Kotlin (không Android): [persist] là seam ghi `Prefs` do `:app` truyền vào, nên toàn bộ luật này
 * test được off-car.
 */
object NavSourceModeSwitch {

    /**
     * Người dùng vừa chọn [selectedMode] trong menu nguồn; [previousMode] là giá trị đang lưu
     * (`Prefs.sourceMode`). Trả **true** nếu mode THỰC SỰ đổi (đã ghi + đã bỏ kênh không còn được phép).
     *
     * ⚠ **BẮT BUỘC so với mode ĐANG LƯU, không được chạy vô điều kiện**: `Spinner` của Android bắn
     * `onItemSelected` cả khi `setSelection()` lúc dựng màn hình, nên chạy vô điều kiện = mỗi lần mở app
     * lại xoá oan kênh ảnh của app đang dẫn. Trùng mode ⇒ **no-op tuyệt đối** (không ghi prefs, không đụng
     * [ScreenCaptureSignal]).
     */
    fun onModeSelected(previousMode: Int, selectedMode: Int, persist: (Int) -> Unit): Boolean {
        if (selectedMode == previousMode) return false
        persist(selectedMode)
        // `now` KHÔNG được [SourceArbiter.allowedByMode] dùng (cổng PREFER_* chỉ là `pkg in nhóm`, không đọc
        // đồng hồ — xem KDoc của nó). Truyền hằng để khỏi kéo một nguồn thời gian vào một luật không cần.
        // Trả về = các gói vừa bị gỡ kênh; hàm đó tự ghi yêu cầu nhả khung cho `NavOutputOwner` (một nơi, để
        // không call site nào gỡ được kênh mà quên nhả khung — CLAUDE.md §8).
        ScreenCaptureSignal.dropDisallowed { pkg -> SourceArbiter.allowedByMode(pkg, selectedMode, MODE_GATE_IGNORES_CLOCK) }
        return true
    }

    /** Xem chú thích trong [onModeSelected]: cổng theo mode không đọc đồng hồ. */
    private const val MODE_GATE_IGNORES_CLOCK = 0L
}
