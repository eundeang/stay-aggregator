package com.eundeang.aggregator.mock.suppliera

data class ARoomType(val roomTypeCode: String, val roomTypeName: String, val maxOccupancy: Int)

data class AHotel(val hotelCode: String, val hotelName: String, val roomTypes: List<ARoomType>)

data class AHotelsResponse(val hotels: List<AHotel>)

data class ADailyRate(val date: String, val nightlyRate: Int, val taxAmount: Int, val remainingRooms: Int)

data class ARoomAvailability(val roomTypeCode: String, val dailyRates: List<ADailyRate>)

data class AHotelAvailability(val hotelCode: String, val roomTypes: List<ARoomAvailability>)

data class AAvailabilityResponse(val hotels: List<AHotelAvailability>)

data class AErrorResponse(val error: String, val message: String)
