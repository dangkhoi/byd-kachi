package com.byd.clusternav.carexec

/** Tính năng của xe mà ta đang chứng minh khả thi. */
enum class CarFeature {
    CLUSTER_CAST,
    NAVIGATION,

    /**
     * Biển báo giới hạn tốc độ.
     *
     * Xe đọc biển bằng camera rồi đẩy lên cụm và HUD. Biển ở Việt Nam rối (biển phụ, biển hết hạn chế,
     * biển của làn khác, biển trong ngoặc theo giờ), camera đọc sai nên cảnh báo sai. Hướng đang đánh giá:
     * lấy giới hạn tốc độ từ VietMap rồi đưa vào đúng đường mà cụm/HUD đang nghe, và có thể tắt đường đọc
     * camera để hai nguồn không tranh nhau.
     *
     * Chưa biết gì về ba mắt xích: ai sinh giá trị, nó đi qua đường nào, ai vẽ nó. Vì thế các step của
     * tính năng này phần lớn là KHÁM PHÁ, không phải thao tác.
     */
    SPEED_SIGN,

    /**
     * Kính lái (HUD thật) — công tắc `SET_HUD_SWITCH_SET`, không phải làn cụm.
     *
     * RE 2026-07-29 (carsettings-apk = com.byd.vehiclesettings đã decompile) chứng minh được TOÀN BỘ
     * chuỗi gọi từ UI xuống tới ranh giới stub: HudSwitch UI -> HudSwitchModel -> HalSetter.set(class,id,val)
     * -> BYDAutoSettingDevice.getInstance(ctx).set(int[]{id}, EventValue) — CÙNG hình dạng lệnh gọi mà
     * BydHal.kt đã dùng cho INSTRUMENT/SETTING, và CÙNG class BYDAutoSettingDevice. Feature-id thật
     * (0x4C10E023 SET_HUD_SWITCH_SET, 0x38B00015 SET_HUD_CONFIG, 0x38B0001C ...FEEDBACK) đọc được từ
     * firmware thật (`firmware/fw-2602-diff/jadx-l3-new`), không phải từ file stub (mọi field trong
     * `BYDAutoFeatureIds.java` decompile đều = 0).
     *
     * Xe test CÓ kính lái vật lý — chủ xe xác nhận trực tiếp 2026-07-29 (không phải suy đoán, không cần
     * đo lại việc có/không). Vẫn CHƯA đo được: SET_HUD_CONFIG trả 1 (W-mode) hay 2 (AR-mode), và
     * ClusterNav's process có được HAL cấp quyền set() hay không (permission gate nằm trong chính stub,
     * không thấy được qua decompile). Bước hud-probe dưới đây vẫn nên chạy trước mọi setraw ghi — không
     * phải để hỏi "có hay không" nữa, mà để biết W-mode/AR-mode và xác nhận quyền set() có thật.
     */
    HUD_SWITCH,
}

/**
 * Mức rủi ro khi chạy một candidate. Có mặt vì không phải lệnh nào cũng chỉ đọc, và một số lệnh có thể
 * làm treo head unit trong lúc xe đang chạy — người bấm phải được cảnh báo TRƯỚC khi gửi, không phải sau.
 */
enum class CandidateRisk {
    /** Chỉ đọc. Không đổi gì. */
    READ_ONLY,

    /** Đổi trạng thái nhưng phục hồi được bằng một lệnh đã biết. */
    REVERSIBLE,

    /** Có thể làm gián đoạn thứ tài xế đang dùng: cụm, HUD, ADAS. Chỉ chạy khi đã dặn trước. */
    MAY_DISRUPT_DRIVER,

    /** Có bằng chứng hoặc lý do vững rằng có thể treo/khởi động lại hệ thống. Chỉ chạy khi đỗ. */
    MAY_HANG_SYSTEM,
}

/**
 * Ai kết luận một candidate là đạt.
 *
 * [MEASURED] — máy tự kết luận từ output đọc được, nên chạy lại được vô số lần và không cần người.
 * [HUMAN] — phải có người nhìn cụm rồi nói. Đây không phải chỗ để né việc: hiện chưa có observable nào
 * phân biệt "cụm đang hiện app" với "cụm đang hiện đồng hồ" (Q1 trong spec), nên mở/đóng chiếu buộc phải
 * do mắt người kết luận. Ledger ghi rõ nguồn verdict để sau này không ai nhầm mắt người thành số đo.
 */
enum class VerdictSource { MEASURED, HUMAN }

/**
 * Một cách làm cụ thể cho một step. Nhiều candidate cho cùng một step là chuyện bình thường: đời máy khác
 * nhau, app resizeable hay không, chiếu đang mở hay đóng — mỗi hoàn cảnh có thể cần cách khác.
 */
data class StepCandidate(
    val id: String,
    val purpose: String,
    /** Lệnh gửi vào shell của head unit, theo đúng thứ tự. Placeholder dạng {pkg}, {comp}, {display}, {taskId}, {svc}. */
    val commands: List<String>,
    /** Điều gì chứng minh candidate này đạt. Với HUMAN thì đây là câu hỏi đặt cho người nhìn. */
    val evidence: String,
    val verdictSource: VerdictSource,
    /**
     * Bắt buộc khai, KHÔNG có giá trị mặc định.
     *
     * Bản đầu tôi cho mặc định READ_ONLY và lập tức dán nhãn sai cho toàn bộ candidate cũ, kể cả những
     * candidate có `am start` và `settings put`. Mặc định ở đây là một lời nói dối im lặng: nó không làm
     * test đổ, chỉ làm người vận hành tin sai rồi gửi lệnh đổi trạng thái trong lúc đang lái.
     */
    val risk: CandidateRisk,
    /** Ghi chú field: đã chứng minh ở đâu, khi nào, trên đời máy nào. */
    val fieldNote: String? = null,
)

/** Một bước trong tính năng. Step là đơn vị được đánh cờ OK/FAIL. */
data class CarStep(
    val id: String,
    val feature: CarFeature,
    val purpose: String,
    val precondition: String,
    val candidates: List<StepCandidate>,
) {
    init {
        require(candidates.isNotEmpty()) { "step $id phải có ít nhất một candidate" }
    }
}
