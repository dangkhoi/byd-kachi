package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ PROFILE-IO-0930 — DÂY NỐI xuất/nhập hồ sơ từ nút Cài đặt tới tệp (spec `kachi-profiles-are-everything.html` §12) ═
 *
 * Phép thuần (lọc chia sẻ · tên tệp · header · chọn ảnh chụp · kế hoạch nhập) có test chạy thật ở `:core`
 * (`ProfileTransferTest`, `ProfileFilesTest`, `ProfileSharePolicyTest`). Bài ở đây canh phần `:app` không dựng được
 * trong JVM: nút → deps → HomePanels → ProfileIoStore/ViewModel → repository → WorkspacePrefs. Quét source đã bỏ chú
 * thích ([SourceRoots.codeOf]), cắt vùng bằng [SourceRoots.body] (nổ nếu mốc vắng).
 *
 * Khoá ba lỗi owner duyệt sửa 2026-09-30:
 *  • (1) nhập MỌI tệp mỗi lần bấm ⇒ nhân bản: nay nút Nhập mở hộp chọn, và chỉ lượt chạm một dòng mới nhập MỘT tệp;
 *  • (3) xuất chia sẻ phải thật sự truyền kiểu SHARE xuống tới `ProfileTransfer.export`;
 *  • (4) `exportProfile` không được tự chụp tệp sống cho hồ sơ bất kỳ.
 */
class ProfileIoWiringContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val section by lazy { code("SettingsSectionsProfiles.kt") }
    private val panels by lazy { code("HomePanels.kt") }
    private val store by lazy { code("ProfileIoStore.kt") }
    private val wiring by lazy { code("KachiHomeWiring.kt") }
    private val vm by lazy { code("HomeViewModel.kt") }
    private val repo by lazy { code("PrefsWorkspaceRepository.kt") }
    private val profileIo by lazy { code("WorkspacePrefsProfile.kt") }
    private val snapshot by lazy { code("WorkspacePrefsSnapshot.kt") }

    // ── (1) Nhập = chọn MỘT tệp ──────────────────────────────────────────────────────────────

    @Test
    fun `nut Nhap mo hop chon, chi luot cham mot dong moi nhap dung mot tep`() {
        val io = SourceRoots.body(section, "private fun profileIo(body: LinearLayout)")
        assertTrue(io.contains("deps.profileFiles()") && io.contains("SettingsDialogs.pick("), "nút Nhập phải mở hộp chọn tệp")
        assertEquals(1, Regex("""\bimportFile\(""").findAll(io).count(), "chỉ MỘT chỗ nhập: lượt chạm một dòng")
        assertTrue(Regex("""\{\s*i\s*->\s*importFile\(files\[i]\)\s*}""").containsMatchIn(io), "nhập đúng tệp đã chạm")
        assertFalse(Regex("""files\.(forEach|map|mapNotNull|onEach)\s*\{[^}]*importFile""").containsMatchIn(io), "nhập hàng loạt")
        assertTrue(io.contains("R.string.kachi_profile_import_none, deps.profileFolderPath()"), "rỗng ⇒ nói đường dẫn thư mục")
        val one = SourceRoots.body(section, "private fun importFile(entry: ProfileFiles.Entry)")
        assertTrue(one.contains("deps.onImportProfileFile(entry.fileName)"))
        // FIX286 · PI3: toast thành tiêu đề hộp thoại sau khi nhập — vẫn nói TÊN hồ sơ vừa tạo (tên thật từ nơi lưu).
        assertTrue(one.contains("R.string.kachi_profile_imported, ProfileNames.display(created.name)"), "hộp thoại nói TÊN hồ sơ vừa tạo")
    }

    @Test
    fun `HomePanels doc dung mot tep qua ProfileIoStore read, khong con readAll`() {
        val imp = SourceRoots.body(panels, "onImportProfileFile = {")
        assertTrue(imp.contains("ProfileIoStore.read(activity, fileName)") && imp.contains("onImportProfileData(it)"))
        assertFalse(Regex("""ProfileIoStore\.(list|readAll)\(""").containsMatchIn(imp), "lượt nhập không được quét thư mục")
        assertTrue(SourceRoots.body(panels, "profileFiles = {").contains("ProfileIoStore.list(activity)"))
        assertFalse(store.contains("readAll"), "đường đọc-mọi-tệp của #4 phải gỡ hẳn")
        assertTrue(SourceRoots.body(store, "fun read(ctx: Context, fileName: String): String?").contains("ProfileFiles.read(d, fileName)"))
        assertFalse(Regex("""\.readText\(|\.writeText\(|listFiles\(""").containsMatchIn(store), "I/O tệp chỉ đi qua ProfileFiles (:core, có test)")
    }

    @Test
    fun `ViewModel nhap mot chuoi va tra ten ho so vua tao`() {
        val fn = SourceRoots.body(vm, "fun importProfile(data: String): ProfileImportReport?")
        assertTrue(fn.contains("repository.importProfileData(data)") && fn.contains("reload {"))
        assertTrue(fn.contains("return next.report"), "FIX286 · PI3: tên hồ sơ lấy từ nơi lưu, không đoán bằng hiệu danh sách")
        assertFalse(vm.contains("fun importProfiles("), "API nhập-danh-sách của #4 phải gỡ")
        assertTrue(Regex("""onImportProfileData\s*=\s*\{\s*data\s*->\s*viewModel\.importProfile\(data\)\s*}""").containsMatchIn(wiring), "nút chết")
    }

    // ── (2)(3) Xuất: hai kiểu, tệp mới ───────────────────────────────────────────────────────

    @Test
    fun `hai nut xuat truyen dung kieu xuong toi ProfileTransfer export`() {
        val io = SourceRoots.body(section, "private fun profileIo(body: LinearLayout)")
        assertTrue(io.contains("ProfileTransfer.Kind.FULL to R.string.kachi_profile_export_full"))
        assertTrue(io.contains("ProfileTransfer.Kind.SHARE to R.string.kachi_profile_export_share"))
        assertTrue(io.contains("deps.onExportProfile(kind)"))
        val exp = SourceRoots.body(panels, "onExportProfile = {")
        assertTrue(exp.contains("kind ->") && exp.contains("onExportProfileData(kind)") && exp.contains("ProfileIoStore.write(activity, activeProfileName(), kind, data)"))
        assertTrue(Regex("""onExportProfileData\s*=\s*\{\s*kind\s*->\s*viewModel\.exportActiveProfile\(kind\)\s*}""").containsMatchIn(wiring))
        assertTrue(vm.contains("repository.exportActiveProfile(kind)"))
        assertTrue(repo.contains("prefs.exportProfile(prefs.activeProfile(), kind)"))
        val write = SourceRoots.body(store, "fun write(ctx: Context, profileName: String, kind: ProfileTransfer.Kind, data: String): String?")
        assertTrue(write.contains("ProfileFiles.writeNew(d, profileName, stamp, kind, data)"), "ghi qua writeNew — không bao giờ ghi đè")
    }

    // ── (4) Xuất hồ sơ không-đang-dùng không chụp tệp sống ──────────────────────────────────────

    @Test
    fun `exportProfile khong tu chup tep song, quyet dinh o ProfileTransfer`() {
        val exp = SourceRoots.body(profileIo, "internal fun WorkspacePrefs.exportProfile(profile: String, kind: ProfileTransfer.Kind): String")
        assertTrue(exp.contains("ProfileTransfer.export(transferSource(), profile, activeProfile(), kind)"))
        assertFalse(exp.contains("snapshotClusterNav"), "chụp tệp sống vào ảnh của hồ sơ KHÔNG đang dùng = ghi đè ảnh của nó")
        val src = SourceRoots.body(profileIo, "private fun WorkspacePrefs.transferSource(): ProfileTransfer.Source")
        assertTrue(src.contains("override fun snapshotLive(profile: String) = prefs.snapshotClusterNav(profile)"))
        // Mọi chỗ gọi snapshotClusterNav trong tệp này: nhân bản (hồ sơ đang dùng), di trú cảnh (hồ sơ đang dùng), cửa Source.
        val calls = Regex("""snapshotClusterNav\((\w+)\)""").findAll(profileIo).map { it.groupValues[1] }.toList()
        assertEquals(listOf("source", "active", "profile"), calls, "chỗ chụp mới ⇒ soát lại xem nó có chụp nhầm hồ sơ không")
    }

    @Test
    fun `nhap di qua ke hoach thuan va null khong ghi log gia`() {
        val imp = SourceRoots.body(profileIo, "internal fun WorkspacePrefs.importProfile(data: String, name: String? = null): ProfileImportReport?")
        assertTrue(imp.contains("ProfileTransfer.planImport(data, name, profiles())"))
        assertTrue(imp.contains("keyOf(plan.target, suffix)"), "chỉ ghi khoá của hồ sơ MỚI")
        val clean = SourceRoots.body(snapshot, "internal fun cleanImportedSnapshot(suffix: String, value: Any?): Any?")
        val nullGate = clean.indexOf("if (value == null) return null")
        assertTrue(nullGate in 0 until clean.indexOf("Log.w("), "hậu tố vắng (null) phải trả null TRƯỚC nhánh ghi log")
    }
}
