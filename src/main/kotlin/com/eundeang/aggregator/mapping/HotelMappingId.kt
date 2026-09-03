package com.eundeang.aggregator.mapping

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.io.Serializable

@Embeddable
data class HotelMappingId(
    @Enumerated(EnumType.STRING)
    @Column(name = "supplier", length = 20)
    val supplier: SupplierCode,

    @Column(name = "external_hotel_code", length = 100)
    val externalHotelCode: String,
) : Serializable
