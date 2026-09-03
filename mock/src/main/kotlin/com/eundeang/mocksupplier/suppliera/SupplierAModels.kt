package com.eundeang.mocksupplier.suppliera

data class ARoomType(val roomTypeCode: String, val roomTypeName: String, val maxOccupancy: Int)

data class AHotel(val hotelCode: String, val hotelName: String, val roomTypes: List<ARoomType>)

data class AHotelsResponse(val items: List<AHotel>)

data class ADailyRate(val date: String, val remainingRooms: Int, val nightlyRate: Int, val taxAmount: Int)

data class AAvailabilityItem(
    val hotelCode: String,
    val hotelName: String,
    val roomTypeCode: String,
    val roomTypeName: String,
    val maxOccupancy: Int,
    val breakfastIncluded: Boolean,
    val currency: String,
    val dailyRates: List<ADailyRate>,
)

data class AAvailabilityResponse(val items: List<AAvailabilityItem>)

data class AErrorResponse(val error: String, val message: String)
