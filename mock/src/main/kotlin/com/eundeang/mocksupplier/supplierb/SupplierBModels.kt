package com.eundeang.aggregator.mock.supplierb

data class BRoom(val roomId: String, val roomName: String, val maxOccupancy: Int)

data class BProperty(val propertyId: String, val propertyName: String, val rooms: List<BRoom>)

data class BPropertiesData(val items: List<BProperty>)

data class BPropertiesResponse(val resultCode: String, val resultMessage: String, val data: BPropertiesData?)

data class BInventoryDay(val date: String, val remainingRooms: Int)

data class BSearchItem(
    val propertyId: String,
    val roomId: String,
    val totalPrice: Int,
    val taxIncluded: Boolean,
    val inventory: List<BInventoryDay>,
)

data class BSearchData(val items: List<BSearchItem>)

data class BSearchResponse(val resultCode: String, val resultMessage: String, val data: BSearchData?)
