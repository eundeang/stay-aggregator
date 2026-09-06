package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierHotel
import com.eundeang.aggregator.mapping.MappingBatchUpsertRepository
import org.springframework.stereotype.Service

/** 숙소 upsert와 room type upsert를 같은 청크 경계로 묶는다 — 근거: MappingBatchUpsertRepository 문서. */
private const val HOTEL_CHUNK_SIZE = 1000

/**
 * 공급사 숙소 목록(①)을 매핑 테이블(HotelMapping/RoomTypeMapping)과 동기화.
 * 근거: docs/architecture.md "같은 공급사 상품이 항상 같은 내부 식별자로
 * 매핑되는 것을 DB 레벨에서 보장" — 존재 여부 판정은 (supplier, externalHotelCode)
 * 복합키 기준.
 *
 * 실제 upsert는 [MappingBatchUpsertRepository]의 네이티브 멀티로우 upsert에 위임
 * (근거: docs/architecture.md "매핑 배치 upsert"). room_type_mapping이
 * hotel_mapping_id(surrogate FK)를 참조하므로, 청크 하나마다 "숙소 upsert →
 * 방금 upsert된 id를 bulk SELECT로 조회 → 그 id로 room type upsert" 순서로 진행한다.
 */
@Service
class MappingSyncService(
    private val mappingBatchUpsertService: MappingBatchUpsertRepository,
) {
    fun syncHotels(
        supplier: SupplierCode,
        hotels: List<SupplierHotel>,
    ) {
        hotels.chunked(HOTEL_CHUNK_SIZE).forEach { chunk ->
            mappingBatchUpsertService.upsertHotels(supplier, chunk)

            val hotelIdByExternalCode =
                mappingBatchUpsertService.findHotelIdsByExternalCodes(supplier, chunk.map { it.externalHotelCode })

            val roomTypes =
                chunk.flatMap { hotel ->
                    val hotelMappingId = hotelIdByExternalCode.getValue(hotel.externalHotelCode)
                    hotel.roomTypes.map { roomType -> hotelMappingId to roomType }
                }
            mappingBatchUpsertService.upsertRoomTypes(roomTypes)
        }
    }
}
