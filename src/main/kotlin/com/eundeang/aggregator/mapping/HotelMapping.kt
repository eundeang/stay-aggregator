package com.eundeang.aggregator.mapping

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "hotel_mapping")
class HotelMapping(
    @EmbeddedId
    val id: HotelMappingId,

    @Column(name = "hotel_name", nullable = false)
    var hotelName: String,
)
