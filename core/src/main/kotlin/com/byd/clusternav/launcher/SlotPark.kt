package com.byd.clusternav.launcher

/**
 * ═══ Ô 7 — ĐỖ ẨN app của ô (2.89-thử1 · bản THỬ) — phần THUẦN ═══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-287-look-and-keys.html` §4.6d · backlog `SLOT-PARK-HIDDEN`. Owner 05/10, đang ở trên xe:
 * *"sao ko giả lập 1 ô số 7 gì đó, để nhét các app chạy nền vào đó"* · *"thử cho nó vào nền đi xem nào?"*.
 *
 * [ĐO xe 05/10, Seal DL3, 2.88]: (1) giữ chỗ BEHIND-HOME ném NPE trong system_server ⇒ mọi lượt đẩy app ra sau màn nhà
 * hỏng; (2) dời YouTube từ màn ảo ô sang display 0 ⇒ activity RELAUNCH, dừng phát hẳn. ⇒ đổi display = relaunch. Đổi CỠ màn ảo
 * đang chạy (mọi ô vẫn làm khi inset đổi) thì CHƯA đo có relaunch không: [ĐO nguồn r47] activity chỉ dựng lại khi thay đổi (cỡ vượt
 * ngưỡng tài nguyên của app · mật độ) không nằm trong `configChanges` của nó — KDoc `ParkedApps.claim`. 2.91 · F2 đổi cỡ khi nhận
 * lại vào ô khác cỡ (🚗 OC-291-2).
 * Ô 7 = app ở YÊN trong chính màn ảo của nó; chỉ mặt vẽ đổi sang một bề mặt không ai xem (`ParkedApps` ở `:app`).
 *
 * Hai phần thuần (test off-device): [ParkLedger] — sổ app đang đỗ (thứ tự, trần, đỗ lại cùng gói) · [SlotParkPlan] — app RỜI
 * ô ở một lượt dựng lại thì ĐỖ hay NHẢ như hôm nay, host đỗ được không, và lượt nhận lại có phải đổi cỡ màn ảo theo ô không.
 */
class ParkLedger<T>(private val cap: Int = CAP) {

    init { require(cap >= 1) { "cap >= 1" } }

    /** Một màn ảo phải NHẢ do lượt [park]: [why] = đỗ lại cùng gói ([Why.SAME_PKG]) hay vượt trần ([Why.CAP], cũ nhất trước). */
    data class Evicted<T>(val pkg: String, val handle: T, val why: Why)

    enum class Why { SAME_PKG, CAP }

    private val lock = Any()

    /** Thứ tự chèn = thứ tự đỗ (cũ nhất đứng đầu). */
    private val entries = LinkedHashMap<String, T>()

    /**
     * Đỗ [pkg] với [handle]. Trả danh sách bên gọi PHẢI nhả: bản đỗ cũ của CÙNG gói (một app chỉ một chỗ đỗ) rồi các bản cũ
     * nhất vượt [cap]. Bản vừa đỗ không bao giờ nằm trong danh sách; gói trong [protect] cũng không (app SẮP được nhận lại
     * ở cùng lượt dựng lại — đặt tạm app đỗ cũ nhất vào ô đang có app khác: đỗ app cũ TRƯỚC, nhận lại SAU). Chỉ còn bản được
     * che chắn ⇒ sổ vượt trần TẠM (bên nhận lấy ra ngay sau đó; lượt đỗ kế tiếp đưa về trần).
     */
    fun park(pkg: String, handle: T, protect: Set<String> = emptySet()): List<Evicted<T>> = synchronized(lock) {
        val out = ArrayList<Evicted<T>>()
        entries.remove(pkg)?.let { out += Evicted(pkg, it, Why.SAME_PKG) }
        entries[pkg] = handle
        val victims = entries.keys.filter { it != pkg && it !in protect }.iterator()
        while (entries.size > cap && victims.hasNext()) {
            val oldest = victims.next()
            out += Evicted(oldest, entries.getValue(oldest), Why.CAP)
            entries.remove(oldest)
        }
        out
    }

    /** Lấy RA (gỡ khỏi sổ) bản đỗ của [pkg] — `null` = không đỗ. Nhận lại vào ô là lấy ra: một màn ảo, một chủ. */
    fun take(pkg: String): T? = synchronized(lock) { entries.remove(pkg) }

    /** XEM bản đỗ của [pkg] mà KHÔNG lấy ra — chỉ ĐỌC (`ParkedApps.vdOf`: màn ảo cho bước nhạc của chuyến). Nhận lại vào ô = [take]. */
    fun peek(pkg: String): T? = synchronized(lock) { entries[pkg] }

    fun has(pkg: String): Boolean = synchronized(lock) { pkg in entries }

    /** Gói đang đỗ, cũ nhất trước (nhật ký). */
    fun pkgs(): List<String> = synchronized(lock) { entries.keys.toList() }

    companion object {
        /** Trần ô 7 (bản thử): tối đa 3 app đỗ — đỗ app thứ 4 ⇒ nhả màn ảo đỗ CŨ NHẤT. Chi phí mỗi app: xem §4.6d OQ. */
        const val CAP = 3
    }
}

object SlotParkPlan {

    /** App rời ô ở một lượt dựng lại: [PARK] = đỗ vào ô 7 (không `force-stop`) · [RELEASE] = nhả như hôm nay. */
    enum class Leave { PARK, RELEASE }

    /**
     * Host đỗ được: có màn ảo, đã ra lệnh mở và lượt mở đã XONG ([watching] — nhịp đo ô sống đã nhận app), chưa nhả, chưa báo
     * chết, app không đang mở toàn màn ở display 0 ([detached] — task của nó không còn trên màn ảo). Lượt mở còn dở ⇒ không
     * đỗ: đỗ một màn ảo chưa có app là để lại khung đen vĩnh viễn khi nhận lại (`SlotLiveness` không kết luận chết trước khi
     * thấy sống).
     */
    fun parkable(
        released: Boolean,
        launched: Boolean,
        hasVd: Boolean,
        pkg: String?,
        dead: Boolean,
        detached: Boolean,
        watching: Boolean,
    ): Boolean = !released && launched && hasVd && !pkg.isNullOrBlank() && !dead && !detached && watching

    /**
     * Ô [index] dựng lại từ [old] sang [new] ([next] = cả bố cục đang HIỆN sau lượt này). App cũ được ĐỖ khi nó còn được
     * dùng tiếp: một app KHÁC vào ô (đặt tạm · lối tắt · giọng nói · ⇄ · ngăn kéo) hoặc chính nó sang ô khác (kéo-thả, một-
     * app-một-ô). Ô bị xoá / thành widget / cùng app dựng lại ⇒ [Leave.RELEASE] (đường hôm nay, CLAUDE.md §6). Host có đỗ
     * được không là việc của [parkable] tại chỗ thi hành — không đỗ được thì vẫn nhả như hôm nay.
     */
    fun leave(old: SlotContent, new: SlotContent, next: List<SlotContent>, index: Int, profileSwitch: Boolean = false): Leave {
        // Review 2.89 Pass 3 · whole-r2-2 — đổi HỒ SƠ không phải một trong các lối đỗ của spec 287 §4.6d dòng B (đặt tạm · lối tắt ·
        // giọng nói · ⇄ · kéo-thả): nhả như 2.88, CÙNG kết cục với đổi hồ sơ khác bố cục (RebuildAll ⇒ nhả) — không để tới 3 app của
        // người lái trước chạy ẩn dưới hồ sơ người sau mà không có danh sách ô 7 nào để tắt (OQ-P4).
        if (profileSwitch) return Leave.RELEASE
        val a = (old as? SlotContent.App)?.pkg ?: return Leave.RELEASE
        val b = (new as? SlotContent.App)?.pkg
        if (b == a) return Leave.RELEASE
        if (b != null) return Leave.PARK
        val moved = next.withIndex().any { (j, c) -> j != index && (c as? SlotContent.App)?.pkg == a }
        return if (moved) Leave.PARK else Leave.RELEASE
    }

    /**
     * Một bước của lượt NHẬN LẠI (`ParkedApps.claim` ở `:app` chỉ THI HÀNH bước này): [GOLDEN] = không có bản đỗ ⇒ đường thường
     * (tạo màn ảo mới) · [ATTACH] = cùng cỡ ⇒ lấy ra + gắn · [ATTACH_RESIZE] = khác cỡ ⇒ lấy ra + gắn rồi ĐỔI CỠ màn ảo theo ô
     * (`VdAppHost.resize` — đúng đường đổi cỡ mọi ô đã chạy khi inset đổi).
     */
    enum class ClaimStep { GOLDEN, ATTACH, ATTACH_RESIZE }

    /**
     * 2.91 · F2 (spec `kachi-291-small-fixes.html` §4.2) — QUYẾT lượt nhận lại. [parkedW]/[parkedH] = cỡ màn ảo đỗ của gói (`null` =
     * không đỗ / bản đỗ đã mất), [w]×[h] = cỡ mặt vẽ ô của lượt `surfaceChanged` này.
     *
     * Trước 2.91 khác cỡ ⇒ ghim cỡ mặt vẽ theo màn ảo đỗ + khung viền giữ tỉ lệ ⇒ [ĐO máy ảo QA 05/10] app 1129×610 nằm trong ô
     * 1129×804: viền đen, góc trong vuông. Nay khác cỡ ⇒ màn ảo đổi cỡ theo ô (CÙNG display — app không dời màn, cùng tiến trình;
     * app nhận một lượt đổi cấu hình như khi ô đổi cỡ do inset). Cỡ ô hỏng (≤ 0) ⇒ gắn, không đổi cỡ.
     */
    fun claim(parkedW: Int?, parkedH: Int?, w: Int, h: Int): ClaimStep = when {
        parkedW == null || parkedH == null -> ClaimStep.GOLDEN
        w <= 0 || h <= 0 || (w == parkedW && h == parkedH) -> ClaimStep.ATTACH
        else -> ClaimStep.ATTACH_RESIZE
    }

    /** Gói app sẽ HIỆN trong bố cục [next] — được che chắn khỏi trần ô 7 ở lượt đỗ cùng lượt dựng lại ([ParkLedger.park]). */
    fun shown(next: List<SlotContent>): Set<String> = next.mapNotNullTo(HashSet()) { (it as? SlotContent.App)?.pkg }
}
