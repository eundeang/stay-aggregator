package com.eundeang.aggregator.domain

/**
 * 근거: docs/domain-model.md "요금" — 총액(세금 포함)은 필수, 일자별 단가는
 * 원본에 없으면(B) null. taxIncluded는 구현 범위에서 항상 true.
 */
data class Price(
    val currency: String,
    val totalAmount: Long,
    val taxIncluded: Boolean = true,
    val nightlyRates: List<NightlyRate>? = null,
)
