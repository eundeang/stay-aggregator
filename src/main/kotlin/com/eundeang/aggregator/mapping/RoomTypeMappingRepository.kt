package com.eundeang.aggregator.mapping

import org.springframework.data.jpa.repository.JpaRepository

interface RoomTypeMappingRepository : JpaRepository<RoomTypeMapping, Long> {
    fun findByHotelMappingAndExternalRoomTypeCode(
        hotelMapping: HotelMapping,
        externalRoomTypeCode: String,
    ): RoomTypeMapping?

    fun findAllByHotelMapping(hotelMapping: HotelMapping): List<RoomTypeMapping>
}
