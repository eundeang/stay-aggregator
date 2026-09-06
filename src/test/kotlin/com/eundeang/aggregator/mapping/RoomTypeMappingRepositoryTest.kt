package com.eundeang.aggregator.mapping

import com.eundeang.aggregator.domain.SupplierCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * `room_type_mapping`이 `hotel_mapping_id` 단일 FK로 숙소를 참조하고, 그 조합에
 * UNIQUE 제약이 걸려 있음을 검증. 근거: docs/architecture.md "매핑 테이블" —
 * 객실 타입 코드는 숙소 안에서만 유일하다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoomTypeMappingRepositoryTest
    @Autowired
    constructor(
        private val hotelMappingRepository: HotelMappingRepository,
        private val roomTypeMappingRepository: RoomTypeMappingRepository,
        dataSource: DataSource,
    ) {
        private val jdbcTemplate = JdbcTemplate(dataSource)

        @Test
        fun `저장하면 room_type_mapping의 hotel_mapping_id 컬럼에 실제 hotelMapping id가 채워진다`() {
            val hotelMapping =
                hotelMappingRepository.save(HotelMapping(SupplierCode.SUPPLIER_A, "A-10023", "Riverside Hotel Seoul"))
            roomTypeMappingRepository.save(RoomTypeMapping(hotelMapping, "DLX-TWN", "Deluxe Twin", 2))

            val rawHotelMappingId =
                jdbcTemplate.queryForObject(
                    "SELECT hotel_mapping_id FROM room_type_mapping WHERE external_room_type_code = ?",
                    Long::class.java,
                    "DLX-TWN",
                )

            assertEquals(hotelMapping.id, rawHotelMappingId)
        }

        @Test
        fun `같은 숙소 안에서 동일 external_room_type_code를 두 번 저장하면 제약 위반이 발생한다`() {
            val hotelMapping =
                hotelMappingRepository.save(HotelMapping(SupplierCode.SUPPLIER_A, "A-10023", "Riverside Hotel Seoul"))
            roomTypeMappingRepository.saveAndFlush(RoomTypeMapping(hotelMapping, "DLX-TWN", "Deluxe Twin", 2))

            assertThrows(DataIntegrityViolationException::class.java) {
                roomTypeMappingRepository.saveAndFlush(RoomTypeMapping(hotelMapping, "DLX-TWN", "Deluxe Twin (dup)", 2))
            }
        }

        @Test
        fun `다른 숙소라면 동일 external_room_type_code를 각각 저장할 수 있다`() {
            val hotelA =
                hotelMappingRepository.save(HotelMapping(SupplierCode.SUPPLIER_A, "A-10023", "Riverside Hotel Seoul"))
            val hotelB =
                hotelMappingRepository.save(HotelMapping(SupplierCode.SUPPLIER_A, "A-20045", "Namsan Garden Stay"))

            roomTypeMappingRepository.saveAndFlush(RoomTypeMapping(hotelA, "STD", "Standard", 2))
            roomTypeMappingRepository.saveAndFlush(RoomTypeMapping(hotelB, "STD", "Standard Double", 2))

            assertEquals(2, roomTypeMappingRepository.findAll().size)
        }
    }
