package com.eundeang.aggregator.mapping

import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierHotel
import com.eundeang.aggregator.domain.SupplierRoomType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * 신규/기존 판정을 위한 SELECT 없이 `INSERT ... ON DUPLICATE KEY UPDATE` 멀티로우로
 * upsert. 유니크 제약(hotel_mapping PK, room_type_mapping UNIQUE)이 신규/기존 판정을
 * DB에 위임할 수 있게 해준다.
 *
 * 근거(docs/architecture.md "매핑 배치 upsert" 참고): 레코드당 1회 왕복이던 방식을
 * 청크당 1회 왕복으로 줄임 — 5만 건 기준 청크 1000이면 약 50회.
 */
@Repository
class MappingBatchUpsertRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun upsertHotels(
        supplier: SupplierCode,
        hotels: List<SupplierHotel>,
        chunkSize: Int = 1000,
    ) {
        hotels.chunked(chunkSize).forEach { chunk ->
            val sql =
                """
                INSERT INTO hotel_mapping (supplier, external_hotel_code, hotel_name)
                VALUES ${chunk.joinToString(",") { "(?, ?, ?)" }}
                ON DUPLICATE KEY UPDATE hotel_name = VALUES(hotel_name)
                """.trimIndent()
            val args: List<Any> = chunk.flatMap { listOf(supplier.name, it.externalHotelCode, it.hotelName) }
            jdbcTemplate.update(sql, *args.toTypedArray())
        }
    }

    fun upsertRoomTypes(
        supplier: SupplierCode,
        roomTypes: List<Pair<String, SupplierRoomType>>,
        chunkSize: Int = 1000,
    ) {
        roomTypes.chunked(chunkSize).forEach { chunk ->
            val sql =
                """
                INSERT INTO room_type_mapping
                    (supplier, external_hotel_code, external_room_type_code, room_type_name, max_occupancy)
                VALUES ${chunk.joinToString(",") { "(?, ?, ?, ?, ?)" }}
                ON DUPLICATE KEY UPDATE
                    room_type_name = VALUES(room_type_name),
                    max_occupancy = VALUES(max_occupancy)
                """.trimIndent()
            val args: List<Any> =
                chunk.flatMap { (hotelCode, roomType) ->
                    listOf(supplier.name, hotelCode, roomType.externalRoomTypeCode, roomType.roomTypeName, roomType.maxOccupancy)
                }
            jdbcTemplate.update(sql, *args.toTypedArray())
        }
    }
}
