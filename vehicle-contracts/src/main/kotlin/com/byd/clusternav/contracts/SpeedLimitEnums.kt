package com.byd.clusternav.contracts

/** Semantic sign shape; numeric vehicle encodings remain profile-specific and unproven. */
enum class SpeedSignType {
    REGULATORY,
    ADVISORY,
    VARIABLE,
    UNKNOWN,
}

/** Semantic applicability; numeric vehicle encodings remain outside this neutral contract. */
enum class SpeedLimitType {
    ABSOLUTE,
    CONDITIONAL,
    TEMPORARY,
    UNKNOWN,
}

enum class SpeedUnit {
    KPH,
    MPH,
}

enum class SpeedLimitSource {
    /**
     * Widget VietMap — nguồn biển báo tốc độ DUY NHẤT có thật.
     *
     * 2026-08-22: gỡ hằng `WAZE`. Nó đọc tag logcat `WazeHudLink` của WazeMod, mà WazeMod chỉ phát tag đó
     * khi có peer HUD BT/BLE; đo trên máy không có HUD (Waze ĐANG dẫn) được **0 dòng**. Không producer nào
     * sinh ra nó nữa nên để lại chỉ gây hiểu nhầm là "Waze đọc được tốc độ".
     */
    VIETMAP,

    /** Chưa chọn nguồn nào. [SpeedSignLifecycleCoordinator] từ chối nhận frame mang giá trị này. */
    NONE,
}

enum class FreshnessState {
    FRESH,
    STALE,
    UNAVAILABLE,
}

enum class SpeedLimitClearReason {
    ZERO_VALUE,
    TTL_EXPIRED,
    SOURCE_SWITCHED,
    PROVIDER_DISCONNECTED,
    SOURCE_STOPPED,
    MASTER_DISABLED,
    OUTPUT_DISABLED,
    PROCESS_RESTARTED,
    QUEUE_SATURATED,
}
