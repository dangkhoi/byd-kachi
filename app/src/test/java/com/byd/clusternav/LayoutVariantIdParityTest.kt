package com.byd.clusternav

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Khoá bất biến: **một view id mà code chạm phải có mặt ở MỌI biến thể layout**, không chỉ bản dọc.
 *
 * ── VÌ SAO CÓ FILE NÀY — cùng một lỗi đã xảy ra HAI LẦN trong 4 ngày ────────────────────────────────
 * Đầu xe là **1920×1080 @ density 240 ⇒ 1280dp** (đo thật:
 * `docs/diagnostics/multi-app-nav-source-channels-2026-08-20.md:55`), nên Android chọn
 * `res/layout-w960dp/activity_main.xml`, **KHÔNG** phải `res/layout/activity_main.xml`. Máy build lại
 * sinh `R.id.*` từ **hợp** của mọi biến thể, nên thêm một id vào duy nhất bản dọc vẫn **compile xanh**
 * và mọi test cũ vẫn xanh — rồi `findViewById` trả `null` **trên xe** và `.setOnClickListener` ném NPE
 * ngay trong `onCreate` ⇒ app không mở được.
 *
 *  • Lần 1 — commit `5d4dab6`: `txt_nav_source_active` chỉ có ở bản dọc ⇒ crash lúc mở app.
 *    Bài học lúc đó **chỉ nằm trong commit message**, không có test nào khoá lại (trái `CLAUDE.md §10`).
 *  • Lần 2 — F3 (2026-08-24): `btn_voicekey_add` / `list_voicekey_bindings` / `txt_voicekey_empty`
 *    lặp lại y hệt. 2058 test xanh trong khi app **crash lúc mở trên xe**.
 *
 * Test này là cái khoá còn thiếu. Nó quét **quan hệ code ↔ MỌI biến thể layout**, chứ không đọc một file
 * layout đơn lẻ như các test trước (đó chính là điểm mù đã để lọt cả hai lần).
 *
 * ── PHÉP THỬ LÀM-ĐỎ (bắt buộc, `CLAUDE.md §10` + P5.3) ───────────────────────────────────────────
 * Xoá khối `btn_voicekey_add` khỏi `res/layout-w960dp/activity_main.xml` ⇒ CẢ HAI test dưới phải ĐỎ.
 * Kết quả đo 2026-08-24 ghi trong `docs/PROJECT-BACKLOG.md` mục F3.
 *
 * Test thuộc `:app` vì nó canh tài nguyên của `:app`; nó chỉ đọc file văn bản, không đụng API Android.
 */
class LayoutVariantIdParityTest {

    /** Thư mục `app/src/main/res` — dò từ một file layout đã biết để không phụ thuộc CWD của Gradle. */
    private val resDir: File by lazy {
        SourceRoots.path("src/main/res/layout/activity_main.xml").toFile().parentFile!!.parentFile!!
    }

    /** Mọi thư mục layout kể cả biến thể theo qualifier (`layout`, `layout-w960dp`, `layout-land`…). */
    private fun layoutDirs(): List<File> =
        (resDir.listFiles() ?: emptyArray())
            .filter { it.isDirectory && (it.name == "layout" || it.name.startsWith("layout-")) }
            .sortedBy { it.name }

    /** Tên id được KHAI BÁO trong một file layout (`android:id="@+id/foo"`). */
    private fun declaredIds(f: File): Set<String> =
        DECLARE_ID.findAll(f.readText()).map { it.groupValues[1] }.toSet()

    /** Mọi biến thể của một tên layout, khoá theo tên file. */
    private fun variantsOf(fileName: String): List<File> =
        layoutDirs().map { File(it, fileName) }.filter { it.isFile }

    // ───────────────────────────────────────────────────────────────────────────────────────────
    // 1 · Hai biến thể của CÙNG một layout phải khai báo CÙNG một tập id
    // ───────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Bất biến chung, không gắn với màn hình nào: layout cùng tên ⇒ cùng tập id.
     *
     * Cố ý so **cả hai chiều** (thiếu ở biến thể rộng *và* thiếu ở bản dọc): id chỉ có ở bản rộng thì
     * máy nào hẹp hơn 960dp — vd emulator lúc dev — sẽ crash y hệt, chỉ đổi vai.
     *
     * Khác biệt hình thức (một cột / hai cột, thứ tự thẻ, chữ nhãn) vẫn tự do; test chỉ chặn **thiếu id**.
     */
    @Test
    fun `moi bien the cua cung mot layout phai khai bao cung tap id`() {
        val byName = layoutDirs().flatMap { d -> (d.listFiles() ?: emptyArray()).filter { it.extension == "xml" } }
            .groupBy { it.name }
            .filterValues { it.size > 1 }

        assertTrue(
            byName.isNotEmpty(),
            "Không thấy layout nào có nhiều biến thể — dò sai thư mục res? resDir=$resDir",
        )

        for ((name, files) in byName) {
            val ref = files.first()
            val refIds = declaredIds(ref)
            for (other in files.drop(1)) {
                val otherIds = declaredIds(other)
                val missingHere = refIds - otherIds
                val missingThere = otherIds - refIds
                assertEquals(
                    emptySet<String>(),
                    missingHere,
                    "$name: id có ở ${ref.parentFile!!.name} nhưng THIẾU ở ${other.parentFile!!.name} " +
                        "⇒ findViewById trả null trên màn dùng biến thể đó (đầu xe = w960dp) → NPE lúc mở app.",
                )
                assertEquals(
                    emptySet<String>(),
                    missingThere,
                    "$name: id có ở ${other.parentFile!!.name} nhưng THIẾU ở ${ref.parentFile!!.name} " +
                        "⇒ crash trên màn hẹp hơn (emulator/điện thoại lúc dev).",
                )
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────────────────────────
    // 2 · Mọi R.id mà MainActivity CHẠM phải có ở MỌI biến thể của layout nó setContentView
    // ───────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Vế thứ hai, đi từ **code** ra: test 1 chỉ so hai file layout với nhau, nên nếu một id bị xoá khỏi
     * *cả hai* biến thể mà code vẫn gọi thì test 1 vẫn xanh. Vế này bắt đúng ca đó.
     *
     * Id được chấp nhận nếu có ở **mọi** biến thể của layout đặt bằng `setContentView(R.layout.X)`,
     * **hoặc** ở một layout con mà chính MainActivity `inflate(R.layout.Y)` (vd `row_voicekey_binding`).
     */
    @Test
    fun `moi R id MainActivity cham deu ton tai o moi bien the layout`() {
        val src = KotlinSource.stripComments(
            SourceRoots.text("src/main/java/com/byd/clusternav/MainActivity.kt"),
        )

        // `android.R.id.…` là id của nền tảng, không nằm trong res của app ⇒ lookbehind loại thẳng khi quét
        // (không dùng phép trừ tập, vì trừ sẽ xoá nhầm nếu app tình cờ có id trùng tên với id nền tảng).
        val appIds = REF_ID.findAll(src).map { it.groupValues[1] }.toSortedSet()

        assertTrue(appIds.size >= 20, "Quét hụt R.id trong MainActivity (chỉ thấy ${appIds.size}) — regex hỏng?")

        val contentLayouts = SET_CONTENT.findAll(src).map { it.groupValues[1] }.toSet()
        val inflated = INFLATE_LAYOUT.findAll(src).map { it.groupValues[1] }.toSet()
        assertTrue(contentLayouts.isNotEmpty(), "Không thấy setContentView(R.layout.…) trong MainActivity")

        // Id nào ở layout con (được inflate) thì không đòi phải có trong activity_main.
        val childIds = inflated.flatMap { variantsOf("$it.xml") }.flatMap { declaredIds(it) }.toSet()

        val problems = mutableListOf<String>()
        for (layout in contentLayouts) {
            val variants = variantsOf("$layout.xml")
            assertTrue(variants.isNotEmpty(), "Không tìm thấy file layout nào tên $layout.xml dưới $resDir")
            for (v in variants) {
                val have = declaredIds(v)
                for (id in appIds) {
                    if (id !in have && id !in childIds) {
                        problems += "${v.parentFile!!.name}/${v.name} THIẾU @+id/$id (MainActivity gọi R.id.$id)"
                    }
                }
            }
        }
        assertEquals(
            emptyList<String>(),
            problems,
            "MainActivity chạm id không có ở biến thể layout mà máy đó render ⇒ findViewById=null → NPE " +
                "lúc mở app (đầu xe 1920×1080@240 = 1280dp ⇒ dùng layout-w960dp).",
        )
    }

    private companion object {
        val DECLARE_ID = Regex("""@\+id/([A-Za-z0-9_]+)""")
        val REF_ID = Regex("""(?<!android\.)\bR\.id\.([A-Za-z0-9_]+)""")
        val SET_CONTENT = Regex("""setContentView\(\s*R\.layout\.([A-Za-z0-9_]+)\s*\)""")
        val INFLATE_LAYOUT = Regex("""inflate\(\s*R\.layout\.([A-Za-z0-9_]+)""")
    }
}
