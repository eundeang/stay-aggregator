package com.eundeang.aggregator.domain

import java.time.LocalDate

/** 세전(net) 단가. totalAmount(세후)와 성격이 다름을 필드명으로 명시. */
data class NightlyRate(
    val date: LocalDate,
    val nightlyNetAmount: Long,
)
