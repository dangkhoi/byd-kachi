package com.byd.clusternav.launcher

/**
 * ═══ Ô 7 — ĐỖ ẨN app của ô (2.89-thử1 · bản THỬ) — phần THUẦN ═══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-287-look-and-keys.html` §4.6d · backlog `SLOT-PARK-HIDDEN`. Owner 05/10, đang ở trên xe:
 * *"sao ko giả lập 1 ô số 7 gì đó, để nhét các app chạy nền vào đó"* · *"thử cho nó vào nền đi xem nào?"*.
 *
 * [ĐO xe 05/10, Seal DL3, 2.88]: (1) giữ chỗ BEHIND-HOME ném NPE trong system_server ⇒ mọi lượt đẩy app ra sau màn nhà
 * hỏng; (2) dời YouTube từ màn ảo ô sang display 0 ⇒ activity RELAUNCH, dừng phát hẳn. ⇒ đổi display / đổi cỡ = relaunch.
 * Ô 7 = app ở YÊN trong chính màn ảo của nó; chỉ mặt vẽ đổi sang một bề mặt không ai xem (`ParkedApps` ở `:app`).
 *
 * Hai phần thuần (test off-device): [ParkLedger] — sổ app đang đỗ (thứ tự, trần, đỗ lại cùng gói) · [SlotParkPlan] — app RỜI
 * ô ở một lượt dựng lại thì ĐỖ hay NHẢ như hôm nay, host đỗ được không, và khung viền đen khi cỡ ô ≠ cỡ màn ảo đỗ.
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

    /** XEM bản đỗ của [pkg] mà KHÔNG lấy ra (PARK-1: mặt vẽ ô đổi cỡ trước, chỉ lấy ra khi đã đúng cỡ). */
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
     * Một bước của lượt NHẬN LẠI (PARK-1, `ParkedApps.claim` ở `:app` chỉ THI HÀNH bước này): [GOLDEN] = đường thường (tạo màn ảo mới)
     * · [FIT_WAIT] = ghim cỡ mặt vẽ theo màn ảo đỗ + khung giữa ô, CHỜ lượt `surfaceChanged` kế (không lấy ra) · [ATTACH] = đúng cỡ ⇒
     * lấy ra + gắn · [UNFIT_GOLDEN] = bỏ khung rồi đường thường NGAY (host đã đúng cỡ, không có lượt kế) · [UNFIT_WAIT] = bỏ khung,
     * chờ lượt `surfaceChanged` kế ở cỡ ô.
     */
    enum class ClaimStep { GOLDEN, FIT_WAIT, ATTACH, UNFIT_GOLDEN, UNFIT_WAIT }

    /**
     * Review 2.89 Pass 3 · whole-r2-6 — QUYẾT lượt nhận lại (thuần, trước đây nằm rải trong `ParkedApps.claim` và chỉ được canh bằng
     * thứ tự dòng mã). [parkedW]/[parkedH] = cỡ màn ảo đỗ của gói (`null` = không đỗ / bản đỗ đã mất), [w]×[h] = cỡ mặt vẽ ô của lượt
     * `surfaceChanged` này, [hostW]×[hostH] = cỡ host hiện tại, [pinned] = mặt vẽ đang ghim cỡ màn ảo đỗ (lượt trước đã [ClaimStep.FIT_WAIT]).
     * Cỡ ≠ ⇒ KHÔNG BAO GIỜ gắn ([ĐO nguồn A10 r47 native] SurfaceFlinger chốt cỡ đích lúc đổi mặt vẽ — KDoc `ParkedApps.claim`).
     */
    fun claim(parkedW: Int?, parkedH: Int?, w: Int, h: Int, hostW: Int, hostH: Int, pinned: Boolean): ClaimStep = when {
        parkedW == null || parkedH == null -> lost(pinned, w, h, hostW, hostH)
        w != parkedW || h != parkedH -> ClaimStep.FIT_WAIT
        else -> ClaimStep.ATTACH
    }

    /**
     * Bản đỗ không còn để gắn (không đỗ · bị trần nhả giữa hai lượt · lấy ra hỏng · gắn hỏng): chưa ghim ⇒ đường thường; đang ghim ⇒
     * bỏ khung, rồi đường thường ngay khi host đã đúng cỡ [w]×[h], không thì chờ lượt kế (cỡ mặt vẽ ĐỔI).
     */
    fun lost(pinned: Boolean, w: Int, h: Int, hostW: Int, hostH: Int): ClaimStep = when {
        !pinned -> ClaimStep.GOLDEN
        hostW == w && hostH == h -> ClaimStep.UNFIT_GOLDEN
        else -> ClaimStep.UNFIT_WAIT
    }

    /** Gói app sẽ HIỆN trong bố cục [next] — được che chắn khỏi trần ô 7 ở lượt đỗ cùng lượt dựng lại ([ParkLedger.park]). */
    fun shown(next: List<SlotContent>): Set<String> = next.mapNotNullTo(HashSet()) { (it as? SlotContent.App)?.pkg }

    /**
     * Nhận lại vào ô có cỡ [w]×[h] một màn ảo đỗ cỡ [pw]×[ph]: KHÔNG đổi cỡ màn ảo (đổi cỡ = đổi cấu hình = relaunch — [ĐO
     * xe 05/10] (2)); mặt vẽ ô giữ cỡ màn ảo và được thu/phóng GIỮ TỈ LỆ vào giữa ô (viền hai bên). Trả `[rộng, cao]` của
     * khung vẽ, hoặc `null` = cùng cỡ (đường thường, không đổi gì) / cỡ hỏng.
     */
    fun letterbox(pw: Int, ph: Int, w: Int, h: Int): IntArray? {
        if (pw <= 0 || ph <= 0 || w <= 0 || h <= 0) return null
        if (pw == w && ph == h) return null
        // So sánh chéo bằng Long: pw/ph và w/h — bên nào "rộng" hơn quyết trục chạm biên.
        return if (pw.toLong() * h >= ph.toLong() * w) {
            intArrayOf(w, maxOf(1, (ph.toLong() * w / pw).toInt()))
        } else {
            intArrayOf(maxOf(1, (pw.toLong() * h / ph).toInt()), h)
        }
    }
}
