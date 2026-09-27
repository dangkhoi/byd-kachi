package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ MỆNH ĐỀ Ô **TỰ NÓ ĐÃ LÀ ĐỘNG TỪ** — *"vietmap vào ô số một"* ⇒ mở VietMap vào ô 1 ══════════════════════
 *
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car. Chạy ở **nhánh áp chót** của [VoiceIntentParser]: đúng một dòng, ngay
 * trước `return Unknown(NO_VERB)`, nên nó không đụng tới một câu nào đang hiểu được.
 *
 * ## Bệnh nó chữa — [ĐO xe 2026-09-27 10:30–10:37, log `KachiVoiceSession`]
 * 22 lượt owner nói *"mở &lt;app&gt; vào ô số N"*; **10 lượt hiểu sai**, và nhóm lớn nhất là **rụng động từ ở
 * đầu**: mô hình in ra đúng vế ô nhưng mất chữ *"mở"*.
 *
 * | mô hình in ra | ý định TRƯỚC | ý định SAU |
 * |---|---|---|
 * | *"vietmap vào ô số một"* | `Unknown(NO_VERB)` — *"không hiểu"* | `OpenApp(VietMap → ô 1)` |
 * | *"bỏ youtube vào ô số một"* | `Unknown(NO_VERB)` | `OpenApp(YouTube → ô 1)` |
 * | *"mát vào ô số hai"* ×2 · *"áp vào ô số một"* | `Unknown(NO_VERB)` | **giữ nguyên** `Unknown` (tên app không còn trong câu — xem §*Ba cổng*) |
 *
 * ## Vì sao mệnh đề ô được phép **thay** động từ, và chỉ nó
 * *"vào ô số N"* không phải một cụm trung tính: nó chỉ có nghĩa với **một** loại việc — gắn một app vào một ô của
 * bố cục ([VoiceIntent.OpenApp]). Không có lệnh xe nào, không có datum nào, không có câu nhạc/dẫn đường nào nhận
 * mệnh đề ấy. Nên khi câu có **cả** một tên app **và** một mệnh đề ô hoàn chỉnh (có con số), động từ là thứ duy
 * nhất còn thiếu, và nó suy ra được — đúng nghĩa *"đo rồi mới rẽ"* của CLAUDE.md §7, không phải một bảng câu
 * đặc biệt.
 *
 * ## Ba cổng — mỗi cổng chặn một kiểu đoán sai
 *  1. **Phải có mệnh đề ô CÓ SỐ** ([VoiceTailClause.slotAt] trên phần đuôi). *"vietmap"* trần ⇒ `null`: một cái
 *     tên đứng một mình **không** là một lệnh (bài `ten app tran KHONG duoc mo`). Và *"vào ô số một"* trần (không
 *     tên) cũng ⇒ `null` — đó là **câu trả lời cho một câu hỏi lại**, [VoiceClarify] đã mang vế trước theo và ghép
 *     lại; nhận nó ở đây là cướp đường của vòng hỏi-đáp.
 *  2. **Phần đầu phải giải ra một app**, qua đúng [VoiceLastResort] mà nhánh cuối của bộ phân tích dùng (cách gọi
 *     tiếng Việt → khớp mờ tên app, với cả bốn cổng của nó: chỉ vị trí 0 · dừng trước mệnh đề ô · ứng viên duy
 *     nhất theo nhãn · neo 4 ký tự + lệch ≤ 2 + hai chuỗi ≥ 5 ký tự). Không dựng phép so thứ hai ở đây (DRY).
 *  3. **Động từ suy ra là [VoiceVerb.OPEN]**, tức đúng động từ mà *"mở &lt;app&gt; vào ô N"* dùng hôm nay ⇒ câu
 *     sinh ra ở đây đi **cùng một đường** với câu nói đủ, không phải một đường thứ hai.
 *
 * ## Một từ lạ đứng trước tên app thì **bỏ qua nó** — *"bỏ youtube vào ô…"* · *"cho youtube vào ô…"*
 * [ĐO xe 2026-09-27 10:36:27] *"**bỏ** youtube vào ô số một"*: owner nói *"đặt"*, mô hình in ra *"bỏ"*. Tiếng Việt
 * còn có *"**cho** X vào Y"* — cùng nghĩa GẮN. Thay vì thêm `bo`/`cho` vào [VoiceGrammar.VERBS] (làm thế là
 * **vỡ** cả họ câu *"**bố** cục 2 cột"*: bỏ dấu ra `bo cuc`, và mọi câu bố cục sẽ có một *"động từ"* ở vị trí 0 ⇒
 * không bao giờ tới được [VoiceLayouts.match]), luật ở đây rộng đúng một bậc: **bỏ qua tối đa MỘT từ đầu** rồi thử
 * lại. Nó an toàn vì hai cổng còn lại không đổi — vẫn phải có một app **và** một mệnh đề ô có số.
 *
 * ⚠ Nhánh này nằm **sau** `headMatch` · [VoiceLayouts.match] · `savedPlace` · tra nhạc trong [VoiceIntentParser],
 * nên nhãn bộ đăng ký, bố cục, sổ địa chỉ và câu nhạc đều đã được hỏi trước (CLAUDE.md §6 — đường mới xuống cuối).
 */
internal object VoiceSlotNoVerb {

    /**
     * Ý định cho một câu **không có động từ** nhưng có *"&lt;app&gt; vào ô số N"*, hoặc `null`.
     *
     * @param t token của cả câu (đã bỏ lời khách sáo, đúng thứ [VoiceIntentParser] đang cầm ở nhánh đó).
     * @param terms từ vựng của phiên (nhãn app đã cài + hồ sơ) — truyền thẳng cho [VoiceLastResort].
     * @param original câu nguyên văn, chỉ để [VoiceLastResort] mang vào [VoiceIntent.Unknown] của đường (1).
     */
    fun pick(t: List<Token>, terms: List<VoiceTerm>, original: String): VoiceIntent? =
        at(t, 0, terms, original) ?: at(t, 1, terms, original)

    /**
     * Thử đọc câu như *"&lt;app bắt đầu ở [from]&gt; vào ô số N"*.
     *
     * Cổng 1 kiểm **trước** khi gọi [VoiceLastResort]: phép khớp mờ tên app là phần đắt nhất và cũng là phần có
     * đoán, nên nó chỉ được chạy khi câu đã chứng minh là một câu-có-ô. Thứ tự này cũng là thứ giữ *"vietmap"* trần
     * ở `Unknown`.
     */
    private fun at(t: List<Token>, from: Int, terms: List<VoiceTerm>, original: String): VoiceIntent? {
        if (from >= t.size) return null
        val rest = t.subList(from, t.size)
        val head = nameWords(rest)
        // Cổng 1 — mệnh đề ô phải có mặt VÀ có con số; phần trước nó phải còn ít nhất một từ (cái tên).
        if (head == 0 || head >= rest.size) return null
        if (VoiceTailClause.slotAt(rest.subList(head, rest.size)) == null) return null
        // Cổng 2 + 3 — đúng nhánh cuối của bộ phân tích, với động từ suy ra là OPEN.
        return VoiceLastResort.pick(VoiceVerb.OPEN, rest, terms, original) as? VoiceIntent.OpenApp
    }

    /**
     * Số từ ở **đầu** [rest] còn có thể là một phần của cái tên — dừng trước mệnh đề ô.
     *
     * Cùng phép cắt mà [VoiceLastResort] dùng ở cổng 2 của nó ([VoiceLexicon.SLOT_WORDS]); đọc lại chính bảng ấy
     * thay vì khai một bản thứ hai, để thêm một từ vào mệnh đề ô là tự có ở cả hai chỗ.
     */
    private fun nameWords(rest: List<Token>): Int {
        var n = 0
        while (n < rest.size && rest[n].norm !in VoiceLexicon.SLOT_WORDS) n++
        return n
    }
}
