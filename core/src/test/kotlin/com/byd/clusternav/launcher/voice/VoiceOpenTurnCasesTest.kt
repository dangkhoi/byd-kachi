package com.byd.clusternav.launcher.voice

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VOICE-OPEN-TURN · LƯỚI AN TOÀN — **cả bộ ca E2E** phải đóng lượt ngay ═══════════════════════════════════
 *
 * Yêu cầu số 1 của OQ9: *"câu đủ nghĩa KHÔNG được thêm một mili-giây nào"*. Một bài kiểm dựng tay chỉ chứng minh
 * được cho vài câu người viết nghĩ ra; cái đáng canh là **mọi** câu đang chạy. Bài này đọc thẳng
 * `scripts/emulator/voice-cases.tsv` — bộ ca của harness E2E (`scripts/emulator/voice-e2e.sh`, 106 ca ×3 lượt
 * ổn định ở 2.69) — và bắt buộc **0 câu** bị [VoiceOpenTurn.isOpen] coi là dở.
 *
 * ## Vì sao bộ ca ấy là nguồn ĐÚNG cho lưới này
 * Mỗi hàng của nó là một câu **người thật nói được** kèm ý định mong đợi, và nó là thứ chạy lại trước mỗi lần
 * nói *"xong"* ([[kachi-emulator-voice-e2e]]). Nếu một câu trong đó bị coi là dở thì lượt E2E kế tiếp sẽ chậm
 * thêm 1,2 s **mà vẫn xanh** — đúng loại hồi quy im lặng mà CLAUDE.md §10 bảo phải khoá bằng test, không bằng
 * mắt. Cái này cũng đã bắt thật: hai cụm [VoiceOpenVocab.TRIGGERS] (*"phát nhạc"* · *"play"*) và cụm đánh dấu
 * `qua` (từ *"quá"* trong *"hôm nay trời đẹp quá"*) đều nằm trong bảng nguồn, và chính bài này ép hai cổng
 * lọc bằng bộ phân tích ở [VoiceOpenTurn] phải tồn tại.
 *
 * ⚠ Câu trong bộ ca **đúng là dở** thì nằm ở [expectedOpen] kèm lý do — danh sách kèm-lý-do, không phải một
 * con số đếm, để lần sau ai thêm một câu vào đó phải nói tại sao (cùng khuôn `LayeringRulesTest`).
 */
class VoiceOpenTurnCasesTest {

    /**
     * Câu trong bộ ca mà [VoiceOpenTurn.isOpen] **được phép** trả `true`, kèm lý do từng câu.
     *
     * Khai ở dạng chuỗi đã chuẩn hoá (chữ thường) để so đúng cái đem kiểm.
     */
    private val expectedOpen: Map<String, String> = mapOf(
        "dẫn đường" to
            "ca t56 — **chính bộ ca** mong đợi `Unknown` cho câu này (cột kind = Unknown): *dẫn đường* là một " +
            "cụm động từ NAV trọn vẹn của VoiceGrammar.VERBS mà **chưa có điểm đến**, nên nó đúng là vế dở và " +
            "chờ thêm ở đây là ĐÚNG ý OQ9 (người nói *\"dẫn đường … ⟨ngừng để nghĩ⟩ … tới Bitexco\"*). Ca này " +
            "không có ý định nào bị đổi: hết cửa sổ mà không ai nói tiếp thì nó vẫn ra `Unknown` như hôm nay.",
    )

    private fun repoText(rel: String): String {
        val tries = listOf(rel, "../$rel", "../../$rel").map(Paths::get)
        val hit = tries.firstOrNull { Files.exists(it) }
            ?: error("không tìm thấy $rel; đã thử: ${tries.joinToString()}")
        return hit.toFile().readText()
    }

    /**
     * Câu của mọi hàng dữ liệu, `@PROFILE@` thay bằng một tên hồ sơ thật.
     *
     * Không thay thì *"đổi sang hồ sơ @PROFILE@"* tách từ ra `… ho so profile` — kết đúng bằng cụm đánh dấu
     * `profile` ⇒ bài sẽ đỏ vì một **giữ chỗ của harness**, không vì mã.
     */
    private fun sentences(): List<String> = repoText("scripts/emulator/voice-cases.tsv")
        .lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { it.split("\t").getOrNull(2)?.trim() }
        .filter { it.isNotEmpty() && it != "-" }
        .map { it.replace("@PROFILE@", "Mặc định") }

    @Test
    fun `khong cau nao trong bo ca E2E bi coi la do`() {
        val all = sentences()
        assertTrue(all.size >= 60, "bộ ca phải còn ≥ 60 câu để lưới không trống (thấy ${all.size})")
        val open = all.filter { VoiceOpenTurn.isOpen(it) }
        val unexpected = open.filterNot { it in expectedOpen.keys }
        assertEquals(
            emptyList<String>(), unexpected,
            "những câu ĐỦ NGHĨA này sẽ phải chờ thêm ${VoiceOpenTurn.OPEN_JOIN_WINDOW_MS} ms — " +
                "sửa bảng vế dở, hoặc thêm vào expectedOpen KÈM LÝ DO nếu nó thật sự dở",
        )
    }

    /** Danh sách ngoại lệ không được mọc âm thầm: mỗi phần tử phải còn là một hàng thật của bộ ca. */
    @Test
    fun `moi ngoai le phai con ton tai trong bo ca`() {
        val all = sentences()
        expectedOpen.keys.forEach {
            assertTrue(it in all, "\"$it\" không còn trong voice-cases.tsv — gỡ khỏi expectedOpen")
        }
    }

    /**
     * Bốn chuỗi **bệnh** của xe 26/09 phải nằm đúng phía *"dở"*, dựng từ chính bản thu.
     *
     * Đặt cạnh lưới trên có chủ ý: một bảng vế dở rỗng cũng làm bài trên xanh, nên phải có một bài đòi nó **nổ**.
     */
    @Test
    fun `bon chuoi benh cua xe 26-09 deu la ve do`() {
        listOf("mở vietmap vào ô", "mở vietmap vào ô số", "chuyển sang hồ sơ", "đổi sang hồ sơ").forEach {
            assertTrue(VoiceOpenTurn.isOpen(it), "\"$it\" — chuỗi THẬT của xe, phải được chờ vế sau")
        }
    }
}
