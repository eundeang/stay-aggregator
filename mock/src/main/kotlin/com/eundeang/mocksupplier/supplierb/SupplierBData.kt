package com.eundeang.mocksupplier.supplierb

object SupplierBData {
    const val CURRENCY = "KRW"

    data class Rate(
        val pricePerNight: Int,
        val remainingRoomsCycle: List<Int>, // 날짜마다 순환 적용 — 재고가 날짜별로 달라야 함
        val breakfastIncluded: Boolean,
    )

    val properties = listOf(
        BProperty(
            propertyId = "B77120",
            propertyName = "Riverside Hotel Seoul",
            rooms = listOf(BRoom("R-401", "Deluxe Twin Room", 2)),
        ),
    )

    val rates = mapOf(
        "R-401" to Rate(pricePerNight = 165_000, remainingRoomsCycle = listOf(2, 1, 3), breakfastIncluded = true),
    )
}
