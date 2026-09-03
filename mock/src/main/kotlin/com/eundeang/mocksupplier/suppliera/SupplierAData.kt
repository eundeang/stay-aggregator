package com.eundeang.mocksupplier.suppliera

object SupplierAData {
    const val CURRENCY = "KRW"

    data class Rate(
        val nightlyRate: Int,
        val taxAmount: Int,
        val remainingRoomsCycle: List<Int>, // 날짜마다 순환 적용 — 재고가 날짜별로 달라야 함(0 포함)
        val breakfastIncluded: Boolean,
    )

    val hotels = listOf(
        AHotel(
            hotelCode = "A-10023",
            hotelName = "Riverside Hotel Seoul",
            roomTypes = listOf(ARoomType("DLX-TWN", "Deluxe Twin", 2)),
        ),
        AHotel(
            hotelCode = "A-20045",
            hotelName = "Namsan Garden Stay",
            roomTypes = listOf(ARoomType("STD-DBL", "Standard Double", 2)),
        ),
    )

    val rates = mapOf(
        "DLX-TWN" to Rate(nightlyRate = 150_000, taxAmount = 15_000, remainingRoomsCycle = listOf(3, 1, 5), breakfastIncluded = false),
        "STD-DBL" to Rate(nightlyRate = 90_000, taxAmount = 9_000, remainingRoomsCycle = listOf(0, 2, 4), breakfastIncluded = true),
    )
}
