package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ FIX286 · R-F2 — "Thêm hồ sơ (bản sao)" từ hồ sơ toàn ô trống ⇒ bản sao cũng TRỐNG (đúng nghĩa bản sao) ═══════
 *
 * Spec `docs/specs/kachi-286-field-fixes.html` · R-F2. **Đã chốt 2026-10-03 — phiên điều phối, owner có thể đổi**: giữ
 * hành vi hiện tại (owner 14/09 về hồ sơ mới: *"giống cái đang dùng rồi sửa vài chỗ"*). [ĐO máy ảo E2E F2 03/10] bản
 * sao của bố cục toàn ô trống ra trống; 2.85 ra 3 widget (luật cũ "mọi ô trống ⇒ mặc định", ES5 đã gỡ).
 *
 * Hành vi nằm ở chỗ GHÉP ba mắt xích, không mắt nào tự nói ra nó — nên mỗi mắt một khẳng định:
 *  1. đường UI duy nhất tạo hồ sơ là NHÂN BẢN (`HomeViewModel.addProfile` 0 chỗ gọi [ĐO grep 03/10]);
 *  2. nhân bản chép NGUYÊN mọi khoá theo hồ sơ — kể cả `slot_*` mang chuỗi rỗng — và khoá vắng thì bản sao cũng vắng;
 *  3. dấu "đã từng lưu" là sự CÓ MẶT của khoá `slot_*` (`hasStoredSlots`, khoá ở `EmptySlotFix286WiringContractTest`).
 * ⇒ bản sao luôn hiện đúng thứ hồ sơ gốc đang hiện: gốc toàn ô trống đã lưu ⇒ bản sao trống; gốc chưa từng lưu ⇒ bản
 * sao cũng ra bố cục mặc định. Ai "sửa" F2 bằng cách bỏ chép `slot_*`, nạp mặc định riêng cho bản sao, hay chép
 * khoá vắng thành chuỗi rỗng sẽ làm đỏ bài này — đổi hành vi ấy là quyết định của owner (CLAUDE.md §16), không phải vá.
 */
class ProfileCopyFix286ContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    @Test
    fun `them ho so tren UI la nhan ban ho so dang dung`() {
        val ui = SourceRoots.body(code("SettingsSectionsProfiles.kt"), "private fun addProfile(")
        assertTrue("deps.onDuplicateProfile(name)" in ui, "nút 'Thêm hồ sơ (bản sao…)' phải đi đường NHÂN BẢN")
        assertTrue(
            "prefs.duplicateActiveProfile(name)" in SourceRoots.body(code("PrefsWorkspaceRepository.kt"), "override fun duplicateProfile("),
            "repository nhân bản qua đúng một hàm chép khoá",
        )
    }

    @Test
    fun `ban sao chep nguyen khoa o, khong nap mac dinh rieng`() {
        val dup = SourceRoots.body(code("WorkspacePrefsProfile.kt"), "internal fun WorkspacePrefs.duplicateActiveProfile(")
        assertTrue(
            Regex("""WorkspacePrefs\.PROFILE_SUFFIXES\.forEach \{ suffix ->\s*val from = keyOf\(source, suffix\)\s*val to = keyOf\(clean, suffix\)\s*val value = sp\.all\[from]\s*copyValue\(e, to, value\)""")
                .containsMatchIn(dup),
            "mọi hậu tố theo hồ sơ (gồm slot_0..SLOT_CAP-1) chép NGUYÊN giá trị gốc",
        )
        listOf("WorkspaceState.DEFAULT", "WorkspaceDefault", "slot_").forEach {
            assertFalse(it in dup, "nhân bản không được có nhánh riêng cho ô/bố cục mặc định (`$it`) — F2 chốt: bản sao là bản sao")
        }
        val copy = SourceRoots.body(code("WorkspacePrefsProfile.kt"), "private fun copyValue(")
        assertTrue("null -> e.remove(to)" in copy, "khoá VẮNG ở gốc ⇒ vắng ở bản sao (gốc chưa từng lưu ⇒ bản sao cũng chưa)")
        assertTrue("is String -> e.putString(to, value)" in copy, "ô trống lưu chuỗi RỖNG vẫn là khoá có mặt ⇒ bản sao 'đã lưu'")
    }
}
