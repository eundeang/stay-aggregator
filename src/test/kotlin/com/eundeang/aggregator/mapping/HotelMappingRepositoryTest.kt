package com.eundeang.aggregator.mapping

import com.eundeang.aggregator.domain.SupplierCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase

/**
 * `hotel_mapping.id`가 진짜 내부 숙소 ID(surrogate PK)임을 검증.
 * 근거: docs/domain-model.md "Kotlin 도메인 모델" 원안 — RoomTypeOffer.roomTypeId처럼
 * hotelId도 (supplier, externalHotelCode) 조합이 아니라 발급된 내부 PK여야 함.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HotelMappingRepositoryTest
    @Autowired
    constructor(
        private val hotelMappingRepository: HotelMappingRepository,
    ) {
        @Test
        fun `저장하면 supplier와 externalHotelCode를 그대로 유지하고 id가 자동 채번된다`() {
            val saved =
                hotelMappingRepository.save(
                    HotelMapping(SupplierCode.SUPPLIER_A, "A-10023", "Riverside Hotel Seoul"),
                )

            assertEquals(SupplierCode.SUPPLIER_A, saved.supplier)
            assertEquals("A-10023", saved.externalHotelCode)
            assert(saved.id > 0) { "저장 후 id가 자동 채번되어야 한다" }
        }

        @Test
        fun `findBySupplierAndExternalHotelCode로 조회할 수 있다`() {
            val saved =
                hotelMappingRepository.save(
                    HotelMapping(SupplierCode.SUPPLIER_A, "A-10023", "Riverside Hotel Seoul"),
                )

            val found = hotelMappingRepository.findBySupplierAndExternalHotelCode(SupplierCode.SUPPLIER_A, "A-10023")

            assertEquals(saved.id, found?.id)
        }

        @Test
        fun `존재하지 않는 supplier+externalHotelCode 조합은 null을 반환한다`() {
            val found = hotelMappingRepository.findBySupplierAndExternalHotelCode(SupplierCode.SUPPLIER_A, "NO-SUCH-CODE")

            assertNull(found)
        }

        @Test
        fun `findById(Long)로 조회할 수 있다`() {
            val saved =
                hotelMappingRepository.save(
                    HotelMapping(SupplierCode.SUPPLIER_A, "A-10023", "Riverside Hotel Seoul"),
                )

            val found = hotelMappingRepository.findById(saved.id).orElseThrow()

            assertEquals("Riverside Hotel Seoul", found.hotelName)
        }
    }
