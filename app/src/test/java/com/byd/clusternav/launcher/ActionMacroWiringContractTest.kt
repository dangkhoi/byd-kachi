package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá DÂY NỐI của gói 3 (W2 — gói lệnh) + nợ gói 2 đã đóng (bảng lốp nói SAI CÁI GÌ).
 *
 * Quét SOURCE (không dựng được View trong JVM thuần); mọi phép quét đi qua [code] để **bỏ chú thích trước khi
 * kiểm** — nếu không thì viết tên hàm vào comment là test xanh, tức test tự lừa mình.
 */
class ActionMacroWiringContractTest {

    private fun code(relative: String): String =
        SourceRoots.text(relative)
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().joinToString("\n") { it.substringBefore("//") }

    /**
     * Thân hàm [signature] — cắt bằng **đếm ngoặc**, KHÔNG bằng mốc comment.
     *
     * [ĐO] 2026-09-11 (senior review): bản đầu cắt vùng bằng `substringBefore("// ── ĐỌC")`, nhưng [code] đã BỎ
     * comment trước đó nên mốc ấy không còn tồn tại ⇒ vùng quét trôi tới **hết file** (đo được: 6802 ký tự, đúng bằng
     * khoảng cách tới cuối file, chứa cả `fun readTile(` và `class ControlTileState`). Ba phép kiểm bên dưới khi ấy
     * vẫn xanh nhưng đang soi sai vùng — tức test trang trí. [signature] không tìm thấy ⇒ **nổ** thay vì âm thầm lấy
     * cả file (`substringAfter` trả nguyên chuỗi khi không thấy — cùng một cái bẫy).
     */
    private fun body(src: String, signature: String): String {
        val at = src.indexOf(signature)
        require(at >= 0) { "không thấy '$signature' trong source — test này đang soi sai chỗ" }
        val open = src.indexOf('{', at)
        require(open >= 0) { "không thấy thân hàm của '$signature'" }
        var depth = 0
        for (i in open until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return src.substring(open, i + 1)
            }
        }
        error("thân hàm '$signature' không đóng ngoặc")
    }

    private val factory by lazy { code("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt") }

    /**
     * Bảng trạng thái dùng chung — **tệp RIÊNG từ S4 · R12** (`ControlTileFactory.kt` chạm trần 500 dòng khi thêm ô
     * loại LAUNCHER, xem đầu `ControlTileState.kt`). Ba phép kiểm bên dưới cắt vùng theo tệp, nên đường dẫn phải đi
     * theo chỗ mã thật sự nằm; để nguyên đường cũ thì `SourceRoots.body` `require` hỏng và bài NỔ — đúng ý đồ của
     * nó (nổ còn hơn âm thầm quét cả file, xem KDoc [body]).
     */
    private val tileState by lazy { code("src/main/java/com/byd/clusternav/launcher/ControlTileState.kt") }
    private val dock by lazy { code("src/main/java/com/byd/clusternav/launcher/ControlDockView.kt") }
    private val widgets by lazy { code("src/main/java/com/byd/clusternav/launcher/WidgetViews.kt") }
    private val board by lazy { code("src/main/java/com/byd/clusternav/launcher/TyreBoardView.kt") }
    private val macroTile by lazy { body(factory, "fun macroTile(") }

    // ── R4: gói lệnh đặt được ở các vùng ─────────────────────────────────────────────────────────

    @Test
    fun `goi lenh dat duoc o CA thanh nut LAN o giua man`() {
        assertTrue(factory.contains("fun macroTile("), "phải có ô dựng cho gói lệnh")
        assertTrue(dock.contains("macroTile("), "thanh nút phải dựng được gói lệnh")
        assertTrue(widgets.contains("macroTile("), "ô giữa màn phải dựng được gói lệnh")
    }

    @Test
    fun `ma hanh dong khong tim thay trong bo nut thi thu tiep sang goi lenh`() {
        // Thiếu nhánh này thì bật gói lệnh vào thanh sẽ KHÔNG hiện gì mà cũng KHÔNG báo lỗi.
        assertTrue(dock.contains("ActionMacros.byId(id)"), "thanh nút phải tra tiếp sang gói lệnh")
        assertTrue(widgets.contains("ActionMacros.byId(id)"), "ô giữa màn phải tra tiếp sang gói lệnh")
    }

    // ── R1/R3: mỗi bước đi qua ĐÚNG CỬA theo kiểu nút ────────────────────────────────────────────

    @Test
    fun `moi buoc di qua dung cua theo kieu nut chu KHONG bat qua toggle`() {
        // `toggle` nén tham số thành 1/0: bước trỏ nút STEP (nhiệt/quạt) mất giá trị, bước SELECT mất chỉ số, bước
        // BUTTON với arg=0 thì không bấm gì. `actByKind` là MỘT bảng định tuyến duy nhất của dự án.
        assertTrue(macroTile.contains("actByKind("), "phải định tuyến theo ControlKind")
        assertFalse(macroTile.contains("port.toggle("),
            "bắn MỌI bước qua toggle là sai — xem KDoc CarControlPort.actByKind")
    }

    // ── R7: không chặn giao diện + chống bấm kép ─────────────────────────────────────────────────

    @Test
    fun `goi lenh chay tren thread nen chu KHONG tren thread chinh`() {
        assertTrue(macroTile.contains("Thread("), "gói có chờ giữa các bước ⇒ chạy trên thread chính sẽ treo giao diện")
        assertTrue(macroTile.contains("MacroRunner.run("), "phải dùng bộ chạy thuần, không tự viết vòng lặp")
        assertTrue(macroTile.contains("tile.post"), "cập nhật giao diện phải quay về thread chính")
    }

    @Test
    fun `chong bam kep theo MA GOI, khong theo View`() {
        assertTrue(macroTile.contains("state.beginRun(macro.id)"),
            "gói đang chạy thì bấm thêm KHÔNG được xếp thêm lượt — hai lượt chồng nhau làm lệnh bị bỏ")
        // ⚠ [SOÁT P1-1] Chốt PHẢI ở bảng trạng thái dùng chung, KHÔNG phải biến trong thân hàm dựng ô: trên xe ô
        // TRỘN (mục đọc + gói lệnh) từng bị dựng lại mỗi giây ⇒ ô mới có cờ mới `false` ⇒ bấm được lần hai trong khi
        // lượt một còn chạy ⇒ hai lượt "đóng hết kính" chồng nhau, đúng thứ chốt này sinh ra để chặn.
        assertFalse(macroTile.contains("AtomicBoolean("),
            "cờ KHÔNG được tạo trong thân ô — ô dựng lại là mất cờ")
        assertTrue(tileState.contains("fun beginRun(") && tileState.contains("fun endRun("),
            "chốt phải nằm ở ControlTileState (bảng dùng chung, theo mã gói)")
        // `beginRun` là hàm một-biểu-thức (không có ngoặc thân) ⇒ soi ngay dòng khai, không dùng helper đếm ngoặc.
        assertTrue(
            Regex("""fun beginRun\([^)]*\)[^\n]*putIfAbsent""").containsMatchIn(tileState),
            "chốt phải là phép đặt-nếu-chưa-có nguyên tử (putIfAbsent), không phải đọc-rồi-ghi hai bước",
        )
    }

    @Test
    fun `co chong bam kep phai duoc nha o finally chu KHONG nha trong tile post`() {
        // `View.post` trên view đã bị removeView chỉ XẾP HÀNG chờ lần gắn lại — có thể không bao giờ tới ⇒ cờ kẹt
        // `true` và ô chết hẳn. Nhả cờ phải nằm trên thread nền, TRƯỚC khi nhờ thread chính vẽ lại.
        assertTrue(macroTile.contains("finally"), "phải nhả cờ trong finally, kể cả khi giữa lượt ném lỗi")
        val release = macroTile.indexOf("state.endRun(macro.id)")
        val post = macroTile.indexOf("tile.post")
        assertTrue(release in 1 until post,
            "cờ phải được nhả TRƯỚC/NGOÀI tile.post (nhả bên trong = ô có thể chết hẳn)")
    }

    @Test
    fun `goi lenh mang dau chua kiem theo muc bang chung cua no`() {
        assertTrue(macroTile.contains("needsBadge()"), "gói có bước chưa kiểm thì phải hiện dấu")
    }

    // ── Đóng 3 điểm treo (soát xét lượt 2) ───────────────────────────────────────────────────────

    @Test
    fun `ghi trang thai KHONG duoc phu thuoc vao view con song`() {
        // [SOÁT] bản cũ cắt khối bằng `substringBefore("}")` ⇒ chỉ lấy tới dấu } của `runCatching` bên trong,
        // nên mọi lệnh nằm sau đó trong `tile.post` KHÔNG bị soi. Nay đếm ngoặc để lấy đúng cả khối.
        val fn = SourceRoots.body(factory, "fun macroTile(")
        val postBlock = SourceRoots.body(fn, "tile.post ")
        assertFalse(postBlock.contains("state.setOn("),
            "Ghi trạng thái phải nằm NGOÀI tile.post: nếu ô đã bị removeView (đổi bố cục/đơn vị/hồ sơ giữa lúc gói " +
                "đang chạy) thì việc post có chạy hay không là hành vi tài liệu Android KHÔNG nói rõ ⇒ trạng thái có " +
                "thể mất, và ô sẽ nói sai so với xe.")
        assertTrue(fn.contains("state.setOn("), "vẫn phải ghi trạng thái sau khi gói ghi thật")
    }

    @Test
    fun `bang trang thai dung chung phai an toan da luong`() {
        // [SOÁT] bản cũ viết `assertFalse(cóHashMap && !cóConcurrentHashMap)` ⇒ chỉ cần MỘT map là ConcurrentHashMap
        // thì assert LUÔN qua, dù hai map còn lại là HashMap thường. Nay kiểm TỪNG map một.
        val state = SourceRoots.body(tileState, "class ControlTileState")
        val maps = Regex("""private val (\w+) = ([\w.]*Map)<""").findAll(state)
            .map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue(maps.size >= 4, "phải có ít nhất 4 bảng (bật/tắt · giá trị · lựa chọn · chốt đang chạy): $maps")
        maps.forEach { (name, type) ->
            assertTrue(
                type.endsWith("ConcurrentHashMap"),
                "bảng '$name' dùng $type — gói lệnh ghi từ THREAD NỀN trong khi thread chính đang đọc để vẽ ⇒ " +
                    "map thường ở đây là tranh chấp dữ liệu thật",
            )
        }
    }

    @Test
    fun `hong thi bao cho nguoi dung chu khong chi ghi nhat ky`() {
        val fn = SourceRoots.body(factory, "fun macroTile(")
        assertTrue(fn.contains("notice("), "phải soạn câu báo từ kết quả gói")
        assertTrue(fn.contains("Toast"), "phải hiện ra cho người dùng — nhật ký thì người lái không đọc")
    }

    // ── R8: nợ gói 2 — bảng lốp nói SAI CÁI GÌ ───────────────────────────────────────────────────

    @Test
    fun `bang lop noi ro sai cai gi chu khong chi to mau`() {
        // Canh VIỆC (ô vẽ đọc lý do sai từ :core), không canh cách gõ tên biến — xem cùng bài học ở
        // `Goi2FeatureWiringContractTest.mot canh bao mot mau`. Cặp đôi với bài dưới (chuỗi "non"/"căng"/"lệch"
        // KHÔNG được nằm trong bộ vẽ) mới là phép khoá đủ: đọc `.reason` mà vẫn không tự ghép chữ.
        assertTrue(
            Regex("""\w+\.reason\b""").containsMatchIn(board),
            "trước đây chỉ có màu ⇒ người xem phải tự so số mới biết non hay quá căng",
        )
    }

    @Test
    fun `chu noi sai cai gi nam o tang loi chu khong viet trong bo ve`() {
        // Cùng lý do như ngưỡng: một chỗ định nghĩa, không rải chuỗi trong bộ vẽ
        listOf("\"non\"", "\"căng\"", "\"lệch\"").forEach {
            assertFalse(board.contains(it), "chuỗi $it phải nằm ở :core (TyreStatus.reason), không viết trong bộ vẽ")
        }
    }
}
