package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ 3.1 · R26 — freeform sống ⇒ màn cụm về kích vật lý (khôi phục luật V1 đã mất ở V2) ═══════════════════════════════════
 *
 * ClusterNav V1 (`ClusterCast.applyBounds` tầng 1, git `93709693`): `am task resize` ăn ⇒ `CastShell.resetDisplayGeometry(sh, vd)` —
 * *"task tự quản khung → gỡ ép hình học, GIỮ DPI của user"*. V2 (`CastGeometryController`) giữ resize nhưng mất lượt gỡ, lại thêm
 * đường lùi `wm size` lưu THEO APP ⇒ một lần lùi là `wmSize` lạ (vd 1920×520) bị `DisplayConfigurator.apply` áp lại ở mọi lần chiếu
 * app đó. [ĐO xe 13/10 `docs/diagnostics/oncar-2026-10-13/`]: Waze kẹt trong `parent=[0,0][1920,520]`; gỡ size + overscan ⇒ 720.
 *
 * Hàm mở rộng cùng package (khuôn `CastSessionPin.kt`) vì `SimpleCastCoordinator.kt` sát trần 500 dòng. Không chứa chuỗi lệnh
 * shell: lệnh ở [DisplayConfigurator.releaseGeometry]; đọc ở [CastGeometryController.displayFacts].
 */

/**
 * Gỡ ép hình học (size + overscan, giữ DPI) trên màn cụm đã XÁC MINH sống. Gọi bởi [CastGeometryController] sau mỗi `am task
 * resize` ăn, và bởi [applySessionPin] khi màn cụm đo được là freeform. Phiên CP/AA ⇒ 0 lệnh (chúng sống nhờ `wm size`/overscan đã
 * đo — `DisplayConfig.CARPLAY`/`ANDROID_AUTO`); display chưa xác minh ⇒ 0 lệnh (CLAUDE.md §4).
 */
internal fun SimpleCastCoordinator.releaseDisplayGeometry() {
    val s = state
    if (s is SimpleCastState.CastingFull && s.appType.isProtected) return
    val vd = liveDisplayId
    if (vd < 1) { log("R26: bỏ gỡ ép hình học — display cụm chưa xác minh live ($vd)"); return }
    configurator.releaseGeometry(vd)
}

/**
 * Cấu hình MÀN của một app THƯỜNG sắp áp ([DisplayConfigurator.apply]) — làm sạch `wmSize` cũ khi màn cụm freeform
 * ([ClusterDisplayFacts.sanitize]). Đọc sự thật màn (2 lệnh đọc) một lần mỗi (phiên × display).
 */
internal fun SimpleCastCoordinator.sanitizedForFreeform(config: DisplayConfig): DisplayConfig {
    val clean = ClusterDisplayFacts.sanitize(config, geometry.displayFacts())
    if (clean.wmSize != config.wmSize) log("R26: bỏ wmSize ${config.wmSize} đã lưu (màn cụm freeform) ⇒ ${clean.wmSize}")
    return clean
}

/** Màn cụm của phiên đo được là freeform (`null` = chưa đọc — CHỈ ĐỌC RAM, an toàn cho luồng vẽ). */
val SimpleCastCoordinator.clusterFreeform: Boolean?
    get() = geometry.knownFacts?.freeform

/** Kích VẬT LÝ của màn cụm (đã đo trong phiên; `null` = chưa đo — CHỈ ĐỌC RAM) — trần dải −/+ ở `:app`. */
val SimpleCastCoordinator.clusterPhysicalSize: Pair<Int, Int>?
    get() = geometry.knownFacts?.physical
