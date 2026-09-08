package com.byd.clusternav.contracts

data class MonotonicFreshness(
    val observedAtMonotonicMs: Long,
    val validUntilMonotonicMs: Long,
    val state: FreshnessState,
) {
    init {
        require(observedAtMonotonicMs >= 0) { "observedAtMonotonicMs must be non-negative" }
        require(validUntilMonotonicMs >= observedAtMonotonicMs) {
            "validUntilMonotonicMs must not precede observation"
        }
    }

    fun isFreshAt(monotonicNowMs: Long): Boolean =
        state == FreshnessState.FRESH &&
            monotonicNowMs >= observedAtMonotonicMs &&
            monotonicNowMs < validUntilMonotonicMs
}

data class SpeedLimitFrame(
    val value: Int?,
    val signType: SpeedSignType?,
    val limitType: SpeedLimitType?,
    val unit: SpeedUnit,
    val source: SpeedLimitSource,
    val sequence: Long,
    val freshness: MonotonicFreshness,
    val clearReason: SpeedLimitClearReason?,
) {
    init {
        require(sequence >= 0) { "sequence must be non-negative" }
        if (value == null) {
            require(clearReason != null) { "a clear frame requires a clear reason" }
            require(signType == null && limitType == null) {
                "a clear frame cannot retain sign or limit type"
            }
        } else {
            require(value in 1..300) { "active speed limit must be in 1..300" }
            require(source != SpeedLimitSource.NONE) { "an active frame requires a source" }
            require(clearReason == null) { "an active frame cannot carry a clear reason" }
            require(freshness.state == FreshnessState.FRESH) { "an active frame must be fresh" }
        }
    }

    fun isStrictSuccessorOf(previous: SpeedLimitFrame): Boolean =
        sequence > previous.sequence &&
            freshness.observedAtMonotonicMs >= previous.freshness.observedAtMonotonicMs

    companion object {
        fun active(
            value: Int,
            signType: SpeedSignType?,
            limitType: SpeedLimitType?,
            unit: SpeedUnit,
            source: SpeedLimitSource,
            sequence: Long,
            observedAtMonotonicMs: Long,
            validUntilMonotonicMs: Long,
        ): SpeedLimitFrame = SpeedLimitFrame(
            value = value,
            signType = signType,
            limitType = limitType,
            unit = unit,
            source = source,
            sequence = sequence,
            freshness = MonotonicFreshness(
                observedAtMonotonicMs = observedAtMonotonicMs,
                validUntilMonotonicMs = validUntilMonotonicMs,
                state = FreshnessState.FRESH,
            ),
            clearReason = null,
        )

        fun clear(
            unit: SpeedUnit,
            source: SpeedLimitSource,
            sequence: Long,
            observedAtMonotonicMs: Long,
            reason: SpeedLimitClearReason,
            state: FreshnessState = FreshnessState.UNAVAILABLE,
        ): SpeedLimitFrame = SpeedLimitFrame(
            value = null,
            signType = null,
            limitType = null,
            unit = unit,
            source = source,
            sequence = sequence,
            freshness = MonotonicFreshness(
                observedAtMonotonicMs = observedAtMonotonicMs,
                validUntilMonotonicMs = observedAtMonotonicMs,
                state = state,
            ),
            clearReason = reason,
        )
    }
}
