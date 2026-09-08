package com.byd.clusternav.navigation.screencapture

/**
 * Mô hình THUẦN (không Android) cho nguồn dẫn đường bằng screen-capture (B3 — xem
 * `docs/specs/waze-vietmap-screen-capture.html`, §4.3/§4.4). Đây là phần "khoá được off-car" theo R-nf5:
 * quyết định app đang ở đâu (4 case) + vùng cần crop, không đụng MediaProjection/VirtualDisplay/PixelCopy.
 *
 * Ba khối:
 *   - [CaptureCase]  : app dẫn đang ở 1 trong 4 vị trí hiển thị (§4.3).
 *   - [AppLocation]  : đầu vào thô (nơi app + trạng thái) mà lớp :app đọc từ `am stack`/a11y/arbiter.
 *   - [CropRect]/[CaptureBounds]/[CapturePlan] : đầu ra (vùng crop + tầng bounds dùng).
 */

/** Bốn vị trí app dẫn có thể ở (owner requirement R3, spec §4.3). */
enum class CaptureCase {
    /** Case 1: app dẫn chiếm TOÀN màn chính (Android display 0). */
    FULL_MAIN,

    /** Case 2: app dẫn ở NỬA màn chính (split/freeform trái hoặc phải trên display 0). */
    HALF_MAIN_SPLIT,

    /** Case 3: app dẫn đang chiếu (cast) sang màn CỤM (display 1). */
    CLUSTER_CAST,

    /** Case 4: nav còn tươi nhưng app KHÔNG foreground ở đâu (offscreen / không active). Khó nhất (B3.4). */
    NOT_ACTIVE,
}

/**
 * Màn hình VẬT LÝ mà một [CaptureCase] phải CHỤP (B3.58). THUẦN (off-car testable) — lớp `:app` map giá trị
 * này sang display id chụp thật của transport (MAIN → `fission_screencap -d1`; CLUSTER → `-d0`; OFFSCREEN →
 * MediaProjection scaffold), giữ chi tiết fission (đảo so với Android, proven on-car) ở đúng lớp transport.
 */
enum class CaptureDisplayTarget { MAIN, CLUSTER, OFFSCREEN }

/**
 * QUYẾT ĐỊNH THUẦN: một [CaptureCase] chụp màn nào (B3.58 — "VietMap cast lên CỤM thì HUD không có gì").
 *
 * Điểm cốt lõi cần khoá off-car: **[CaptureCase.CLUSTER_CAST] phải chụp [CaptureDisplayTarget.CLUSTER]**,
 * KHÔNG phải màn chính — nếu không, khi app dẫn được chiếu sang cụm thì transport chụp nhầm display 0 (app
 * khác đang hiện) ⇒ mũi tên/pixel dẫn đường không bao giờ tới. Việc chụp thật pixel của display phụ là
 * PHỤ THUỘC XE (emulator không host/chụp được display phụ — B3.26); ở đây chỉ khoá logic CHỌN display.
 */
fun captureDisplayForCase(case: CaptureCase): CaptureDisplayTarget = when (case) {
    CaptureCase.FULL_MAIN, CaptureCase.HALF_MAIN_SPLIT -> CaptureDisplayTarget.MAIN
    CaptureCase.CLUSTER_CAST -> CaptureDisplayTarget.CLUSTER
    CaptureCase.NOT_ACTIVE -> CaptureDisplayTarget.OFFSCREEN
}

/** Vùng cần crop trong nhận diện (arrow của Waze / icon camera của VietMap). Chọn theo package (§4.4). */
enum class CaptureTarget {
    /** Mũi tên hướng rẽ (Waze/GMaps) → nuôi [com.byd.clusternav.navigation.ManeuverSignature]. */
    ARROW,

    /** Icon camera phạt nguội (VietMap) → nuôi [VietMapCameraMatcher]. */
    CAMERA,

    ;

    companion object {
        /** §7 — dùng roster [com.byd.clusternav.navigation.NavApps], KHÔNG chép lại tên gói ở đây. */
        private val VIETMAP_PKGS = com.byd.clusternav.navigation.NavApps.VIETMAP

        /**
         * TẤT CẢ target cần thử cho [pkg] trong MỘT nhịp (B3.8). VietMap khi dẫn hiện CẢ HAI: banner mũi tên
         * lệnh-kế ở TOP-LEFT (như Waze) VÀ icon camera phạt nguội neo trên bản đồ ⇒ [ARROW, CAMERA]. Waze/
         * WazeMod/GMaps chỉ có mũi tên ⇒ [ARROW]. Thứ tự ARROW-trước cho phép lát a11y (một holder rect duy
         * nhất) ưu tiên mũi tên khi cùng frame; router tính bounds RIÊNG mỗi target (§4.4).
         *
         * ĐÂY là API đa-target mà [CaptureRouter.routePlans] + `ScreenCaptureNavSource.tick` dùng. Giữ [forPackage]
         * (đơn) RIÊNG vì đường a11y (`NavAccessibilityService` → `CaptureBoundsHeuristic.pick`) cần MỘT bộ từ
         * khoá target và các file đó thuộc agent khác — không đổi chữ ký [forPackage] để chúng vẫn biên dịch.
         */
        fun targetsForPackage(pkg: String): List<CaptureTarget> =
            if (pkg in VIETMAP_PKGS) listOf(ARROW, CAMERA) else listOf(ARROW)

        /**
         * Target ĐƠN "chính" cho [pkg] — đường chọn-node a11y (`NavAccessibilityService` → `CaptureBoundsHeuristic`)
         * cần đúng MỘT bộ từ khoá. VietMap → CAMERA (icon widget/data không phơi được); còn lại → ARROW. Giữ
         * ĐƠN (kiểu trả không đổi) để đường a11y + heuristic — do agent khác sở hữu — vẫn tương thích nguồn; định
         * tuyến ĐA-target dùng [targetsForPackage].
         */
        fun forPackage(pkg: String): CaptureTarget =
            if (pkg in VIETMAP_PKGS) CAMERA else ARROW
    }
}

/** Tầng bounds nào đã được dùng (chẩn đoán + test khẳng định thứ tự ưu tiên §4.4). */
enum class BoundsSource {
    /** Tầng 1: bounds động từ a11y (`getBoundsInScreen`), còn tươi. */
    A11Y_DYNAMIC,

    /** Tầng 2: rect cố định đã hiệu chỉnh theo (app, geometry) — fallback khi a11y không tươi. */
    FIXED_CALIBRATED,

    /** Không có bounds nào áp được (không cả a11y lẫn bảng cố định) — caller bỏ frame. */
    NONE,
}

/**
 * Hình chữ nhật crop THUẦN (thay cho `android.graphics.Rect` — :core không biết Android). Toạ độ theo
 * KHÔNG GIAN ẢNH capture (pixel tuyệt đối của display đang chụp). [right]/[bottom] là exclusive.
 */
data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    /** Rỗng/không hợp lệ (không có gì để crop) khi bề rộng hoặc cao ≤ 0. */
    fun isEmpty(): Boolean = width <= 0 || height <= 0

    /** Dời NGANG [dx] pixel (dùng cho Case-2 offset nửa phải). */
    fun offsetX(dx: Int): CropRect = copy(left = left + dx, right = right + dx)

    /**
     * Giao với [region] (clamp). Nếu không giao → trả rect rỗng (isEmpty). Dùng để loại nhiễu app nửa kia
     * (Case 2): a11y trả toạ độ tuyệt đối, clamp về nửa của app dẫn.
     */
    fun clampTo(region: CropRect): CropRect {
        val l = maxOf(left, region.left)
        val t = maxOf(top, region.top)
        val r = minOf(right, region.right)
        val b = minOf(bottom, region.bottom)
        return if (r <= l || b <= t) CropRect(l, t, l, t) else CropRect(l, t, r, b)
    }

    companion object {
        val EMPTY = CropRect(0, 0, 0, 0)
    }
}

/**
 * Kích thước + phân loại display cho router. [displayW]/[displayH] là của display app đang ở.
 * [mainDisplayId]/[clusterDisplayId] để router phân biệt màn chính vs cụm mà KHÔNG hardcode (dù mặc
 * định Android: 0 = chính, 1 = cụm).
 */
data class DisplayGeometry(
    val displayW: Int,
    val displayH: Int,
    val mainDisplayId: Int = 0,
    val clusterDisplayId: Int = 1,
    /**
     * Mật độ điểm ảnh THẬT của display (dpi), đọc từ dòng configuration của `am stack list` (vd `240dpi`).
     * 0 = chưa biết → caller dùng [DENSITY_DEFAULT]. Cần vì người dùng CHỈNH được dpi của cụm khi cast
     * (`wm density <dpi> -d <vd>` trong `CastShell`), mà kích thước glyph mũi tên tỉ lệ thẳng với dpi.
     */
    val densityDpi: Int = 0,
) {
    val fullRect: CropRect get() = CropRect(0, 0, displayW, displayH)

    /** dpi dùng để quy đổi dp→px; rơi về [DENSITY_DEFAULT] khi chưa đọc được. */
    val effectiveDensityDpi: Int get() = if (densityDpi > 0) densityDpi else DENSITY_DEFAULT

    companion object {
        /** mdpi = 160 là gốc quy đổi dp; 240 là dpi mặc định của cụm đã đo trên xe/emulator. */
        const val DENSITY_DEFAULT = 240
    }
}

/**
 * Nửa màn khi app dẫn ở chế độ split (Case 2 / cast chia đôi cụm). NAV-LOCAL: navigation SỞ HỮU bản của
 * mình để KHÔNG import ngang sang feature Cast (quy tắc Q3 — `LayeringRulesTest.navigation va cast khong
 * goi ngang nhau`). Ánh xạ 1-1 với enum `ClusterSlotSide` của feature Cast; lớp `:app` map
 * hai chiều ở BIÊN (ví dụ `CaptureLocationResolver`).
 */
enum class CaptureSlotSide { LEFT, RIGHT }

/**
 * Đầu vào thô cho [CaptureRouter]: app dẫn đang ở đâu + trạng thái. Lớp :app điền từ `am stack list`
 * (displayId/fullscreen/slot), a11y (foreground) và [com.byd.clusternav.navigation.SourceArbiter] (navFresh).
 *
 * @param pkg          package app dẫn (thường = `SourceArbiter.activeSource`).
 * @param displayId    id display Android mà task app đang nằm (0 = chính, 1 = cụm…).
 * @param isFullscreen task chiếm trọn display (khác split/freeform).
 * @param slotSide     nửa nào khi split ([CaptureSlotSide.LEFT]/[CaptureSlotSide.RIGHT]); null = không split.
 * @param leftPercent  vị trí vách chia theo % từ trái (0..100). Nửa trái = [0, W*lp/100); phải = [W*lp/100, W).
 *                     Cùng nghĩa với `AppMover.fitToCluster`.
 * @param foreground   task app đang hiển thị/foreground trên display của nó.
 * @param navFresh     SourceArbiter báo nguồn dẫn còn tươi (gate §4.2). false → router không capture.
 */
data class AppLocation(
    val pkg: String,
    val displayId: Int,
    val isFullscreen: Boolean,
    val slotSide: CaptureSlotSide? = null,
    val leftPercent: Int = 50,
    val foreground: Boolean = true,
    val navFresh: Boolean = true,
    /**
     * Ô CHỮ NHẬT THẬT app đang chiếm trên display (từ `am stack list`), KHÔNG phải cả display.
     *
     * VÌ SAO (08-22): trên cụm người dùng chỉnh được kích thước / dpi / vị trí cửa sổ cast và có thể cast
     * MỘT hoặc HAI app (`CastShell`: `wm size`, `wm density`, `am task resize`, chia đôi). Mọi vùng quan tâm
     * phải neo vào Ô NÀY thay vì vào display, nếu không sẽ trượt ngay khi người dùng đổi bố cục. Resolver đã
     * parse sẵn bounds này từ trước nhưng VỨT ĐI — chỉ giữ lại `slotSide`/`leftPercent`.
     *
     * null = không thấy task (đường a11y-hint) → caller dùng cả display.
     */
    val windowRect: CropRect? = null,
    /**
     * dpi của display app đang nằm, đọc từ dòng configuration của `am stack list` (0 = chưa biết).
     * Đi kèm [windowRect] vì cả hai đều do người dùng chỉnh khi cast, và cả hai cùng đến từ MỘT output.
     */
    val densityDpi: Int = 0,
)

/**
 * Bounds động do a11y publish (`NavAccessibilitySource`), kèm mốc thời gian để router quyết "còn tươi"
 * (§4.4 tầng 1). Toạ độ tuyệt đối trong không gian ảnh display.
 */
data class CaptureBounds(
    val rect: CropRect,
    val capturedAtMs: Long,
    /**
     * Package RUNTIME của cửa sổ mà rect được đo trong đó (§R-BI). **null = chưa gán chủ ⇒ consumer bỏ qua
     * tầng-1** (rơi về rect cố định, đường cũ đã có test) — vì rect mũi tên/camera của app A vẫn crop ra
     * pixel hợp lệ trong ảnh app B, tức có thể ra **SAI HƯỚNG**, nguy hiểm hơn cả ca làn.
     *
     * Mặc định null chỉ để [CaptureRouter] và test của nó (đo tầng bounds, không đo danh tính) không phải
     * viết lại; producer THẬT ([CaptureBoundsSource.publish]) luôn bắt buộc truyền pkg.
     */
    val pkg: String? = null,
    /**
     * **MỤC TIÊU mà rect này được ĐO CHO** ([CaptureTarget.ARROW] = node mũi tên, [CaptureTarget.CAMERA] =
     * node icon camera). null = chưa khai ⇒ [CaptureRouter.computeBounds] **BỎ QUA tầng-1** (rơi về rect
     * cố định — đường cũ đã có test).
     *
     * ⚠ VÌ SAO PHẢI CÓ (lỗi CÓ THẬT, [ĐO] 08-23 — B3.53 vòng review). Holder này là **MỘT Ô** và trước bản
     * vá nó chỉ mang `rect` + `pkg`, không mang mục tiêu; còn [CaptureRouter.computeBounds] áp snapshot đó
     * cho **MỌI** target. Với VietMap, producer a11y (`NavAccessibilityService.maybePublishCaptureBounds`)
     * chọn node bằng [CaptureTarget.forPackage] = [CaptureTarget.CAMERA] ⇒ rect trong ô là của **icon
     * camera**; nhưng [CaptureRouter.routePlans] cũng phát plan [CaptureTarget.ARROW] cho VietMap và plan đó
     * nhận **y hệt** rect camera, gắn nhãn [BoundsSource.A11Y_DYNAMIC]:
     * ```
     * PLAN target=ARROW  bounds=CropRect(1500,300,1620,420) src=A11Y_DYNAMIC   ← rect của node CAMERA
     * PLAN target=CAMERA bounds=CropRect(1500,300,1620,420) src=A11Y_DYNAMIC
     * ```
     * Crop icon camera rồi đem chấm mũi tên vẫn có mực ⇒ vẫn ra chữ ký, mà tier [BoundsSource.A11Y_DYNAMIC]
     * ở `ScreenCaptureNavSource.handleArrow` dùng khớp MỀM (`classify` = Hamming **?: NCC** 0.45) nên nó tìm
     * được một cái tên. [ĐO] crop 87 khung VietMap qua [CaptureCalibration.VIETMAP_CAMERA_SEED]: khớp mềm ra
     * mã **1** khung (`arrive_straight` → amap 12, đúng phải 9), khớp cứng ra **0**. Đây ĐÚNG cùng một lớp
     * lỗi mà B3.53 vá ở tier rect-cố-định — bản vá đó chặn một nửa cửa, nửa này là nửa còn lại.
     *
     * Gate ở [CaptureRouter.computeBounds] là `a11y.target == target`: rect đo cho CAMERA chỉ phục vụ plan
     * CAMERA, đo cho ARROW chỉ phục vụ plan ARROW. Waze/GMaps (`targetsForPackage` = [ARROW], producer khai
     * ARROW) **không đổi một nhịp nào** — CLAUDE.md §6. Rẽ theo MỤC TIÊU ĐO ĐƯỢC, không theo tên gói (§7).
     */
    val target: CaptureTarget? = null,
)

/**
 * Kết quả của [CaptureRouter.route] (đơn) / [CaptureRouter.routePlans] (đa-target, B3.8): case đã chọn + vùng
 * crop + tầng bounds dùng + target (arrow/camera). [bounds].isEmpty() ⇒ caller bỏ frame CHO TARGET ĐÓ (không
 * có vùng hợp lệ). [CaptureRouter.route] trả null / [CaptureRouter.routePlans] trả rỗng khi gate đóng (navFresh=false).
 */
data class CapturePlan(
    val case: CaptureCase,
    val target: CaptureTarget,
    val bounds: CropRect,
    val boundsSource: BoundsSource,
)
