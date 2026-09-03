package com.eundeang.aggregator.mock.supplierb

object SupplierBData {
    data class Rate(val pricePerNight: Int, val remainingRooms: Int)

    val properties = listOf(
        BProperty(
            propertyId = "B77120",
            propertyName = "Riverside Hotel Seoul",
            rooms = listOf(BRoom("R-401", "Deluxe Twin", 2)),
        ),
    )

    val rates = mapOf(
        "R-401" to Rate(pricePerNight = 165_000, remainingRooms = 2),
    )
}
