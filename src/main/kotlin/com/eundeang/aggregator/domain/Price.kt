package com.eundeang.aggregator.domain

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 근거: docs/domain-model.md "요금" — 총액(세금 포함)은 필수, 일자별 단가는
 * 원본에 없으면(B) null. taxIncluded는 구현 범위에서 항상 true.
 */
@Schema(description = "요금 정보")
data class Price(
    @Schema(description = "통화 코드", example = "KRW")
    val currency: String,
    @Schema(description = "숙박 전체 총액 (항상 세금 포함/gross)")
    val totalAmount: Long,
    @Schema(description = "총액에 세금이 포함됐는지 여부 — 구현 범위에서 항상 true")
    val taxIncluded: Boolean = true,
    @Schema(description = "일자별 단가(세전/net) — 공급사 원본에 없으면 null")
    val nightlyRates: List<NightlyRate>? = null,
)
