package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.camera.CameraWhich
import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ 2.93 · CAMERA THEO YÊU CẦU bằng lời — NHIỀU cách nói, một bảng (spec `docs/specs/kachi-293-cam.html` §4.4) ═══════
 *
 * Owner 06/10 (nguyên văn): *"voice: mở cam trái, mở cam phải, chứ không phải ai cũng đọc là mở camera trái đâu nhé, nhiều
 * option nhé"*. Nhãn của [LauncherActions] (*"Camera sau"*) chỉ cho đúng MỘT cách gọi; người lái thì nói *"cam trái"* ·
 * *"bật cam bên phải"* · *"xem camera lùi"* · *"tắt máy quay trước"* · *"tắt cam"*. Bảng ở đây khai đủ bốn trục:
 * **động từ** mở/tắt × **tên camera** (kể cả dạng mô hình in theo âm tiết *"ca-me-ra"*) × **từ nối** (*bên/phía*) ×
 * **phía** (kèm từ đồng nghĩa: sau = lùi/đuôi, trước = đầu).
 *
 * ## Vì sao một bộ khớp RIÊNG, không nhét vào từ vựng chung ([VoiceGrammar])
 *  • **Động từ riêng của camera**: *"ẩn"* · *"thôi"* là động từ TẮT chỉ khi đi với camera. Đưa chúng vào
 *    [VoiceGrammar.VERBS] là đổi nghĩa cả trăm câu đang chạy (*"thôi lấy gió ngoài"* là lệnh tuần hoàn — KDoc
 *    [VoiceEndWords]); ở đây chúng chỉ sống trong bảng này.
 *  • **Chỉ nhận khi CẢ VẾ là một câu camera** — thiếu/thừa một từ ⇒ `null` ⇒ vế đi tiếp đường cũ y nguyên. Nhờ vậy câu
 *    hỏi (*"camera sau có bật không"*), câu kể (*"camera sau bẩn quá"*) và mọi cụm khác chứa chữ *"cam"* (*"cảm biến"*,
 *    *"cảm ơn"*, *"camera 360"*, *"camera hành trình"*) không đổi nghĩa.
 *  • Không động từ ĐỨNG SAU (*"camera sau mờ"* là câu kể, không phải lệnh) — động từ chỉ được đứng đầu.
 *
 * ## Ý định sinh ra — cùng [VoiceIntent.Launcher] của nhãn (không loại ý định mới)
 *  • có phía + động từ MỞ hoặc không động từ ⇒ `Launcher(camera)` — tầng thi hành coi là BẬT/TẮT theo thứ đang hiện
 *    (nói lại câu mở lần hai thì tắt — `CameraDemand.spoken`);
 *  • có phía + động từ TẮT ⇒ `Launcher(camera, off = true)` — chỉ tắt đúng camera ấy;
 *  • KHÔNG phía + động từ TẮT (*"tắt cam"* · *"tắt camera"* · *"đóng hết camera"*) ⇒ `Launcher(CAM_OFF)` — tắt camera theo
 *    yêu cầu đang mở, bất kể camera nào. ⚠ Đổi nghĩa so với ≤ 2.92 (*"tắt camera"* trần từng là nút Camera 360 của xe):
 *    chỉ dẫn owner 06/10 qua điều phối 2.93; Camera 360 vẫn gọi được bằng *"tắt camera 360"* · *"tắt camera toàn cảnh"* ·
 *    *"tắt camera quanh xe"* (spec §9).
 *
 * Mọi bảng viết CÓ DẤU — là nguồn cho cả phép khớp (bỏ dấu qua [VoiceLexicon.tokenize]) lẫn cụm hotword ([hotwordPhrases]).
 */
object VoiceCameraPhrases {

    /** Động từ MỞ (tiếng Anh không dấu là chính nó). */
    val OPEN_VERBS: List<String> = listOf(
        "mở", "bật", "xem", "hiện", "cho xem", "coi", "hiển thị", "cho coi", "mở xem", "bật xem", "xem thử", "coi thử",
        "open", "show", "view", "display", "turn on",
    )

    /** Động từ TẮT — *"ẩn"* · *"thôi"* chỉ mang nghĩa này TRONG bảng camera (KDoc lớp). */
    val CLOSE_VERBS: List<String> = listOf(
        "tắt", "đóng", "ẩn", "thôi", "thôi xem", "thôi coi", "tắt xem", "dẹp", "close", "hide", "turn off",
    )

    /** Cách gọi camera — gồm dạng mô hình nhận dạng in theo âm tiết. */
    val NOUNS: List<String> = listOf("camera", "cam", "máy quay", "ca-me-ra", "ca mê ra")

    /** Từ nối tuỳ chọn giữa tên camera và phía (*"cam BÊN trái"* · *"camera PHÍA sau"* · *"camera ĐẰNG trước"*). */
    val LINKS: List<String> = listOf("bên", "phía", "đằng", "tay", "ở", "bên tay", "bên phía", "ở phía", "ở bên")

    /** Phía của từng camera, kèm từ đồng nghĩa thường nói. */
    val SIDES: Map<CameraWhich, List<String>> = mapOf(
        CameraWhich.REAR to listOf("sau", "lùi", "đuôi", "hậu", "rear", "back", "reverse"),
        CameraWhich.LEFT to listOf("trái", "left"),
        CameraWhich.RIGHT to listOf("phải", "right"),
        CameraWhich.FRONT to listOf("trước", "đầu", "front"),
    )

    /**
     * Phía được ĐỨNG TRƯỚC tên camera — chỉ tiếng Anh (*"rear camera"*). Tiếng Việt đặt phía SAU tên; thứ tự ngược ở tiếng
     * Việt là câu khác nghĩa (*"trái cam"* = quả cam · *"trước camera"* = phía trước ống kính) — soát senior 2.93 [P3].
     */
    private val SIDE_FIRST: Set<String> = setOf("rear", "back", "reverse", "left", "right", "front")

    /** Từ đệm ngay sau động từ (*"mở CÁI camera sau"* · *"tắt HẾT camera"* · *"turn on THE rear camera"*). */
    val AFTER_VERB: List<String> = listOf("cái", "giúp", "hộ", "giùm", "dùm", "hết", "tất cả", "mọi", "all", "the")

    /** Từ đệm cuối câu (*"bật cam sau LÊN"* · *"camera đầu XE"*). Lịch sự cuối câu (*"nhé"*, *"đi"*) đã được cắt trước. */
    val TAIL: List<String> = listOf("xe", "lên", "ra", "lại", "đi", "nhé", "nha", "giúp", "với", "nào", "thử", "luôn", "ngay")

    private fun norm(table: List<String>): List<List<String>> =
        table.map { p -> VoiceLexicon.tokenize(p).map { it.norm } }.filter { it.isNotEmpty() }.distinct()
            .sortedByDescending { it.size }

    private val OPEN_N by lazy { norm(OPEN_VERBS) }
    private val CLOSE_N by lazy { norm(CLOSE_VERBS) }
    private val NOUNS_N by lazy { norm(NOUNS) }
    private val LINKS_N by lazy { norm(LINKS) }
    private val AFTER_VERB_N by lazy { norm(AFTER_VERB) }
    private val TAIL_N by lazy { norm(TAIL) }
    private val SIDES_N by lazy { SIDES.mapValues { (_, v) -> norm(v) } }

    /** Độ dài cụm dài nhất của [table] khớp tại [i], `0` = không khớp. */
    private fun at(w: List<String>, i: Int, table: List<List<String>>): Int =
        table.firstOrNull { p -> p.indices.all { k -> w.getOrNull(i + k) == p[k] } }?.size ?: 0

    /** Bỏ qua mọi từ của [table] đứng liền từ [i]; trả vị trí sau chúng. */
    private fun skip(w: List<String>, i: Int, table: List<List<String>>): Int {
        var j = i
        while (true) { val n = at(w, j, table); if (n == 0) return j; j += n }
    }

    /** Phía tại [i]: (camera, số từ) hoặc `null`. */
    private fun sideAt(w: List<String>, i: Int): Pair<CameraWhich, Int>? =
        SIDES_N.entries.mapNotNull { (cam, t) -> at(w, i, t).takeIf { it > 0 }?.let { cam to it } }.maxByOrNull { it.second }

    /**
     * Một VẾ (đã bỏ đệm đầu, đã cắt lịch sự) có phải TRỌN một lệnh camera không — xem KDoc lớp. `null` ⇒ không phải (vế đi
     * tiếp đường cũ, không đổi một ý định nào đang có).
     */
    fun parse(t: List<Token>): VoiceIntent? {
        val w = t.map { it.norm }
        if (w.isEmpty()) return null
        var i = 0
        val open = at(w, i, OPEN_N)
        val close = at(w, i, CLOSE_N)
        // Dài nhất thắng giữa hai bảng (*"thôi xem"* (TẮT) dài hơn *"xem"* — nhưng *"xem"* không đứng đầu *"thôi xem"*).
        val isClose = close > open
        i += maxOf(open, close)
        val hasVerb = open > 0 || close > 0
        i = skip(w, i, AFTER_VERB_N)
        var which: CameraWhich? = null
        val noun = at(w, i, NOUNS_N)
        if (noun > 0) {
            i += noun
            val link = at(w, i, LINKS_N)
            sideAt(w, i + link)?.let { (cam, n) -> which = cam; i += link + n }
        } else {
            // Thứ tự tiếng Anh: phía rồi tên (*"rear camera"*) — chỉ phía tiếng Anh ([SIDE_FIRST]). Không có tên camera thì
            // không phải câu camera (*"mở trái"*).
            val (cam, n) = sideAt(w, i)?.takeIf { (_, len) -> w.subList(i, i + len).all { it in SIDE_FIRST } } ?: return null
            val after = at(w, i + n, NOUNS_N).takeIf { it > 0 } ?: return null
            which = cam
            i += n + after
        }
        i = skip(w, i, TAIL_N)
        if (i != w.size) return null
        val cam = which
        return when {
            cam != null -> VoiceIntent.Launcher(LauncherActions.cameraId(cam), off = isClose)
            hasVerb && isClose -> VoiceIntent.Launcher(LauncherActions.CAM_OFF)
            else -> null
        }
    }

    /** [phrase] có phải đúng MỘT cách gọi camera trần (*"camera"* · *"cam"* · *"máy quay"*) không — cho bảng câu mẫu. */
    fun isCameraNoun(phrase: String): Boolean = VoiceLexicon.tokenize(phrase).map { it.norm } in NOUNS_N

    /**
     * 2.93 OQ5 — cụm [words] (đã bỏ dấu) là TÊN camera không kèm phía: mở đầu bằng một cách gọi ở [NOUNS] và ngay sau đó
     * (qua từ nối nếu có) KHÔNG có phía (*"camera"* · *"camera 360 độ"* · *"camera quanh xe"* · *"cam ba sáu mươi"*). Dùng ở
     * [VoiceIntentParser]: câu KHÔNG động từ mà chỉ có tên ấy thì không phải lệnh — [ĐO host 2026-10-07] *"tắt camera"* +
     * nhạc mất chữ *"tắt"* ⇒ *"CAMERA"* từng BẬT camera. Có phía (*"cam trái"*) thì vẫn là lệnh bật/tắt của [parse].
     * Phía chỉ xét NGAY sau tên, không xét cả cụm: bỏ dấu thì *"sáu"* (6) = *"sau"* (phía sau).
     */
    fun bareName(words: List<String>): Boolean {
        val n = at(words, 0, NOUNS_N)
        return n > 0 && sideAt(words, n + at(words, n, LINKS_N)) == null
    }

    /** Phía nói bằng tiếng Việt (để bias) — tiếng Anh không vào tệp hotword (mô hình VN không phát token ấy). */
    private val HOTWORD_SIDES: List<Pair<String, String?>> = listOf(
        "sau" to "phía", "lùi" to null, "đuôi" to null, "trái" to "bên", "phải" to "bên", "trước" to "phía", "đầu" to null,
    )

    /**
     * Cụm CÓ DẤU đáng bias cho bộ nhận dạng ([SherpaPhraseHotwords]) — *"CAM TRÁI"* · *"MỞ CAM BÊN TRÁI"* · *"TẮT CAMERA LÙI"*.
     * Chỉ hai cách gọi phổ biến (*cam* · *camera*) × năm động từ phổ biến: tệp hotword là ngân sách (KDoc [SherpaPhraseHotwords]),
     * phần còn lại của bảng vẫn HIỂU được ở tầng chữ dù không được bias. Viết HOA ở [SherpaHotwords.normalize].
     */
    fun hotwordPhrases(): List<String> {
        val out = ArrayList<String>()
        val verbs = listOf("mở", "bật", "xem", "tắt", "đóng")
        listOf("cam", "camera").forEach { n ->
            HOTWORD_SIDES.forEach { (side, link) ->
                val bases = listOfNotNull("$n $side", link?.let { "$n $it $side" })
                bases.forEach { b -> out.add(b); verbs.forEach { v -> out.add("$v $b") } }
            }
            listOf("tắt", "đóng").forEach { out.add("$it $n") }
        }
        return out
    }
}
