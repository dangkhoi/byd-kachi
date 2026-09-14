package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá VÒNG KIỂM QUYỀN (P8 — spec `kachi-permission-preflight.html`, R1–R7).
 *
 * Ba test quan trọng nhất:
 *  • `du thi IM LANG` — không ai muốn bị thông báo về chuyện đang chạy bình thường.
 *  • `chua doc duoc thi KHONG ket luan la thieu` — ROM thiếu API sẽ tạo nhiễu đúng lúc đang test trên xe.
 *  • `khong muc nao chi toi man cai dat he thong` — [ĐO] màn đó BỊ KHOÁ trên xe.
 */
class LauncherRequirementsTest {

    private fun report(vararg missing: String) =
        LauncherRequirements.check { req -> req.id !in missing }

    // ── R1: một chỗ duy nhất, mỗi mục khai đủ ────────────────────────────────────────────────────

    @Test
    fun `moi dieu kien deu khai du cach kiem, mat gi, ai sua duoc`() {
        assertTrue(LauncherRequirements.ALL.isNotEmpty(), "phải có danh mục")
        LauncherRequirements.ALL.forEach { r ->
            assertTrue(r.id.isNotBlank(), "mục phải có mã ổn định")
            assertTrue(r.label.isNotBlank(), "mục ${r.id} phải có tên cho người đọc")
            assertTrue(
                r.losesWhatIfMissing.isNotBlank(),
                "mục ${r.id} PHẢI khai thiếu-thì-mất-gì — câu chung 'thiếu quyền' không giúp ai (R3)",
            )
        }
        val ids = LauncherRequirements.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "mã điều kiện không được trùng")
    }

    @Test
    fun `muc can nguoi dung thi PHAI noi viec can lam`() {
        LauncherRequirements.ALL.filter { it.fixBy == FixBy.USER }.forEach {
            assertNotNull(it.userAction, "mục ${it.id} cần người dùng ⇒ phải nói việc cần làm")
            assertTrue(it.userAction!!.isNotBlank())
        }
        // Ngược lại: mục tự sửa được thì KHÔNG nên bắt người dùng làm gì
        LauncherRequirements.ALL.filter { it.fixBy == FixBy.SELF }.forEach {
            assertNull(it.userAction, "mục ${it.id} tự sửa được ⇒ đừng bắt người dùng làm gì")
        }
    }

    // ── R6 + Đ3: màn cài đặt hệ thống trên xe BỊ KHOÁ ────────────────────────────────────────────

    @Test
    fun `khong muc nao chi toi man cai dat he thong`() {
        // [ĐO] mở màn cài đặt trên xe chỉ nhận "Hệ thống IVI không hỗ trợ hoạt động này" ⇒ chỉ tới đó là bỏ mặc
        // người dùng ở chỗ không làm được gì.
        LauncherRequirements.ALL.mapNotNull { it.userAction }.forEach { action ->
            val a = action.lowercase()
            assertFalse("cài đặt hệ thống" in a, "không được chỉ tới màn cài đặt hệ thống: '$action'")
            assertFalse("vào settings" in a, "không được chỉ tới Settings: '$action'")
        }
    }

    // ── R2: đủ thì im lặng ───────────────────────────────────────────────────────────────────────

    @Test
    fun `du thi IM LANG`() {
        val r = LauncherRequirements.check { true }
        assertTrue(r.allOk, "mọi mục đủ ⇒ allOk")
        assertNull(r.notice(), "đủ thì KHÔNG hiện gì (R2)")
        assertEquals("đủ quyền", r.logLine())
        assertTrue(r.missing.isEmpty() && r.selfFixable.isEmpty() && r.needsUser.isEmpty())
    }

    @Test
    fun `thieu nhung toan phan TU SUA duoc thi cung im lang`() {
        // Launcher tự cấp rồi; nói ra chỉ làm người dùng lo về việc họ không cần làm gì.
        val r = report("notif_listener", "accessibility", "overlay")
        assertFalse(r.allOk)
        assertEquals(3, r.selfFixable.size)
        assertTrue(r.needsUser.isEmpty())
        assertNull(r.notice(), "toàn bộ phần thiếu đều tự sửa được ⇒ im lặng")
        assertTrue(r.logLine().contains("tự xin"), "nhưng NHẬT KÝ phải ghi lại để buổi test trên xe đọc được")
    }

    // ── R3: nói rõ thiếu cái gì và mất gì ────────────────────────────────────────────────────────

    @Test
    fun `thieu thi neu DICH DANH muc do va mat gi`() {
        val r = report("default_home")
        val msg = r.notice()!!
        assertTrue(msg.contains("Là màn hình chính"), "phải nêu đích danh mục thiếu")
        assertTrue(msg.contains("HOME"), "phải nói mất gì")
        assertFalse(msg.equals("thiếu quyền", ignoreCase = true), "không được là câu chung")
    }

    @Test
    fun `thieu do moi truong thi noi ro de nguoi test khoi di tim bug`() {
        val r = report("shell")
        assertEquals(1, r.environment.size)
        assertTrue(r.missingCore.isNotEmpty(), "kênh shell thiếu ⇒ mất tính năng lõi")
        assertNotNull(r.notice(), "phải nói ra — off-car đây là ca thường, người test cần biết đó KHÔNG phải lỗi")
    }

    // ── UNKNOWN không được coi là thiếu ──────────────────────────────────────────────────────────

    @Test
    fun `chua doc duoc thi KHONG ket luan la thieu`() {
        // Một số ROM không có API để đọc (code cũ bọc runCatching đúng vì lý do đó). Đoán là thiếu rồi đi xin lại sẽ
        // tạo nhiễu đúng vào lúc đang test trên xe — trái hẳn mục đích của việc này.
        val r = LauncherRequirements.check { req -> if (req.id == "overlay") null else true }
        assertTrue(r.allOk, "chưa đọc được KHÔNG phải là thiếu")
        assertEquals(1, r.unknown.size)
        assertTrue(r.missing.isEmpty())
        assertNull(r.notice(), "không hiện gì vì không kết luận được")
        assertTrue(r.logLine().contains("chưa đọc được"), "nhưng nhật ký phải ghi để lần sau soi")
    }

    @Test
    fun `ham doc nem loi thi coi nhu chua doc duoc, khong lam sap`() {
        val r = LauncherRequirements.check { throw IllegalStateException("ROM không có API") }
        assertEquals(LauncherRequirements.ALL.size, r.unknown.size, "mọi mục thành chưa-đọc-được")
        assertTrue(r.allOk, "không suy ra là thiếu")
        assertNull(r.notice())
    }

    // ── Phân loại ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `phan loai dung ai sua duoc`() {
        val all = report(*LauncherRequirements.ALL.map { it.id }.toTypedArray())
        assertEquals(LauncherRequirements.ALL.size, all.missing.size, "thiếu hết")
        assertEquals(
            all.missing.size,
            all.selfFixable.size + all.fixedAtBoot.size + all.needsUser.size + all.environment.size,
            "mỗi mục thiếu phải thuộc ĐÚNG một loại — không mục nào rơi ra ngoài",
        )
        assertTrue(all.selfFixable.map { it.id }.containsAll(listOf("notif_listener", "accessibility", "overlay")),
            "ba quyền kiểu ADB phải tự cấp được (công thức đã chạy thật trên xe)")
        assertTrue("shell" in all.environment.map { it.id }, "kênh shell không tự sửa được từ trong app")
        assertTrue("freeform" in all.fixedAtBoot.map { it.id },
            "cờ cửa sổ tự do do ĐƯỜNG KHỞI ĐỘNG gieo (một-nơi-ghi-duy-nhất) — vòng kiểm chỉ BÁO, không tự gieo")
        assertTrue("default_home" in all.needsUser.map { it.id }, "chọn màn hình chính phải do người dùng")
    }

    @Test
    fun `tinh nang loi duoc danh dau dung`() {
        val core = LauncherRequirements.ALL.filter { it.coreFeature }.map { it.id }
        assertTrue("shell" in core && "freeform" in core,
            "app-vào-ô là tính năng LÕI ⇒ hai điều kiện của nó phải được đánh dấu")
        assertFalse("notif_listener" in core, "đọc thông báo là tính năng nav, không phải lõi launcher")
    }

    @Test
    fun `viec do duong khoi dong lo thi van phai NOI khi con thieu`() {
        // Tới màn chính mà cờ cửa sổ tự do vẫn thiếu = việc gieo lúc khởi động đã KHÔNG ăn. Người dùng không có cách
        // nào tự biết điều đó, nên im lặng ở đây là bỏ mặc họ với một launcher không mở được app vào ô.
        val r = report("freeform")
        assertEquals(1, r.fixedAtBoot.size)
        assertTrue(r.selfFixable.isEmpty(), "vòng kiểm KHÔNG được tự nhận là mình cấp được")
        assertNotNull(r.notice(), "phải nói ra")
        assertTrue(r.notice()!!.contains("cửa sổ tự do"))
    }

    // ── F4: "đang hỏi người dùng" KHÁC "hạn chế môi trường" ──────────────────────────────────────

    /**
     * [ĐO] xe DiLink3.0 2026-09-14: lần mở đầu, hàng *Kênh điều khiển cửa sổ* hiện **"Hạn chế của môi trường"** —
     * sai. adbd đang **im lặng chờ** người lái bấm *"Cho phép gỡ lỗi USB?"* (ổ cắm `ESTABLISHED`, `Recv-Q` 24→48),
     * tức người dùng sửa được **bằng một cú tích**. Câu "không ai sửa được" đặt đúng vào lúc đó là câu tệ nhất.
     */
    @Test
    fun `dang hoi nguoi dung thi kenh shell la viec cua NGUOI DUNG, khong phai moi truong`() {
        val r = LauncherRequirements.check(awaitingShellApproval = true) { req -> req.id != "shell" }
        assertTrue("shell" in r.needsUser.map { it.id }, "phải là việc của NGƯỜI DÙNG")
        assertTrue(r.environment.isEmpty(), "và KHÔNG còn bị gọi là hạn chế môi trường")
        val row = r.needsUser.first { it.id == "shell" }
        assertNotNull(row.userAction, "phải nói việc cần làm")
        assertTrue(row.userAction!!.contains("Luôn cho phép"), "phải nhắc tích ô 'luôn cho phép'")
        assertNotNull(row.userActionEn, "câu việc-cần-làm phải có bản EN")
        assertFalse(
            row.userActionEn!!.any { it in "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ" },
            "bản EN còn dấu tiếng Việt (dịch nửa vời)",
        )
    }

    @Test
    fun `bien the giu nguyen ma, nhan va cau mat-gi cua dieu kien goc`() {
        val base = LauncherRequirements.SHELL_CHANNEL
        val variant = LauncherRequirements.SHELL_CHANNEL_AWAITING_APPROVAL
        assertEquals(base.id, variant.id, "vẫn là MỘT điều kiện — đổi mã là đổi thứ mà nhật ký/test chỉ đích danh")
        assertEquals(base.label, variant.label)
        assertEquals(base.losesWhatIfMissing, variant.losesWhatIfMissing)
        assertEquals(base.coreFeature, variant.coreFeature)
        assertFalse(variant in LauncherRequirements.ALL, "bảng ALL nói launcher CẦN gì, không nói đang HỎNG kiểu gì")
    }

    @Test
    fun `co dang-hoi KHONG duoc doi gi khi kenh shell dang DU`() {
        // Kênh lên rồi thì không có gì để nói — cờ đang-hỏi lúc đó chỉ có thể là tàn dư của một lượt dò cũ.
        val r = LauncherRequirements.check(awaitingShellApproval = true) { true }
        assertTrue(r.allOk)
        assertTrue(r.needsUser.isEmpty())
        assertNull(r.notice(), "đủ thì IM LẶNG")
    }

    @Test
    fun `mac dinh KHONG bat co - moi ben goi cu giu nguyen hanh vi`() {
        val r = report("shell")
        assertTrue("shell" in r.environment.map { it.id }, "không bật cờ ⇒ vẫn là hạn chế môi trường như trước F4")
        assertTrue(r.needsUser.none { it.id == "shell" })
    }

    @Test
    fun `tra muc theo ma`() {
        assertNotNull(LauncherRequirements.byId("shell"))
        assertNull(LauncherRequirements.byId("khong_co"), "mã lạ ⇒ null, không sập")
    }

    @Test
    fun `chi kiem thu launcher THAT SU dung`() {
        // R7: không gom quyền của ClusterNav cũ vào đây. Chốt con số để ai thêm mục phải nghĩ.
        // V1 pha NGHE: 6 → 7. `microphone` là quyền mà launcher THẬT SỰ dùng (ô *Nói với xe* · nút mic · đích
        // phím vô-lăng *Kachi nghe*) và **tự cấp được** qua kênh shell, nên nó thuộc đúng chỗ này chứ không phải
        // một hộp hỏi quyền riêng. Nó cố ý KHÔNG `coreFeature`: thiếu micro thì launcher vẫn đủ, chỉ mất lối tắt.
        assertEquals(7, LauncherRequirements.ALL.size,
            "Thêm/bớt điều kiện thì phải xem lại spec §2.2 — đừng nhét quyền không dùng vào vòng kiểm")
    }
}
