package com.byd.clusternav.modules.clustercast

/**
 * ═══ 2.90 · R10 — chọn display CỤM cho lớp phủ trong tiến trình của Kachi (badge tốc độ, camera) — thuần ═════════════════════
 *
 * Bản cũ (`SpeedBadgeOverlay`) thử `getDisplay(1)` TRƯỚC rồi mới tới display PRESENTATION đầu tiên; camera rơi về
 * `dm.displays.firstOrNull { id ≠ 0 }`. [ĐO xe 15/09 + máy ảo 05/10] sau khởi động nguội display 1 = `kachi-slot-0` — VD ô của CHÍNH
 * Kachi (`FLAG_PRIVATE`; Kachi vẫn thấy vì là chủ: [ĐO nguồn A10 r47] `DisplayManagerService.java:584-601` lọc theo
 * `DisplayInfo.hasAccess` `DisplayInfo.java:590-592`) ⇒ badge lên ô màn chính, không lên cụm. Spec `kachi-290-cluster-rect-fix.html` §4.5.
 *
 * Luật (generic — CLAUDE.md §7, không hằng `1`, không tên gói):
 *  1. Loại display 0 và MỌI display riêng tư ([Candidate.isPrivate]) — VD ô của Kachi không bao giờ là cụm.
 *  2. Id cụm SỐNG do coordinator công bố ([castLiveId] — cùng lượt dò `ClusterDisplayResolver` mà cổng theme dùng) còn trong danh
 *     sách ⇒ lấy nó.
 *  3. Không có ⇒ cùng luật tên với [DisplayParse.clusterDisplayId] (chứa `fission`/`xdja`), id NHỎ nhất làm mốc, rồi id LỚN nhất
 *     cùng tên với mốc (màn ảo dựng lại: [ĐO xe 06/10] 4 → 9; id không tái dùng — xem `ClusterDisplayResolver.newestSameName`).
 *  4. Không có nữa ⇒ display `PRESENTATION` không riêng tư có id nhỏ nhất (máy ảo: overlay display) — đường off-car cũ.
 *  5. Không có gì ⇒ `-1` (lớp phủ đứng chờ, không gắn đâu cả).
 */
object ClusterOverlayDisplay {

    /** Một display như `DisplayManager.getDisplays()` thấy: id, `Display.getName()`, cờ `FLAG_PRIVATE`, cờ `FLAG_PRESENTATION`. */
    data class Candidate(val id: Int, val name: String, val isPrivate: Boolean, val isPresentation: Boolean)

    fun pick(displays: List<Candidate>, castLiveId: Int?): Int {
        val ok = displays.filter { it.id >= 1 && !it.isPrivate }
        if (castLiveId != null && castLiveId >= 1 && ok.any { it.id == castLiveId }) return castLiveId
        val named = ok.filter { isClusterName(it.name) }.sortedBy { it.id }
        val first = named.firstOrNull()
        if (first != null) return named.filter { it.name == first.name }.maxOf { it.id }
        return ok.filter { it.isPresentation }.minByOrNull { it.id }?.id ?: -1
    }

    /** Cùng chuỗi nhận diện với [DisplayParse.clusterDisplayId] / [WmParse.clusterDisplayIds]. */
    fun isClusterName(name: String): Boolean = name.lowercase().let { "fission" in it || "xdja" in it }
}
