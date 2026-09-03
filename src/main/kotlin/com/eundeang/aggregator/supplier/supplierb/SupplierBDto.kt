package com.eundeang.aggregator.supplier.supplierb

// B 전용 DTO. 이 파일 밖(SupplierBClient.kt 제외)으로 나가지 않는다.

internal data class SupplierBPropertiesResponseDto(
    val resultCode: String,
    val resultMessage: String,
    val data: SupplierBPropertiesDataDto?,
)

internal data class SupplierBPropertiesDataDto(
    val items: List<SupplierBPropertyDto>,
)

internal data class SupplierBPropertyDto(
    val propertyId: String,
    val propertyName: String,
    val rooms: List<SupplierBRoomDto>,
)

internal data class SupplierBRoomDto(
    val roomId: String,
    val roomName: String,
    val maxOccupancy: Int,
)

internal data class SupplierBSearchResponseDto(
    val resultCode: String,
    val resultMessage: String,
    val data: SupplierBSearchDataDto?,
)

internal data class SupplierBSearchDataDto(
    val items: List<SupplierBSearchItemDto>,
)

internal data class SupplierBSearchItemDto(
    val propertyId: String,
    val propertyName: String,
    val roomId: String,
    val roomName: String,
    val maxOccupancy: Int,
    val breakfastIncluded: Boolean,
    val currency: String,
    val totalPrice: Long,
    val taxIncluded: Boolean,
    val inventory: List<SupplierBInventoryDto>,
)

internal data class SupplierBInventoryDto(
    val date: String,
    val remainingRooms: Int,
)
