package com.eundeang.aggregator.mapping

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "room_type_mapping",
    uniqueConstraints = [UniqueConstraint(columnNames = ["hotel_mapping_id", "external_room_type_code"])],
)
class RoomTypeMapping(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_mapping_id", nullable = false)
    val hotelMapping: HotelMapping,

    @Column(name = "external_room_type_code", nullable = false, length = 100)
    val externalRoomTypeCode: String,

    @Column(name = "room_type_name", nullable = false)
    var roomTypeName: String,

    @Column(name = "max_occupancy", nullable = false)
    var maxOccupancy: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
