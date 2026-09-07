package com.eundeang.aggregator.domain

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

/** 세전(net) 단가. totalAmount(세후)와 성격이 다름을 필드명으로 명시. */
@Schema(description = "일자별 단가(세전)")
data class NightlyRate(
    @Schema(description = "숙박일")
    val date: LocalDate,
    @Schema(description = "해당 일자의 세전(net) 단가")
    val nightlyNetAmount: Long,
)
