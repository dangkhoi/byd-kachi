package com.byd.clusternav.launcher

/**
 * ═══ 2.98 · R3 · SLOT-ELSEWHERE-TWO-HOMES — "ở chỗ khác" không được là ô của một màn Kachi KHÁC (thuần, `:core`) ══════════════
 *
 * Spec `docs/specs/kachi-298-plan.html` R3 · backlog `SLOT-ELSEWHERE-TWO-HOMES` · review SLOT Pass 2 mục 6 (`kachi-293-slot.html`).
 *
 * Chuỗi lỗi [SUY đọc mã — từng mắt xích có `file:line` trong báo cáo R3]: hai `KachiHomeActivity` cùng sống (H2 — [ĐO máy ảo]
 * `SlotVdLedger` KDoc 09-14: bốn bản; fixture `am-stack-list-emulator-2026-10-02-esc-b-on-top.txt`: ba task `KachiHomeActivity`
 * cùng lúc) ⇒ màn MỚI dựng ô n ⇒ `SlotVdOwner.adopt` nhả màn ảo ô n của màn CŨ (bất biến một-màn-ảo-mỗi-ô) mà KHÔNG báo host cũ
 * ⇒ bản đăng ký đo của host cũ vẫn ở `SlotLiveProbe` (khoá `ws@<cũ>#n`, display đã chết) ⇒ màn mới mở app vào màn ảo của NÓ ⇒
 * bản đọc thấy app ở display khác ⇒ `SlotPresence.ELSEWHERE` ⇒ host cũ báo `onGone(…, elsewhere = true)` ⇒ câu
 * *"… đã rời ô, vẫn mở ngoài ô"* (Toast qua `applicationContext` — hiện đè lên màn mới) trong khi app đang nằm đúng ô của màn
 * người lái đang nhìn.
 *
 * Luật: display là màn ảo ô do một chủ HOME KHÁC ([isHome], khác chủ của bản đo — [ownerOf]) đang giữ ở `SlotVdOwner` thì
 * KHÔNG phải "chỗ khác" ([otherHomes] → `SlotPresence.of(ignoring = …)`). App chỉ còn ở đó ⇒ ô của màn cũ thấy "đã rời" (GONE)
 * ⇒ đi luật đã thấy-sống của 2.92 (hai nhịp hụt ⇒ hoàn ô IM LẶNG ở màn cũ, không câu báo). Khác chủ là bắt buộc: CÙNG một màn
 * (ô khác của chính nó), màn ảo ô 7 (`park`) và màn ảo dàn dựng (`stage`) giữ nguyên hành vi 2.93 — một màn Kachi duy nhất ⇒
 * [otherHomes] luôn rỗng ⇒ 0 khác biệt (CLAUDE.md §6). App ra display 0 / cụm khi hai màn cùng sống ⇒ vẫn "ở chỗ khác" như cũ.
 *
 * Không lệnh mới: chỉ đọc sổ chủ màn ảo trong RAM (`SlotVdOwner.held`) trên CÙNG bản `am stack list` của nhịp.
 */
object SlotProbeScope {

    /**
     * Chủ màn ảo của một cây workspace của màn chính: `"ws"` (mặc định `VdAppHost`) hoặc `"ws@<mã cây>"` (`WorkspaceView.hostOwner`
     * — mỗi màn Kachi một cây). `"park"` (ô 7) / `"stage"` (dàn dựng BEHIND-HOME) không phải màn chính.
     */
    const val HOME_OWNER = "ws"

    /** Một màn ảo đang sống ở `SlotVdOwner`: [owner] = chủ, [displayId] = display của nó. */
    data class Held(val owner: String, val displayId: Int)

    /** Chủ của bản đo theo khoá `"$owner#$slot"` (`VdAppHost.probeKey`). Khoá không có `#` ⇒ cả khoá. */
    fun ownerOf(probeKey: String): String = probeKey.substringBeforeLast('#')

    fun isHome(owner: String): Boolean = owner == HOME_OWNER || owner.startsWith("$HOME_OWNER@")

    /**
     * Display là màn ảo ô của một màn Kachi KHÁC chủ của bản đo [probeKey] (màn ảo ô [vd] của chính nó không bao giờ có ở đây).
     * Một màn Kachi ⇒ rỗng.
     */
    fun otherHomes(held: List<Held>, probeKey: String, vd: Int): Set<Int> {
        val me = ownerOf(probeKey)
        return held.filter { it.owner != me && it.displayId != vd && isHome(it.owner) }.mapTo(HashSet()) { it.displayId }
    }
}
