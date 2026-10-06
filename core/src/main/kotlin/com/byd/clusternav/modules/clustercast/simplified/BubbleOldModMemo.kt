package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ 2.93 wave 2A · VM-BUBBLE-OLDMOD-MEMO — sổ "bản mod bóng nổi ĐANG CÀI đã chứng minh không ẩn bóng" (thuần, `:core`) ═══
 *
 * Spec `docs/specs/kachi-293-wave2a.html` §4.2 (đề xuất OQ4 spec `kachi-293-cast.html`). [ĐO log xe 06/10 13:49 · 15:13, xe
 * 2.91, mod VietMap v1] mỗi lượt mở chiếu có bóng VietMap trên cụm, cổng theme DỌN cụm (`VM_BUBBLE_VIS show=false`) rồi đọc lại
 * 6 lượt (~4,5–5 s) — bóng v1 không bao giờ ẩn ⇒ bỏ theme (BUBBLE). Với cùng MỘT bản cài, kết cục đó lặp lại y hệt ở mọi lượt
 * mở sau ⇒ 6 lượt đọc vô ích. Sổ này nhớ phép chứng minh theo DẤU CÀI ĐẶT của app bóng nổi ([token]) để lượt sau bỏ thẳng lượt
 * dọn (0 broadcast, 0 lượt đọc lại) — CHỈ có thể ra "không gửi theme" (rào "lớp còn trên màn ảo cụm ⇒ KHÔNG BAO GIỜ gửi
 * opcode theme" của [ClusterThemeGuard]/[ClusterLayerPause] không đổi: lượt bỏ dọn quyết [ClusterThemePlan.Reason.BUBBLE]).
 *
 * ## Dấu cài đặt = mã phiên bản + lần cài (không chỉ mã phiên bản)
 * Bản mod là APK VietMap vá lại — người vá thường GIỮ nguyên `versionCode` gốc ⇒ v1 → v2 cùng mã thì sổ theo mã sẽ bỏ dọn mãi
 * với chính bản v2 ẩn được. [ĐO nguồn AOSP android-10.0.0_r47] `PackageInfo.lastUpdateTime` (`PackageInfo.java:140-144`) đổi ở
 * MỌI lần cài: cài mới ⇒ `firstInstallTime = lastUpdateTime = currentTime` (`PackageManagerService.java:11468-11474`, `currentTime`
 * = `System.currentTimeMillis()` của lượt cài — `:16993`); cài đè ⇒ `setInstallAndUpdateTime(…, System.currentTimeMillis())`
 * (`:16781-16787`). Cài lại CÙNG bản ⇒ thử lại một lần (≈ 5 s) — hướng an toàn.
 *
 * ## Chỉ ghi khi chứng minh TRỌN, và tin có hạn
 *  - Ghi ([Outcome.PROVEN_OLD]) chỉ khi: đã gửi lệnh ẩn, chạy ĐỦ [ClusterThemeGuard.PAUSE_READS] lượt đọc lại (không bị hạn lượt
 *    mở cắt — CAST-OPEN-TIMEOUT), không lượt nào sạch, và quyết sau dọn vẫn là BUBBLE. Lượt bị cắt ⇒ [Outcome.INCONCLUSIVE] (review
 *    CAST Pass 1 [P3]: mod v2 ẩn chậm mà bị cắt sẽ bị báo "mod cũ" oan — sổ không được khoá điều oan ấy lại).
 *  - Bóng biến mất sau lệnh ẩn ([Outcome.HID]) ⇒ XOÁ sổ.
 *  - Tin sổ tối đa [SKIPS_MAX] lượt liền; lượt kế THỬ THẬT lại (độ trễ ẩn của mod v2 [CHƯA BIẾT] — OQ1 spec 293-cast; một lần
 *    chứng minh oan chỉ làm mất theme tối đa [SKIPS_MAX] lượt mở, không mãi tới lần cài sau).
 *
 * Đọc/ghi qua [ClusterLayerPort] (`:app`: tệp `clustercast`, phạm vi XE — `ProfileScopeCluster.DEVICE_KEYS`). Không ném.
 */
object BubbleOldModMemo {

    /** Khoá prefs (tệp `clustercast`, cạnh sổ theme [ThemeLedger.KEY]). */
    const val KEY: String = "cluster_bubble_old_mod"

    /** Số lượt mở LIỀN được bỏ dọn nhờ sổ trước khi thử thật lại một lần. */
    const val SKIPS_MAX: Int = 4

    /** Một mục sổ: [token] = dấu cài đặt đã chứng minh; [skips] = số lượt đã bỏ dọn nhờ nó kể từ lần chứng minh. */
    data class Entry(val token: String, val skips: Int)

    enum class Outcome { PROVEN_OLD, HID, INCONCLUSIVE }

    /** Dấu cài đặt của app bóng nổi; `null` khi số đọc được không hợp lệ (không nhớ gì). */
    fun token(versionCode: Long, lastUpdateTime: Long): String? =
        if (versionCode < 0 || lastUpdateTime <= 0) null else "$versionCode@$lastUpdateTime"

    fun encode(e: Entry): String = "v1|${e.token}|${e.skips}"

    /** Đọc chặt: định dạng lạ / hỏng ⇒ `null` (như chưa có sổ ⇒ dọn như cũ). */
    fun decode(raw: String?): Entry? {
        val p = raw?.split('|') ?: return null
        if (p.size != 3 || p[0] != "v1" || !TOKEN.matches(p[1])) return null
        val skips = p[2].toIntOrNull()?.takeIf { it in 0..SKIPS_MAX } ?: return null
        return Entry(p[1], skips)
    }

    /**
     * Trước một lượt DỌN có bóng nổi: sổ khớp ĐÚNG bản cài [current] và còn lượt tin ⇒ mục sổ MỚI (đã đếm thêm một lượt bỏ) — bên
     * gọi ghi nó rồi bỏ dọn; `null` ⇒ dọn thật (chưa có sổ · bản cài khác · không đọc được dấu · hết lượt tin ⇒ thử lại).
     */
    fun onGate(memo: Entry?, current: String?): Entry? =
        if (memo == null || current == null || memo.token != current || memo.skips >= SKIPS_MAX) null
        else memo.copy(skips = memo.skips + 1)

    /**
     * Kết cục một lượt dọn THẬT: [hideSent] = đã gửi `VM_BUBBLE_VIS show=false` TỚI một bóng có trong bản đọc ĐẦU (lượt chỉ dọn lớp
     * của Kachi — VietMap không chạy — không thử gì về bản mod ⇒ bên gọi trao `false`; senior review wave 2A Pass 1); [fullWindow]
     * = chạy đủ số lượt đọc lại (không bị hạn cắt); [cleared] = có lượt đọc lại thấy cụm sạch; [stillBubble] = quyết từ bản đọc
     * sau dọn vẫn là BUBBLE.
     */
    fun outcome(hideSent: Boolean, fullWindow: Boolean, cleared: Boolean, stillBubble: Boolean): Outcome = when {
        hideSent && cleared -> Outcome.HID
        hideSent && fullWindow && !cleared && stillBubble -> Outcome.PROVEN_OLD
        else -> Outcome.INCONCLUSIVE
    }

    /** Quyết định của lượt BỎ dọn nhờ sổ — đúng lý do một lượt dọn thật sẽ ra (Cài đặt nói "bản mod VietMap cũ … Áp ngay"). */
    fun skipDecision(apps: List<String>): ClusterThemePlan.Decision.Skip = ClusterThemePlan.Decision.Skip(
        ClusterThemePlan.Reason.BUBBLE,
        "bóng của ${apps.joinToString()} — bản mod đang cài đã chứng minh không ẩn bóng (sổ $KEY) ⇒ bỏ dọn, không gửi",
        apps,
    )

    private val TOKEN = Regex("""\d{1,19}@\d{1,19}""")
}

/**
 * Bộ thi hành sổ [BubbleOldModMemo] trên [ClusterLayerPort] — chạy trên executor của cổng theme (một thực thể mỗi
 * [ClusterThemeGuard]). Không bao giờ ném: cổng hỏng ⇒ coi như không có sổ (dọn thật như 2.90).
 */
internal class OldModMemoGate(private val layers: ClusterLayerPort, private val log: (String) -> Unit) {

    private fun current(): String? = runCatching { layers.bubbleInstallToken() }.getOrNull()

    private fun memo(): BubbleOldModMemo.Entry? = BubbleOldModMemo.decode(runCatching { layers.oldModMemo() }.getOrNull())

    /** `true` = đã chạm đĩa. */
    private fun write(v: String?): Boolean =
        runCatching { layers.writeOldModMemo(v) }.getOrDefault(false)
            .also { if (!it) log("sổ mod cũ: KHÔNG ghi được (${v ?: "xoá"})") }

    /**
     * `true` = bỏ lượt dọn này (đã đếm lượt bỏ vào sổ). Senior review wave 2A Pass 1 [P3]: lượt bỏ KHÔNG ghi được ⇒ DỌN THẬT —
     * không thì đĩa hỏng giữ số lượt bỏ đứng yên ⇒ sổ được tin mãi, vượt trần [BubbleOldModMemo.SKIPS_MAX] (D2 spec wave 2A).
     */
    fun skip(op: Int): Boolean {
        val cur = current()
        val next = BubbleOldModMemo.onGate(memo(), cur) ?: return false
        if (!write(BubbleOldModMemo.encode(next))) return false
        log("dọn cụm (theme $op): BỎ — bản cài $cur đã chứng minh không ẩn bóng · lượt bỏ ${next.skips}/${BubbleOldModMemo.SKIPS_MAX} ⇒ 0 broadcast")
        return true
    }

    /** Sau một lượt dọn THẬT — ghi / xoá sổ theo [BubbleOldModMemo.outcome]. */
    fun settle(hideSent: Boolean, fullWindow: Boolean, cleared: Boolean, stillBubble: Boolean) {
        when (BubbleOldModMemo.outcome(hideSent, fullWindow, cleared, stillBubble)) {
            // Review Pass 1: dòng GHI/XOÁ chỉ khi đĩa nhận (ghi hỏng đã có dòng "KHÔNG ghi được" — không nói hai điều trái nhau).
            BubbleOldModMemo.Outcome.PROVEN_OLD -> current()?.let {
                if (write(BubbleOldModMemo.encode(BubbleOldModMemo.Entry(it, 0)))) {
                    log("sổ mod cũ: GHI $it (đủ ${ClusterThemeGuard.PAUSE_READS} lượt đọc lại, bóng vẫn còn)")
                }
            }
            BubbleOldModMemo.Outcome.HID -> if (memo() != null && write(null)) log("sổ mod cũ: XOÁ — bóng đã ẩn theo VM_BUBBLE_VIS")
            BubbleOldModMemo.Outcome.INCONCLUSIVE -> Unit
        }
    }
}
