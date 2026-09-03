package com.eundeang.aggregator.domain

/**
 * N박 전체를 예약 가능한 객실 수 = 기간 내 일별 remainingRooms의 최솟값.
 * 근거: docs/domain-model.md "재고 / 예약 가능 객실 수"
 */
fun calculateAvailableRooms(dailyRemainingRooms: List<DailyInventory>): Int {
    require(dailyRemainingRooms.isNotEmpty()) { "dailyRemainingRooms must not be empty" }
    return dailyRemainingRooms.minOf { it.remainingRooms }
}
