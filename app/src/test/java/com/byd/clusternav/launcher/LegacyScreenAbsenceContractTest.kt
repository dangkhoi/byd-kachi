package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ MÀN CLUSTERNAV CŨ ĐÃ GỠ — VÀ KHÔNG ĐƯỢC MỌC LẠI ════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-remove-legacy-screen.html` **R1 · R5**. Ngày gỡ: 2026-09-13.
 *
 * ## Vì sao "đã xoá" cũng cần một bài canh
 * Xoá một màn thì trình biên dịch chỉ bắt được **lời gọi tường minh**. Ba đường còn lại im lặng:
 *  - một `Intent` dựng bằng tên lớp dạng chuỗi hoặc một `<activity>` khai lại trong manifest;
 *  - một tệp `MainActivity.kt` mới do người sau "khôi phục cho tiện đối chiếu";
 *  - `R.layout.activity_main` được inflate lại từ một màn khác — và tệp XML đó **vẫn nằm trên đĩa**
 *    (niêm phong T11), nên nó luôn ở đó chờ được dùng lại.
 * Ba đường đó đưa dự án về đúng trạng thái mà đợt này sinh ra để chấm dứt: hai bề mặt cấu hình song song,
 * ghi cùng một bộ khoá, lệch nhau dần.
 *
 * ## Hai tệp niêm phong: PHẢI còn, và phải NGUYÊN BYTE
 * `res/layout/activity_main.xml` + `res/values/strings.xml` nằm trong `T11_PATHS` của
 * `ExpansionTransportFenceTest` (offcar-planner) kèm hash. Chúng ở lại như **hiện vật**: không màn nào inflate,
 * không mã nào tham chiếu. Bài này đọc hằng hash **từ chính tệp nguồn của bài niêm phong** thay vì chép số sang
 * đây — chép là tạo bản sao thứ hai của một hằng, mà bản thứ hai thì trôi (đúng lỗi mà cả đợt này đang dọn).
 */
class LegacyScreenAbsenceContractTest {

    private val fence = "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionTransportFenceTest.kt"

    /** Gốc repo = thư mục tổ tiên gần nhất có `.git` (working dir của test tuỳ module). */
    private fun repoRoot(): Path? =
        generateSequence(SourceRoots.path("src/main/AndroidManifest.xml").toAbsolutePath()) { it.parent }
            .firstOrNull { Files.exists(it.resolve(".git")) }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ── R1: không còn lớp, không còn khai báo, không còn intent ─────────────────────────────────────────────

    @Test
    fun `khong con tep MainActivity nao trong cay nguon`() {
        assertFalse(
            SourceRoots.exists("src/main/java/com/byd/clusternav/MainActivity.kt"),
            "màn ClusterNav cũ đã gỡ 2026-09-13 — dựng lại một bản là quay về hai bề mặt cấu hình song song",
        )
    }

    @Test
    fun `manifest khong con khai bao activity MainActivity`() {
        val manifest = SourceRoots.text("src/main/AndroidManifest.xml")
        val declared = Regex("""android:name="\.MainActivity"""").containsMatchIn(manifest)
        assertFalse(declared, "manifest không được khai `.MainActivity` — lớp đó không còn tồn tại")
        // Chiều thứ hai: icon duy nhất vẫn là Kachi (SingleLauncherIconContractTest canh kỹ hơn; ở đây chỉ
        // chắc rằng việc gỡ màn cũ không vô tình mang theo activity còn lại).
        assertTrue(manifest.contains(".launcher.KachiHomeActivity"), "Kachi phải còn là activity chính")
    }

    @Test
    fun `khong mã nào dựng intent hay tham chieu toi man cu`() {
        val offenders = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
            }.mapNotNull { file ->
                val code = file.toFile().readText()
                    .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
                    .lines().joinToString("\n") { it.substringBefore("//") }
                val hit = listOf("MainActivity::class", "R.layout.activity_main", "\"com.byd.clusternav.MainActivity\"")
                    .firstOrNull { it in code }
                hit?.let { "${file.fileName}: $it" }
            }
        }
        assertEquals(
            emptyList<String>(), offenders.sorted(),
            "còn mã trỏ tới màn cũ (intent tường minh / inflate layout cũ). Mọi đường đó nay phải về " +
                "`KachiHomeActivity`, kèm extra `open_settings_group` nếu có nhóm Cài đặt tương ứng:\n$offenders",
        )
    }

    @Test
    fun `bien the layout rong cua man cu da bien mat`() {
        assertFalse(
            SourceRoots.exists("src/main/res/layout-w960dp/activity_main.xml"),
            "bản `layout-w960dp` (bản xe thật render) không bị niêm phong ⇒ phải gỡ cùng màn; giữ lại là giữ " +
                "một tệp 500+ dòng không ai dựng và một bài parity không còn đối tượng",
        )
    }

    // ── R5: hai tệp niêm phong còn nguyên byte ──────────────────────────────────────────────────────────────

    /**
     * Hash ĐỌC TỪ HẰNG của `ExpansionTransportFenceTest` (module khác, `private`) bằng cách quét nguồn của
     * chính bài đó — nên nếu ai đổi hằng để "chữa" một lần sửa lén thì bài này KHÔNG đỏ, nhưng bài niêm phong
     * gốc sẽ đỏ và lịch sử git ghi lại lần đổi hằng. Ở đây canh đúng một việc: **đợt gỡ màn cũ không đụng vào
     * hai tệp đó** (R5), kể cả khi có người sửa hằng cùng lúc.
     */
    @Test
    fun `hai tep niem phong con ton tai va dung hash cua bai T11`() {
        val root = repoRoot() ?: return  // không phải checkout git (CI tarball) ⇒ bỏ qua
        val fenceSource = root.resolve(fence)
        assertTrue(Files.isRegularFile(fenceSource, LinkOption.NOFOLLOW_LINKS), "thiếu bài niêm phong T11: $fence")

        val expected = Regex(""""(app/src/main/res/[^"]+)" to "([0-9a-f]{64})"""")
            .findAll(fenceSource.toFile().readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(
            setOf("app/src/main/res/layout/activity_main.xml", "app/src/main/res/values/strings.xml"),
            expected.keys,
            "đọc hụt hằng `T11_HASHES` — regex của bài này hỏng, hoặc danh sách niêm phong vừa đổi",
        )
        expected.forEach { (relative, hash) ->
            val file = root.resolve(relative)
            assertTrue(
                Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS),
                "$relative phải Ở LẠI trên đĩa như hiện vật niêm phong — xoá nó là phá T11",
            )
            assertEquals(hash, sha256(Files.readAllBytes(file)), "$relative đã đổi byte — đợt gỡ màn cũ cấm đụng")
        }
    }

    /**
     * Hiện vật thì phải **mồ côi**: không mã nào tham chiếu `R.layout.activity_main`.
     *
     * Đây là nửa còn lại của bài trên. Tệp còn nằm đó vì hash, KHÔNG phải vì còn dùng; một ngày nào đó ai đó
     * inflate lại nó là màn cũ sống dậy mà không cần tệp `MainActivity.kt` nào.
     */
    @Test
    fun `layout niem phong khong con ai inflate`() {
        val users = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
            }.filter { "R.layout.activity_main" in it.toFile().readText() }.map { it.fileName.toString() }
        }
        assertEquals(emptyList<String>(), users.sorted(), "layout niêm phong phải mồ côi, nhưng đang bị dùng ở: $users")
    }
}
