package com.eundeang.aggregator.domain

/**
 * 숙박 전체 금액 = 각 날짜의 (nightlyRate+taxAmount) 합산.
 * 근거: docs/supplier-api-spec.md "요금 규약"
 * dailyRates: List<Pair<nightlyRate, taxAmount>>
 */
fun calculateTotalAmount(dailyRates: List<Pair<Long, Long>>): Long =
    dailyRates.sumOf { (nightlyRate, taxAmount) -> nightlyRate + taxAmount }
