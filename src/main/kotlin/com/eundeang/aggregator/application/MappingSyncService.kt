package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierHotel
import com.eundeang.aggregator.mapping.MappingBatchUpsertRepository
import org.springframework.stereotype.Service

/**
 * 공급사 숙소 목록(①)을 매핑 테이블(HotelMapping/RoomTypeMapping)과 동기화.
 * 근거: docs/architecture.md "같은 공급사 상품이 항상 같은 내부 식별자로
 * 매핑되는 것을 DB 레벨에서 보장" — 존재 여부 판정은 (supplier, externalHotelCode)
 * 복합키 기준.
 *
 * 실제 upsert는 [MappingBatchUpsertRepository]의 네이티브 멀티로우 upsert에 위임
 * (근거: docs/architecture.md "매핑 배치 upsert").
 */
@Service
class MappingSyncService(
    private val mappingBatchUpsertService: MappingBatchUpsertRepository,
) {
    fun syncHotels(
        supplier: SupplierCode,
        hotels: List<SupplierHotel>,
    ) {
        mappingBatchUpsertService.upsertHotels(supplier, hotels)

        val roomTypes =
            hotels.flatMap { hotel ->
                hotel.roomTypes.map { roomType -> hotel.externalHotelCode to roomType }
            }
        mappingBatchUpsertService.upsertRoomTypes(supplier, roomTypes)
    }
}
