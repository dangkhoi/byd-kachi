package com.byd.clusternav.carexec

/**
 * Catalog các step và candidate để đánh giá trên xe.
 *
 * Đây là **khai báo**, không phải thực thi: cùng một danh sách được runner dùng để chạy trên xe và được
 * test dùng để kiểm tính nhất quán off-car. Trước đây các recipe này nằm trong `thư mục scripts/vehicle`,
 * tức một bản hiện thực thứ hai song song với Kotlin — ngày 2026-07-27 đã cho thấy tác hại: hai nơi giữ
 * cùng một logic và không ai biết bản nào đúng.
 */
object CarExecCatalog {

    const val PLACEHOLDER_PACKAGE = "{pkg}"
    const val PLACEHOLDER_COMPONENT = "{comp}"
    const val PLACEHOLDER_DISPLAY = "{display}"
    const val PLACEHOLDER_TASK = "{taskId}"
    const val PLACEHOLDER_SERVICE = "{svc}"

    const val PLACEHOLDER_LEFT = "{left}"
    const val PLACEHOLDER_TOP = "{top}"
    const val PLACEHOLDER_RIGHT = "{right}"
    const val PLACEHOLDER_BOTTOM = "{bottom}"
    const val PLACEHOLDER_DPI = "{dpi}"

    /** Giá trị cần ghi, ví dụ giới hạn tốc độ km/h. */
    const val PLACEHOLDER_VALUE = "{value}"

    /** Khoá/hằng do khám phá tìm ra: tên setting, action broadcast, mã transaction. */
    const val PLACEHOLDER_KEY = "{key}"

    val placeholders = setOf(
        PLACEHOLDER_PACKAGE, PLACEHOLDER_COMPONENT, PLACEHOLDER_DISPLAY,
        PLACEHOLDER_TASK, PLACEHOLDER_SERVICE,
        PLACEHOLDER_LEFT, PLACEHOLDER_TOP, PLACEHOLDER_RIGHT, PLACEHOLDER_BOTTOM, PLACEHOLDER_DPI,
        PLACEHOLDER_VALUE, PLACEHOLDER_KEY,
    )

    val steps: List<CarStep> = buildList {
        addAll(CarExecClusterProjectionCatalog.steps)
        addAll(CarExecNavigationCatalog.steps)
        addAll(CarExecClusterLifecycleCatalog.steps)
        addAll(CarExecClusterDiagnosticsCatalog.profileSteps)
        addAll(CarExecSpeedSignCatalog.steps)
        addAll(CarExecClusterDiagnosticsCatalog.preHudSteps)
        addAll(CarExecHudCatalog.steps)
        addAll(CarExecClusterDiagnosticsCatalog.postHudSteps)
    }

    fun step(id: String): CarStep? = steps.firstOrNull { it.id == id }

    fun candidate(id: String): Pair<CarStep, StepCandidate>? = steps.firstNotNullOfOrNull { step ->
        step.candidates.firstOrNull { it.id == id }?.let { step to it }
    }
}
