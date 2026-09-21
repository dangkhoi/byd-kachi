package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ UX-OVERHAUL WP1 · R1.1 — **KHÔNG CÒN MỘT CÁI VIỀN NÀO** trong tầng UI launcher ══════════════════════════════
 *
 * Owner 2026-09-20, sau khi xem ảnh bản WP1 đầu: *"1, gỡ hết, tất các button, widget gì gỡ hết, KHÔNG còn viền ở
 * BẤT CỨ ĐÂU hết."*
 *
 * ## Vì sao cần bài canh RIÊNG này, dù `SurfaceMaterialContractTest` đã cấm `setStroke` trong `surface()`
 * Bài kia chỉ soi **thân một hàm** (`KachiTheme.surface`). [ĐO] lượt WP1 đầu: `surface()` sạch viền, bài đó XANH, mà
 * màn chính **vẫn còn 27 cột/hàng sáng 1px** ở dải thanh trên + dải thanh nút — vì viền nằm ở *chỗ khác*
 * (`KachiTopStrip` · `ControlDockView` · `gradientSoft` · ô trống · ô màu · ô tick · nút Xoá · bảng lốp · trình vẽ
 * bố cục · ô xem-trước cụm). Tức là **phạm vi của bài canh nhỏ hơn phạm vi của lời hứa** — đúng họ lỗi đã ghi trong
 * project-context (*"bài canh bỏ sót đúng thứ nó sinh ra để bắt"*). Bài này canh **cả tầng vẽ**, không một hàm.
 *
 * ## Hai loại "nét" và vì sao phải phân biệt bằng DANH SÁCH, không bằng cảm nhận
 *  - **VIỀN** = hình chữ nhật/bo góc chạy quanh **mép của một bề mặt** (thẻ · nút · chip · ô · thanh). Đây là thứ
 *    owner gỡ, và `GradientDrawable.setStroke` gần như chỉ dùng để làm việc này ⇒ **cấm tuyệt đối**, không ngoại lệ.
 *  - **MỰC VẼ** = nét LÀ nội dung: cung của đồng hồ đo, hình xe line-art, lưới của trình vẽ bố cục. Gỡ nó không
 *    phải "bỏ viền" mà là **xoá mất cái hình**. Đây đi qua `Paint.Style.STROKE` ⇒ cho phép, nhưng phải khai tên tệp
 *    vào [INK_VIEWS] **kèm lý do**, theo lệ `SettingsCatalog.NOT_SETTINGS`. Tệp mới dùng nét mà chưa khai ⇒ ĐỎ, buộc
 *    người sửa phải nói ra mình đang vẽ hình hay đang vẽ khung.
 */
class ZeroBorderContractTest {

    /**
     * `GradientDrawable.setStroke` = **0 lần** trong tầng vẽ launcher.
     *
     * Không có danh sách miễn trừ, có chủ đích: một `setStroke` trên `GradientDrawable` chỉ làm được đúng một việc —
     * kẻ đường bao quanh mép hình. Mở một ngoại lệ ở đây là mở lại toàn bộ họ lỗi (đã ba lượt: `surfEdge` Pass-4 ·
     * nét đỉnh Pass-5 · bevel 1px WP1).
     */
    @Test
    fun `khong con setStroke nao trong tang ve launcher`() {
        val offenders = uiSources().mapNotNull { f ->
            val n = Regex("""\bsetStroke\s*\(""").findAll(code(f)).count()
            if (n == 0) null else "${f.fileName}: $n"
        }
        assertEquals(
            emptyList<String>(), offenders,
            "còn viền (setStroke) trong tầng vẽ launcher. Phân tách CHỈ bằng MÀU FILL + khoảng cách; bề mặt nào " +
                "trước đây chỉ thấy được nhờ viền thì đổi sang một FILL có tương phản ĐO ĐƯỢC (xem " +
                "docs/_handoff/ux-wp1-zero-borders.md §3), đừng kẻ lại: $offenders",
        )
    }

    /**
     * Không tệp nào vẽ nét mà **chưa khai** vào [INK_VIEWS].
     *
     * Đây là nửa còn lại của bài trên: `setStroke` không phải đường DUY NHẤT để có một cái khung —
     * `canvas.drawRoundRect(rect, r, r, paintNét)` cho đúng cùng kết quả. Không thể cấm thẳng `Paint.Style.STROKE`
     * (đồng hồ đo và hình xe cần nó), nên thay vì đoán, bài này bắt **mọi** chỗ dùng nét phải có tên trong danh sách
     * — và [INK_VIEWS] ghi từng dòng *nét đó vẽ CÁI GÌ*.
     */
    @Test
    fun `net ve chi ton tai o cac view line art da khai`() {
        val undeclared = uiSources()
            .filter { Regex("""Paint\.Style\.STROKE""").containsMatchIn(code(it)) }
            .map { it.fileName.toString() }
            .filterNot { it in INK_VIEWS }
        assertEquals(
            emptyList<String>(), undeclared,
            "tệp dùng nét (Paint.Style.STROKE) mà chưa khai vào INK_VIEWS. Nếu nét đó là MỰC VẼ (cung đồng hồ, hình " +
                "xe, lưới) thì khai tên + lý do; nếu nó là KHUNG quanh một bề mặt thì gỡ đi (WP1 · R1.1): $undeclared",
        )
    }

    /** Danh sách miễn trừ không được mục rữa — khai một tệp rồi tệp đó hết dùng nét thì phải gỡ khỏi danh sách. */
    @Test
    fun `danh sach INK_VIEWS khong co muc chet`() {
        val using = uiSources()
            .filter { Regex("""Paint\.Style\.STROKE""").containsMatchIn(code(it)) }
            .map { it.fileName.toString() }
            .toSet()
        val dead = INK_VIEWS.keys.filterNot { it in using }
        assertEquals(
            emptyList<String>(), dead,
            "mục miễn trừ không còn ai dùng — gỡ khỏi INK_VIEWS, đừng để nó thành giấy phép cho một cái viền tương " +
                "lai (cùng lẽ `SettingsCatalog.NOT_SETTINGS`): $dead",
        )
    }

    /**
     * Hai đường kẻ owner chỉ đích danh ([ĐO] ảnh `after-home-dark.png`: y=107 và y=882, mỗi vạch dài > 1800px) phải
     * chết ở **chính chỗ** sinh ra chúng.
     *
     * Bài `khong con setStroke nao` đã phủ cả hai, nhưng nó nói *"ở đâu đó còn viền"*; bài này ghim đúng **hai chỗ
     * gọi** để khi ai đó thêm viền lại thì thông điệp nói ngay *thanh trên* / *thanh nút*, không phải đi tìm.
     */
    @Test
    fun `thanh tren va thanh nut khong con vien`() {
        val strip = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiTopStrip.kt")
        assertTrue(
            "KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.BAR_TOP)" in strip,
            "thanh trên phải dựng nền KHÔNG viền (card 3 đối số). Viền cũ = vạch 1px chạy gần hết chiều ngang ở đáy " +
                "thanh — một trong hai đường kẻ owner chỉ đích danh.",
        )
        val dock = SourceRoots.body(
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ControlDockView.kt"), "init {",
        )
        assertTrue(
            "setColor(c(KachiTheme.BAR))" in dock && "setStroke" !in dock,
            "thanh nút phải là MỘT tô đặc, không viền (đường kẻ thứ hai owner chỉ đích danh)",
        )
    }

    /**
     * Hàm dựng thẻ/pill **không được có tham số viền**.
     *
     * Bản WP1 đầu chỉ đổi `stroke` về mặc định `CLEAR`, và [ĐO] vẫn còn **năm** chỗ gọi truyền viền vào (hai chỗ
     * `KachiTopStrip`/`VoiceOverlay` grep thấy, cộng **ba** chỗ chỉ lộ ra khi trình biên dịch từ chối:
     * `AppDrawer.notice` · `SettingsSectionsProfiles` nút Xoá · ô con nhóm WARN/ALERT). Một tham số opt-in là *lời
     * nhắc*; **không có tham số** là **cổng biên dịch**. Bài này khoá cái cổng đó lại.
     */
    @Test
    fun `card va pill khong con tham so vien`() {
        val d = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiThemeDrawables.kt")
        listOf("fun KachiTheme.card(", "fun KachiTheme.pill(").forEach { head ->
            val sig = d.substringAfter(head, "").substringBefore("GradientDrawable =", "")
            assertTrue(sig.isNotEmpty(), "không tìm thấy chữ ký `$head` — sửa bài này, đừng để nó quét tràn")
            assertTrue(
                "stroke" !in sig,
                "`$head` mọc lại tham số viền. Xoá tham số là cổng biên dịch chống viền quay lại (KDoc card()); " +
                    "đổi mặc định về CLEAR thì KHÔNG đủ — lượt trước đã thử và lọt 5 chỗ gọi.",
            )
        }
    }

    private companion object {
        /**
         * Tệp được phép dùng `Paint.Style.STROKE` — **nét LÀ nội dung**, không phải khung quanh một bề mặt.
         *
         * Giá trị = lý do, để lượt sửa sau đọc được vì sao nó ở đây mà không phải "một viền được tha".
         */
        val INK_VIEWS: Map<String, String> = mapOf(
            "RingView.kt" to "cung + rãnh của đồng hồ đo — nét CHÍNH LÀ cái vòng; gỡ là không còn đồng hồ",
            "Pm25GaugeView.kt" to "cung đồng hồ bụi mịn — cùng lẽ RingView",
            // ⚠ WP3-v5: "CarArtPainter.kt" (hình xe vector) đã XOÁ — hình xe nay là ẢNH bitmap ([CarImageLayer],
            // dùng FILL + PorterDuff feather, KHÔNG stroke). "DoorBoardView.kt" cũng bỏ khỏi đây: bản mới không còn
            // đường tách vùng tô (chấm màu FILL trên ảnh), nên nó hết dùng STROKE — để lại là "mục chết".
            "SeatDiagramView.kt" to "đường bao thân xe + đệm/ốp/tựa của ghế — nét LÀ hình cái ghế",
            "GridEditorView.kt" to "LƯỚI ô của trình vẽ bố cục — thứ người dùng canh theo khi kéo khung " +
                "(viền quanh từng khung đã GỠ ở WP1; chỉ còn lưới)",
            "ClusterPreviewView.kt" to "vạch chia hai nửa cụm = nội dung; khung quanh mặt cụm đã tắt từ chỗ gọi " +
                "launcher (`line = CLEAR` ở SettingsSectionsCast) vì view này dùng chung với màn ClusterNav cũ",
        )

        /**
         * Tầng UI launcher = gói `launcher/` **cộng** các view tự vẽ được nhúng vào màn Cài đặt của launcher.
         *
         * Hai tệp nhúng (`comfort/SeatDiagramView` · `ui/ClusterPreviewView`) nằm ngoài `launcher/` nên phép quét
         * theo thư mục của `SurfaceMaterialContractTest` không chạm tới — nhưng người dùng nhìn thấy chúng **bên
         * trong** màn Cài đặt của Kachi, nên với owner chúng LÀ giao diện launcher.
         *
         * KHÔNG gồm: `speedbadge/` · `modules/clustercast/` · `VmBubblePlacementView` — ba bề mặt đó vẽ **trên cụm**
         * hoặc trên pixel của app khác, đã chạy ổn trên xe từ trước, và CLAUDE.md §6 cấm đảo thứ tự đường đã chạy
         * tốt ngoài hiện trường. Cùng lối miễn trừ mà `SurfaceMaterialContractTest` đã ghi cho `speedbadge/`.
         */
        val EMBEDDED: List<String> = listOf(
            "src/main/java/com/byd/clusternav/comfort/SeatDiagramView.kt",
            "src/main/java/com/byd/clusternav/comfort/Pm25GaugeView.kt",
            "src/main/java/com/byd/clusternav/ui/ClusterPreviewView.kt",
        )
    }

    private fun uiSources(): List<Path> {
        val root = SourceRoots.moduleSourceRoots().first { it.toString().contains("app") }
        val pkg = Files.walk(root.resolve("com/byd/clusternav/launcher"))
            .use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        val embedded = EMBEDDED.map { SourceRoots.path(it) }
        embedded.forEach {
            assertTrue(Files.exists(it), "tệp nhúng khai trong EMBEDDED không tồn tại: $it — sửa danh sách")
        }
        return (pkg + embedded).sortedBy { it.fileName.toString() }
    }

    /** Bỏ chú thích trước khi quét — KDoc của chính lượt WP1 nhắc tên `setStroke` rất nhiều lần. */
    private fun code(f: Path): String = f.toFile().readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }
}
