package com.byd.clusternav.launcher.voice

/**
 * ═══ NHÃN APP TRÙNG NHAU → CHỌN MỘT GÓI, **CÓ LUẬT VÀ NÓI RA** ═══════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R11 · H3(c). Thuần Kotlin (`:core`) ⇒ kiểm off-car, không cần máy.
 *
 * ## Bệnh nó chữa — [SOÁT 2026-09-16 · P3] `VoiceWiring.kt:38-47`
 * Bản trước dựng bản đồ nhãn→gói bằng `…map { nhãn to gói }.toMap()`. `toMap()` trên một danh sách có **hai
 * phần tử cùng khoá** giữ phần tử **sau cùng** — im lặng, không một dòng log. Trên ROM BYD chuyện hai app cùng
 * nhãn là bình thường chứ không hiếm: bản *"Cài đặt"* của OEM đứng cạnh một bản cài thêm, hai *"Nhạc"*, hai
 * *"Bản đồ"*. Hậu quả không phải là *"không mở được"* (thứ người dùng hiểu ngay) mà là **mở NHẦM app** — người
 * lái nói một cái tên, một app khác bật lên, và không có chỗ nào ghi lại vì sao.
 *
 * ⚠ **Mức bằng chứng** (CLAUDE.md §2): *"BYD ROM hay có nhãn trùng"* là **[SUY]** từ báo cáo soát xét, **CHƯA
 * ĐO** trên xe owner. Thứ **[ĐO] được** là bản thân phép `toMap()` (hợp đồng stdlib: khoá trùng ⇒ giá trị sau
 * thắng). Nên bản vá này không đoán xem xe có bao nhiêu nhãn trùng; nó làm hai việc **đúng trong cả hai ca**:
 * chọn theo một luật xác định, và **ghi log** để lần sau có số thật thay vì lại suy.
 *
 * ## Luật chọn — vì sao "không phải hệ thống trước", rồi tới thứ tự chữ cái
 *  1. **Gói KHÔNG phải hệ thống thắng.** Người dùng cài thêm một app trùng tên với app có sẵn thì gần như luôn
 *     vì họ muốn dùng **bản mới** đó; bản OEM vẫn gọi được qua bảng đích ([VoiceSynonyms.APP_TARGETS]) hoặc qua
 *     ngăn kéo. Đây là một **suy đoán có thể sai**, và chính vì thế nó phải có log.
 *  2. **Hoà thì lấy tên gói nhỏ nhất theo thứ tự chữ cái.** Không phải để "đúng hơn" — mà để **cùng một máy,
 *     cùng một danh sách, luôn ra cùng một kết quả**. Thứ tự `PackageManager` trả về không có gì bảo đảm (nó
 *     đổi theo lượt cài/gỡ), nên lấy "cái cuối cùng" như bản cũ nghĩa là hành vi đổi giữa hai lần khởi động mà
 *     không ai đụng vào gì.
 *
 * Hàm này **thuần**: không `PackageManager`, không `Log`. Tầng Android ([VoiceWiring.appsByLabel]) đọc máy rồi
 * đưa vào đây; chỗ ghi log cũng ở tầng ấy. Cắt vậy để luật chọn kiểm được off-car bằng một danh sách dựng tay.
 */
object VoiceAppLabelPick {

    /**
     * Một activity mở được từ ngăn kéo.
     *
     * @property label nhãn **người dùng nhìn thấy** (`ResolveInfo.loadLabel`).
     * @property pkg tên gói.
     * @property system gói thuộc ảnh hệ thống (`FLAG_SYSTEM` / `FLAG_UPDATED_SYSTEM_APP`) — xem luật (1).
     */
    data class Entry(val label: String, val pkg: String, val system: Boolean = false)

    /**
     * @property labels nhãn→gói đã khử trùng, **giữ thứ tự xuất hiện đầu tiên** của nhãn (để phép sinh cách đọc
     *   ở [VoiceWiring.withPhonetics] không đổi thứ tự cắm bí danh).
     * @property ambiguous chỉ những nhãn có **≥2 gói khác nhau**, giá trị là danh sách gói đã xếp theo đúng luật
     *   chọn — **phần tử đầu là gói đã thắng**. Rỗng = không có gì nhập nhằng (ca thường).
     */
    data class Picked(
        val labels: List<Pair<String, String>>,
        val ambiguous: Map<String, List<String>>,
    )

    /** Xếp ứng viên của MỘT nhãn theo luật chọn: không-phải-hệ-thống trước, rồi tên gói theo chữ cái. */
    private fun rank(candidates: List<Entry>): List<String> =
        candidates.sortedWith(compareBy({ it.system }, { it.pkg })).map { it.pkg }.distinct()

    /**
     * Khử nhãn trùng theo luật ở KDoc lớp.
     *
     * Hai activity của **cùng một gói** mang cùng nhãn (app có nhiều cửa vào ngăn kéo) **không** phải nhập
     * nhằng: chỉ có một gói để mở, nên nó không vào [Picked.ambiguous] và không sinh dòng log nào — cảnh báo
     * cho một ca vô hại là cách nhanh nhất để người ta thôi đọc cảnh báo.
     */
    fun of(entries: List<Entry>): Picked {
        val grouped = LinkedHashMap<String, MutableList<Entry>>()
        entries.forEach { grouped.getOrPut(it.label) { mutableListOf() }.add(it) }
        val labels = ArrayList<Pair<String, String>>(grouped.size)
        val ambiguous = LinkedHashMap<String, List<String>>()
        grouped.forEach { (label, candidates) ->
            val ranked = rank(candidates)
            labels.add(label to ranked.first())
            if (ranked.size > 1) ambiguous[label] = ranked
        }
        return Picked(labels, ambiguous)
    }
}
