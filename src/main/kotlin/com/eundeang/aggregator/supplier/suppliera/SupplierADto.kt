package com.eundeang.aggregator.supplier.suppliera

// A 전용 DTO. 이 파일 밖(SupplierAClient.kt 제외)으로 나가지 않는다.
// 과제 안내 문서(부록 A) "Supplier A" 섹션의 응답 구조를 그대로 반영 (flat — 숙소 x 객실타입 조합당 1행).

internal data class SupplierAHotelsResponseDto(
    val items: List<SupplierAHotelDto>,
)

internal data class SupplierAHotelDto(
    val hotelCode: String,
    val hotelName: String,
    val roomTypes: List<SupplierARoomTypeDto>,
)

internal data class SupplierARoomTypeDto(
    val roomTypeCode: String,
    val roomTypeName: String,
    val maxOccupancy: Int,
)

internal data class SupplierAAvailabilityResponseDto(
    val items: List<SupplierAAvailabilityItemDto>,
)

internal data class SupplierAAvailabilityItemDto(
    val hotelCode: String,
    val hotelName: String,
    val roomTypeCode: String,
    val roomTypeName: String,
    val maxOccupancy: Int,
    val breakfastIncluded: Boolean, // dailyRates 안이 아니라 item 최상위(객실 타입당 1개 값)
    val currency: String,
    val dailyRates: List<SupplierADailyRateDto>,
)

internal data class SupplierADailyRateDto(
    val date: String,
    val remainingRooms: Int,
    val nightlyRate: Long,
    val taxAmount: Long,
)

internal data class SupplierAErrorResponseDto(
    val error: String,
    val message: String,
)
