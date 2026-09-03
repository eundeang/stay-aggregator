package com.eundeang.aggregator.mapping

import com.eundeang.aggregator.domain.SupplierCode
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable

@Entity
@Table(name = "hotel_mapping")
class HotelMapping(
    @EmbeddedId
    val id: Id,
    @Column(name = "hotel_name", nullable = false)
    var hotelName: String,
) {
    @Embeddable
    data class Id(
        @Enumerated(EnumType.STRING)
        @Column(name = "supplier", length = 20)
        val supplier: SupplierCode,
        @Column(name = "external_hotel_code", length = 100)
        val externalHotelCode: String,
    ) : Serializable
}
