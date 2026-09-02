package com.eundeang.aggregator.mapping

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "hotel_mapping",
    uniqueConstraints = [UniqueConstraint(columnNames = ["supplier", "external_hotel_code"])],
)
class HotelMapping(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val supplier: SupplierCode,

    @Column(name = "external_hotel_code", nullable = false, length = 100)
    val externalHotelCode: String,

    @Column(name = "hotel_name", nullable = false)
    var hotelName: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
