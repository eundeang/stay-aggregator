package com.eundeang.aggregator.mock.suppliera

object SupplierAData {
    data class Rate(val nightlyRate: Int, val taxAmount: Int, val remainingRooms: Int)

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
        "DLX-TWN" to Rate(nightlyRate = 150_000, taxAmount = 15_000, remainingRooms = 3),
        "STD-DBL" to Rate(nightlyRate = 90_000, taxAmount = 9_000, remainingRooms = 5),
    )
}
