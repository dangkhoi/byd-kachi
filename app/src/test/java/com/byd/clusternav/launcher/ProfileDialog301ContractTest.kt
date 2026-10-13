package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KACHI-301 R10 — bộ chọn hồ sơ dạng THẺ + Xoá hồ sơ qua hộp XÁC NHẬN ═══════════════════════════════════════
 *
 * Khoá ba điều:
 *  1. **Xoá hồ sơ không còn một-chạm-là-mất** (spec §2: `SettingsSectionsProfiles.kt:282` gọi thẳng
 *     `deps.onDeleteProfile`). Intent xoá có ĐÚNG MỘT chỗ gọi, và chỗ đó là lambda của `KachiDialogs.confirm(…,
 *     destructive = true)`. Đưa lời gọi ra ngoài hộp (vd `setOnClickListener { deps.onDeleteProfile(name) }`) ⇒ đỏ.
 *  2. Bộ chọn mang đủ phần R10: chữ cái đầu · đang dùng · dòng phụ thật · lưới 2 cột · lối chân "Quản lý hồ sơ…".
 *  3. Chữ mới (`kprof_*`) đủ 5 thư mục ngôn ngữ, cùng tập khoá, khoá nào khai cũng có người dùng.
 */
class ProfileDialog301ContractTest {

    private val dir = "src/main/java/com/byd/clusternav/launcher"
    private val profiles by lazy { SourceRoots.codeOf("$dir/SettingsSectionsProfiles.kt") }
    private val chip by lazy { SourceRoots.codeOf("$dir/ProfileChip.kt") }

    @Test
    fun `xoa ho so chi chay trong lambda cua hop xac nhan pha huy`() {
        assertEquals(
            1, Regex("""deps\.onDeleteProfile\(""").findAll(profiles).count(),
            "intent xoá phải có ĐÚNG MỘT chỗ gọi — chỗ thứ hai là đường xoá không qua hộp xác nhận",
        )
        val fn = SourceRoots.body(profiles, "private fun confirmDelete(")
        assertTrue(
            Regex("""KachiDialogs\.confirm\([^{}]*destructive\s*=\s*true[^{}]*\)\s*\{\s*deps\.onDeleteProfile\(name\)\s*}""")
                .containsMatchIn(fn),
            "deps.onDeleteProfile phải nằm TRONG lambda xác nhận của KachiDialogs.confirm(destructive = true)",
        )
        assertTrue("R.string.kprof_delete_message" in fn, "hộp phải nói rõ MẤT GÌ (R9)")
        val row = SourceRoots.body(profiles, "private fun profileRow(")
        assertTrue("setOnClickListener { confirmDelete(name) }" in row, "nút Xoá phải mở hộp xác nhận")
    }

    @Test
    fun `bo chon ho so la the R10 va giu loi Quan ly ho so`() {
        val fn = SourceRoots.body(chip, "fun picker(")
        assertTrue("KachiDialogs.pick(" in fn, "dùng khuôn Chọn dùng chung")
        assertTrue("initial = initialOf(label)" in fn, "ô chữ cái đầu")
        assertTrue("current = active" in fn, "hồ sơ đang dùng nổi bật")
        assertTrue("viewModel.profileSummary(name)" in fn, "dòng phụ là dữ liệu THẬT của hồ sơ, không bịa")
        assertTrue("columns = 2" in fn, "lưới 2 cột (khuôn tự áp khi ≥ 4 hồ sơ)")
        assertTrue("linkLabel = activity.getString(R.string.kachi_profile_manage)" in fn, "lối chân Quản lý hồ sơ…")
        assertFalse("which == list.size" in fn, "Quản lý hồ sơ là nút chân, không còn là mục cuối danh sách")
    }

    @Test
    fun `chu kprof du 5 thu muc va deu duoc dung`() {
        val key = Regex("""<string name="(kprof_\w+)">""")
        val sets = listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").associateWith { f ->
            key.findAll(SourceRoots.text("src/main/res/$f/strings_kachi_prof301.xml")).map { it.groupValues[1] }.toSet()
        }
        val vi = sets.getValue("values")
        sets.forEach { (f, ks) -> assertEquals(vi, ks, "$f/strings_kachi_prof301.xml lệch tập khoá với values/") }
        val used = Regex("""R\.string\.(kprof_\w+)""").findAll(chip + profiles).map { it.groupValues[1] }.toSet()
        assertEquals(vi, used, "khoá khai mà không dùng (mồ côi) hoặc dùng mà không khai")
    }
}
