package com.byd.clusternav.modules.clustercast.simplified

/**
 * NGUỒN DUY NHẤT phân loại app "chiếu điện thoại" (CarPlay / Android Auto / projection) + launcher/home + gói
 * system bỏ qua khi parse stack cụm (quality-review 2026-09-15, Pha 3c / §7 · DRY).
 *
 * Trước: cùng khái niệm này khai HARDCODE ở 4 chỗ rời (ClusterCast.PROJECTION_HINTS — đã xoá · AppMover
 * CARPLAY/ANDROID_AUTO_PACKAGES · CastAppCatalog.PROJECTION_HINTS · CastStackParser.skipExactPkgs) ⇒ gặp gói
 * mới là bốn bản lệch nhau. Gom về đây; mọi caller tham chiếu hằng/hàm ở đây. THUẦN — test off-car.
 */
object ProjectionApps {

    /** Gói CarPlay đã biết trên BYD DiLink. */
    val CARPLAY_PACKAGES = setOf(
        "com.byd.autolink.carplay",
        "com.byd.carlife.carplay",
        "com.byd.carplay.ui",
    )

    /** Gói Android Auto đã biết trên BYD DiLink. */
    val ANDROID_AUTO_PACKAGES = setOf(
        "com.byd.autolink.androidauto",
        "com.google.android.projection.gearhead",
    )

    /** Chuỗi con nhận diện "app chiếu điện thoại" trong component/pkg (gồm cả AAP video/activity). */
    val PROJECTION_HINTS = listOf("projection.sink", "aapactivity", "aapvideo", "carplay", "androidauto")

    /** Gói SYSTEM/launcher/CarPlay-UI BỎ QUA khi parse `am stack list` của cụm. */
    val STACK_SKIP_PKGS = setOf(
        "com.android.launcher3",
        "com.android.systemui",
        "com.byd.carplay.ui",
    )

    /** [hay] (component + " " + pkg, hoặc pkg) là app chiếu điện thoại? Khớp chuỗi con, không phân biệt hoa/thường. */
    fun isProjectionComponent(hay: String): Boolean {
        val s = hay.lowercase()
        return PROJECTION_HINTS.any { s.contains(it) }
    }

    fun isCarPlay(pkg: String): Boolean =
        pkg in CARPLAY_PACKAGES || pkg.contains("carplay", ignoreCase = true)

    fun isAndroidAuto(pkg: String): Boolean =
        pkg in ANDROID_AUTO_PACKAGES ||
            pkg.contains("android.auto", ignoreCase = true) ||
            pkg.contains("androidauto", ignoreCase = true)

    /**
     * Gói launcher/home KHÔNG BAO GIỜ được chiếu lên cụm (chiếu launcher kéo home BYD Dudu khỏi display 0 và
     * đóng băng màn chính — owner 2026-08). Khớp chuỗi thuần (không PackageManager) → chạy :core, test off-car;
     * tầng app UNION thêm danh sách CATEGORY_HOME runtime. Khớp: chứa "launcher"; hoặc BYD Dudu ("com.byd.dudu"/"dudu").
     */
    fun isLauncher(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("launcher") || p.startsWith("com.byd.dudu") || p.contains("dudu")
    }
}
