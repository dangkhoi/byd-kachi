package com.byd.clusternav.launcher

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * IA v2 · R1 (docs/specs/kachi-settings-ia-v2.html) — **một icon, một app**.
 *
 * [ĐO 2026-09-12] Trước đợt này `MainActivity` (màn ClusterNav cũ) giữ `MAIN+LAUNCHER` còn `KachiHomeActivity`
 * chỉ có `CATEGORY_HOME` ⇒ icon "Kachi" trong ngăn kéo mở **màn cũ**, không phải launcher. Bài này khoá:
 *  1. manifest chính có **đúng một** `CATEGORY_LAUNCHER`;
 *  2. nó nằm trong khối `KachiHomeActivity`, không nằm trong khối `MainActivity`;
 *  3. `MainActivity` vẫn `exported="true"` — mọi đường gọi tường minh (RebindReceiver, UpdateRelaunch, menu bong
 *     bóng, Settings › Nâng cao) không đổi.
 *
 * Quét manifest theo khối `<activity … </activity>` chứ không đếm chuỗi cả tệp: đếm cả tệp thì một LAUNCHER lạc
 * sang activity khác vẫn qua.
 */
class SingleLauncherIconContractTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private val manifest by lazy { app("src/main/AndroidManifest.xml").toFile().readText() }

    private val LAUNCHER = "android.intent.category.LAUNCHER"

    /** Khối `<activity … </activity>` có `android:name="[name]"` (bỏ chú thích XML để không đếm ví dụ trong comment). */
    private fun activityBlock(name: String): String {
        val noComments = manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        val start = noComments.indexOf("android:name=\"$name\"")
        assertTrue(start >= 0, "manifest phải khai activity $name")
        val open = noComments.lastIndexOf("<activity", start)
        val close = noComments.indexOf("</activity>", start)
        assertTrue(open >= 0 && close > open, "khối <activity> của $name phải đóng đúng")
        return noComments.substring(open, close)
    }

    @Test
    fun `manifest chinh co dung mot LAUNCHER`() {
        val noComments = manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertEquals(1, Regex(Regex.escape(LAUNCHER)).findAll(noComments).count(), "APK chỉ có MỘT icon trong ngăn kéo")
    }

    @Test
    fun `LAUNCHER nam o KachiHomeActivity - la icon duy nhat cua APK`() {
        val home = activityBlock(".launcher.KachiHomeActivity")
        assertTrue(home.contains(LAUNCHER), "KachiHomeActivity phải mang CATEGORY_LAUNCHER")
        assertTrue(home.contains("android.intent.category.HOME"), "và vẫn là HOME")
    }

    /**
     * Màn ClusterNav cũ: IA v2 (09-12) chỉ gỡ `CATEGORY_LAUNCHER` của nó; S3 (09-13) gỡ hẳn cả activity.
     * Bài canh chiều "không mọc lại một icon thứ hai" — chi tiết vắng mặt ở [LegacyScreenAbsenceContractTest].
     */
    @Test
    fun `man ClusterNav cu khong con trong manifest`() {
        val noComments = manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(
            noComments.contains("android:name=\".MainActivity\""),
            "màn cũ đã gỡ 2026-09-13 — khai lại nó là quay về hai bề mặt cấu hình song song",
        )
    }
}
