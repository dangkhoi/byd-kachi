package com.byd.clusternav.launcher

/**
 * Một lượt đọc có **hai** cổng. Chụp tập nhu cầu + mốc giờ MỘT lần cho cả nhịp (gọi [demand] 123 lần mỗi nhịp
 * là đúng kiểu chi phí mà cổng này sinh ra để cắt), rồi mỗi field hỏi hai câu:
 *  1. *màn có đang bày không* ([CarDataDemand]) — nếu không thì GIỮ giá trị cũ;
 *  2. *xe này có không* ([HalAbsentCache]) — datum đã `null` liên tiếp thì giãn nhịp hỏi lại.
 *
 * Thứ tự QUAN TRỌNG: hỏi nhu cầu TRƯỚC. Một datum không hiện thì không được tính là "miss" — nếu không, cái
 * không đọc lại tự nguội, rồi lúc người dùng kéo nó lên màn thì nó câm cho tới lượt thử lại.
 */
internal class CarReadGate(
    private val table: HalBindingTable,
    private val want: Set<String>?,
    private val absent: HalAbsentCache,
    val now: Long,
    /** 3.1.6 HAL-EVENTS — lượt [CarDataAdapter.readEvents] cố ý chỉ đọc vài datum: các field còn lại KHÔNG phải "bỏ vì không hiện" ⇒ không đếm. */
    private val count: Boolean = true,
) {
    /** Màn có bày [id] không — như [wanted] nhưng KHÔNG đếm [KachiPerf] (hỏi để quyết, không phải một lượt bỏ đọc). */
    fun wants(id: String): Boolean = want?.contains(id) ?: true

    private fun wanted(id: String): Boolean {
        val w = want ?: return true
        if (id in w) return true
        if (count) KachiPerf.add(KachiPerf.Counter.HAL_SKIP_OFFSCREEN)
        return false
    }

    /**
     * ⚠ Hai lối bỏ qua trả về HAI thứ khác nhau, có chủ ý:
     *  • *không hiện* ⇒ trả [prev] — ta KHÔNG biết gì mới, mà cũng chưa kết luận gì; xoá đi thì lúc ô quay lại
     *    màn hình nó nháy "—" một nhịp dù dữ liệu cũ vẫn còn đúng.
     *  • *đang nguội* ⇒ trả `null` (= "—") — ta ĐÃ kết luận datum này đọc không ra. Trả [prev] ở đây là đóng
     *    băng con số cuối cùng đọc được, tức ô hiện một giá trị CŨ mà trông như đang sống (đúng bệnh
     *    "SurfaceView giữ khung hình cuối" mà `SlotLiveProbe` sinh ra để chữa, lần này bằng số).
     */
    private inline fun <T> read(id: String, prev: T?, body: () -> T?): T? =
        if (!wanted(id)) prev else fresh(id, body)

    /** Lượt đọc THẬT (đã qua cổng vắng) — `null` ở đây = *"đọc không ra"* (⚠), KHÔNG phải *"không hiện"*. */
    private inline fun <T> fresh(id: String, body: () -> T?): T? {
        if (!absent.shouldRead(id, now)) { KachiPerf.add(KachiPerf.Counter.HAL_SKIP_ABSENT); return null }
        val v = body()
        absent.record(id, v != null, now)
        return v
    }

    fun int(id: String, prev: Int?): Int? =
        if (id in HalReadTables.INVALID_IS_PENDING) pending(id, prev) { HalBindingTable.parseInt(id, it) }
        else read(id, prev) { table.readInt(id) }
    fun dbl(id: String, prev: Double?): Double? =
        if (id in HalReadTables.INVALID_IS_PENDING) pending(id, prev, HalBindingTable::coerceDouble)
        else read(id, prev) { table.readDouble(id) }

    /**
     * Soát 2.88 Pass 2 — datum mà mã "không hợp lệ" nghĩa là *"chưa có số"* ([HalReadTables.INVALID_IS_PENDING]):
     * hiện y như [read] (mã ⇒ `null` = "—"), nhưng cache vắng ghi *"getter CÓ trả lời"* nên KHÔNG nguội vì mã ấy —
     * số thật đến là nhịp kế thấy ngay, không chờ một lượt thử lại 60 s … 10 phút. Getter vắng thật (sentinel /
     * không tra được tên ⇒ [HalBindingTable.answerRaw] `null`) vẫn nguội như mọi datum.
     *
     * 2.93 · HAL-PENDING-PARSE-DRY — *"getter đã trả lời"* = MÃ chưa-có-số HOẶC một con số [parse] đọc được; chuỗi lạ
     * không parse nổi (vd `int=- float=- buf=4`) nay nguội như [fresh] (bản trước coi MỌI chuỗi khác `null` là đã trả
     * lời ⇒ đọc lại mỗi nhịp mãi). Phép parse Int là [HalBindingTable.parseInt] — chung với [HalBindingTable.readInt].
     */
    private inline fun <T> pending(id: String, prev: T?, parse: (String?) -> T?): T? {
        if (!wanted(id)) return prev
        if (!absent.shouldRead(id, now)) { KachiPerf.add(KachiPerf.Counter.HAL_SKIP_ABSENT); return null }
        val raw = table.answerRaw(id)
        val code = raw != null && HalBindingTable.isInvalidValue(id, raw)
        val v = if (code) null else parse(raw)
        // HAL-PENDING-PARSE-DRY (2.93 MISC): "đã đáp" = mã không hợp lệ HOẶC số đọc được — chuỗi rác ⇒ nguội như vắng.
        absent.record(id, code || v != null, now)
        asked += id
        // 2.93 TYRE-BURST-REASON: mã "không hợp lệ" vẫn là LỜI ĐÁP của xe — giữ lại cho [code] (cùng lượt đọc, 0 HAL thêm).
        // Soát senior WIDGET Pass 1 [P3]: đọc mã bằng CÙNG `parseInt` mà `isInvalidValue` vừa dùng để quyết [code] — một phép parse.
        if (code) codes[id] = HalBindingTable.parseInt(id, raw)
        return v
    }

    /** Datum [pending] đã HỎI xe ở nhịp này + mã "không hợp lệ" nó trả (2.93 `TYRE-BURST-REASON`). */
    private val asked = HashSet<String>()
    private val codes = HashMap<String, Int?>()

    /**
     * Mã [HalReadTables.INVALID_VALUES] mà getter của [id] trả ở nhịp này (`null` = số thường / không đọc được); nhịp
     * KHÔNG hỏi [id] (không hiện) ⇒ [prev] — cùng luật hai lối bỏ qua của [read]. Không gọi HAL nào.
     */
    fun code(id: String, prev: Int?): Int? = if (id in asked) codes[id] else if (!wants(id)) prev else null

    /**
     * Như [dbl] nhưng giá trị ĐỌC THẬT đi qua [via] (bộ làm mượt CÓ TRẠNG THÁI — [TripTimeSmoother]); lối *"không
     * hiện ⇒ trả prev"* thì **KHÔNG** đi qua: `prev` là số đã làm mượt, đưa nó vào lại như một mốc HAL là tự nâng mốc
     * (1.8 → 1.85 bị coi là bậc tăng) rồi vượt bậc kế tiếp — đúng cái bộ làm mượt sinh ra để tránh. `null` (nguội /
     * off-car) vẫn qua [via] để nó reset và trả `null` = "—".
     */
    fun dblVia(id: String, prev: Double?, via: (Double?) -> Double?): Double? =
        if (!wanted(id)) prev else via(fresh(id) { table.readDouble(id) })
    fun bool(id: String, prev: Boolean?): Boolean? = read(id, prev) { table.readBool(id) }
    fun str(id: String, prev: String?): String? = read(id, prev) { table.readString(id) }
}
