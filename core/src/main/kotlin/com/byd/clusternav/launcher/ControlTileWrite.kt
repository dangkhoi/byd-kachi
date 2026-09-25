package com.byd.clusternav.launcher

/**
 * ═══ ĐƯỜNG GHI CỦA Ô ĐƠN — nền · tuần tự · lạc quan-rồi-hoàn-nguyên (2026-09-25 · P1-main) ═══════════════════
 *
 * Spec `kachi-closeout-hardening.html` R4(b), audit F10 [P1]: chạm một ô TOGGLE/STEP/COVER/SELECT/BUTTON ⇒
 * `control().toggle/step/…` → `HalBindingTable.write` → `BydHal` reflection → binder HAL **đồng bộ trên luồng
 * chính**. [ĐO xe 09-16] một lượt HAL ≈ 23 ms, ghi có thể lâu hơn; STEP còn ĐỌC (`readState`) trước khi ghi ⇒
 * 2 lượt/cú chạm; vài cú chạm liên tiếp = giật khung. Gói lệnh và giọng nói đã xuống nền qua [MacroExec]; ô đơn là
 * chỗ cuối còn ghi trên luồng vẽ.
 *
 * Lớp này chỉ **cầm lấy cú ghi** và đưa nó qua [bg] (mặc định = làn tuần tự [MacroExec.submitSerial] trên pool
 * dùng chung — không dựng pool mới). Hợp đồng với chỗ gọi (`ControlTileFactory`):
 *  1. **UI đổi lạc quan NGAY** ở chỗ gọi (trước khi [submit]) — y như trước; lớp này không chạm view.
 *  2. **Thứ tự** hai cú bấm liên tiếp (cùng nút hay khác nút) = thứ tự HAL nhận — do làn tuần tự.
 *  3. Kết quả `false` hay ném ⇒ [warn] **một dòng** (không nuốt im) rồi [revert] — nhưng CHỈ khi [stillMine] còn
 *     đúng: cú bấm SAU đã đè giá trị lạc quan thì lượt trước không được kéo ngược nó về (compare-and-revert).
 *  4. [revert] chạy trên chính luồng nền: phần DỮ LIỆU (`ControlTileState`, map đồng thời) sửa ngay tại đó; phần
 *     VẼ chỗ gọi tự `post` — cùng lẽ với `macroTile` (view đã bị gỡ thì `post` có thể không bao giờ chạy, dữ liệu
 *     không được phụ thuộc vào đó).
 *
 * `false` là thất bại thật: `CarControlAdapter.ok` = rc khác null và khác sentinel — cùng luật với `MacroRunner`
 * (bước trả `false` = *"bước không ăn"*). NHƯNG hoàn nguyên chỉ có nghĩa khi cú `false` ấy đến từ **một chiếc xe
 * thật** ([failureIsReal], chỗ gọi truyền `CarControlPort.writeFailureIsReal`): off-car / máy ảo / nút xe không có thì
 * `false` chỉ là *"không biết"* — kéo ô về là nói sai theo chiều ngược (senior review Pass 1 [P2]); giữ hành vi lạc
 * quan như trước, nhật ký W vẫn ghi.
 *
 * ⚠ [SOÁT Pass 2 · 2026-09-26] Cổng này **không** được hỏi `wiredOnThisCar`: hàm ấy trả `true` cho cả *"có nút"* lẫn
 * *"không biết"* nên off-car vẫn nảy ô về — đúng thứ Pass 1 [P2] nêu. Xem KDoc `CarControlPort.writeFailureIsReal`.
 *
 * Thuần JVM (không `android.*`) ⇒ ở `:core`, test được với [bg] giả (`LayeringRulesTest` Q1).
 */
class ControlTileWrite(
    /** Ai cầm luồng. Mặc định: làn [LANE] của [MacroExec] — tuần tự, daemon, có trần. Test tiêm bản đồng bộ. */
    private val bg: (() -> Unit) -> Unit = { MacroExec.submitSerial(LANE, it) },
    /** Nhật ký mức W — `:core` không có `android.util.Log`, chỗ gọi truyền vào. */
    private val warn: (String, Throwable?) -> Unit,
) {

    /**
     * Ghi [act] cho nút [id] trên nền. `false`/ném ⇒ [warn] + (nếu [failureIsReal] **và** [stillMine]) [revert].
     * [act] có thể gồm cả lượt ĐỌC trước khi ghi (STEP: `readState` → cộng → `step`).
     *
     * [failureIsReal] mặc định `true` = *"cứ tin cú `false`"*, để chỗ gọi KHÔNG có đường hỏi xe (và mọi bài kiểm cũ)
     * giữ nguyên hành vi fail-loud; bốn chỗ gọi thật truyền câu hỏi thật. Câu hỏi ấy NÉM ⇒ coi là *"không biết"*
     * (KHÔNG hoàn nguyên): nó chạy qua reflection HAL, một ngoại lệ ở đó không phải bằng chứng xe đã từ chối.
     */
    fun submit(
        id: String,
        act: () -> Boolean,
        stillMine: () -> Boolean = { true },
        failureIsReal: () -> Boolean = { true },
        revert: () -> Unit,
    ) = bg {
        val ok = try {
            act().also { if (!it) warn("$id: ghi HAL trả false", null) }
        } catch (t: Throwable) {
            warn("$id: ghi HAL ném", t)
            false
        }
        if (ok) return@bg
        if (!runCatching { failureIsReal() }.getOrDefault(false)) {
            warn("$id: off-car / không biết xe có nút — giữ giá trị lạc quan, không hoàn nguyên", null)
            return@bg
        }
        if (stillMine()) revert()
    }

    companion object {
        /** Tên làn (cũng là tên luồng lúc chạy — `dumpsys`/nhật ký đọc ra "ô đơn đang ghi"). */
        const val LANE = "tile-write"
    }
}
