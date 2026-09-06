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
 *
 * `room_type_mapping.hotel_mapping_id`(surrogate FK)를 채워야 해서, 청크 하나를
 * upsert하는 흐름은 이제 [MappingSyncService]가 "hotel upsert → id 조회(청크당 1회
 * bulk SELECT) → room type upsert" 순서로 조립한다. 이 클래스는 각 단계의 SQL 실행만
 * 담당하고 청크 단위 조립 책임은 갖지 않는다.
 */
@Repository
class MappingBatchUpsertRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun upsertHotels(
        supplier: SupplierCode,
        hotels: List<SupplierHotel>,
    ) {
        if (hotels.isEmpty()) return
        val sql =
            """
            INSERT INTO hotel_mapping (supplier, external_hotel_code, hotel_name)
            VALUES ${hotels.joinToString(",") { "(?, ?, ?)" }}
            ON DUPLICATE KEY UPDATE hotel_name = VALUES(hotel_name)
            """.trimIndent()
        val args: List<Any> = hotels.flatMap { listOf(supplier.name, it.externalHotelCode, it.hotelName) }
        jdbcTemplate.update(sql, *args.toTypedArray())
    }

    /** upsertHotels 직후, 같은 청크의 (supplier, externalHotelCode) → 실제 발급된 id를 1회 bulk SELECT로 조회. */
    fun findHotelIdsByExternalCodes(
        supplier: SupplierCode,
        externalHotelCodes: List<String>,
    ): Map<String, Long> {
        if (externalHotelCodes.isEmpty()) return emptyMap()
        val placeholders = externalHotelCodes.joinToString(",") { "?" }
        val sql =
            "SELECT external_hotel_code, id FROM hotel_mapping WHERE supplier = ? AND external_hotel_code IN ($placeholders)"
        val args: List<Any> = listOf(supplier.name) + externalHotelCodes
        return jdbcTemplate
            .query(sql, { rs, _ -> rs.getString("external_hotel_code") to rs.getLong("id") }, *args.toTypedArray())
            .toMap()
    }

    fun upsertRoomTypes(roomTypes: List<Pair<Long, SupplierRoomType>>) {
        if (roomTypes.isEmpty()) return
        val sql =
            """
            INSERT INTO room_type_mapping
                (hotel_mapping_id, external_room_type_code, room_type_name, max_occupancy)
            VALUES ${roomTypes.joinToString(",") { "(?, ?, ?, ?)" }}
            ON DUPLICATE KEY UPDATE
                room_type_name = VALUES(room_type_name),
                max_occupancy = VALUES(max_occupancy)
            """.trimIndent()
        val args: List<Any> =
            roomTypes.flatMap { (hotelMappingId, roomType) ->
                listOf(hotelMappingId, roomType.externalRoomTypeCode, roomType.roomTypeName, roomType.maxOccupancy)
            }
        jdbcTemplate.update(sql, *args.toTypedArray())
    }
}
