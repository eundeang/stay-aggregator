package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierHotel
import com.eundeang.aggregator.mapping.HotelMapping
import com.eundeang.aggregator.mapping.HotelMappingRepository
import com.eundeang.aggregator.mapping.RoomTypeMapping
import com.eundeang.aggregator.mapping.RoomTypeMappingRepository
import org.springframework.stereotype.Service

/**
 * 공급사 숙소 목록(①)을 매핑 테이블(HotelMapping/RoomTypeMapping)과 동기화.
 * 근거: docs/architecture.md "같은 공급사 상품이 항상 같은 내부 식별자로
 * 매핑되는 것을 DB 레벨에서 보장" — 존재 여부 판정은 (supplier, externalHotelCode)
 * 복합키 기준.
 */
@Service
class MappingSyncService(
    private val hotelMappingRepository: HotelMappingRepository,
    private val roomTypeMappingRepository: RoomTypeMappingRepository,
) {
    fun syncHotels(
        supplier: SupplierCode,
        hotels: List<SupplierHotel>,
    ) {
        hotels.forEach { hotel ->
            val hotelMapping =
                HotelMapping(
                    id = HotelMapping.Id(supplier, hotel.externalHotelCode),
                    hotelName = hotel.hotelName,
                )
            hotelMappingRepository.save(hotelMapping)

            hotel.roomTypes.forEach { roomType ->
                val existing =
                    roomTypeMappingRepository.findByHotelMappingAndExternalRoomTypeCode(
                        hotelMapping,
                        roomType.externalRoomTypeCode,
                    )
                val roomTypeMapping =
                    if (existing != null) {
                        existing.roomTypeName = roomType.roomTypeName
                        existing.maxOccupancy = roomType.maxOccupancy
                        existing
                    } else {
                        RoomTypeMapping(
                            hotelMapping = hotelMapping,
                            externalRoomTypeCode = roomType.externalRoomTypeCode,
                            roomTypeName = roomType.roomTypeName,
                            maxOccupancy = roomType.maxOccupancy,
                        )
                    }
                roomTypeMappingRepository.save(roomTypeMapping)
            }
        }
    }
}
