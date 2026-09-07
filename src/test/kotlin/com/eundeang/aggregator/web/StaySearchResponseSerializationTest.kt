package com.eundeang.aggregator.web

import com.eundeang.aggregator.application.FakeSupplierClient
import com.eundeang.aggregator.application.StaySearchService
import com.eundeang.aggregator.domain.DailyInventory
import com.eundeang.aggregator.domain.SupplierAvailabilityResult
import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierOffer
import com.eundeang.aggregator.mapping.HotelMapping
import com.eundeang.aggregator.mapping.HotelMappingRepository
import com.eundeang.aggregator.mapping.RoomTypeMapping
import com.eundeang.aggregator.mapping.RoomTypeMappingRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.time.LocalDate

/**
 * Stay.hotelId를 도메인/서비스 레벨에서 Long으로 바꿔도, 실제 컨트롤러가 만드는
 * JSON이 그 형태로 나가는지는 별개로 확인해야 한다 — 근거: 사용자 지적("도메인에서는
 * 맞아 보이는데 실제 API JSON은 다름" 문제 재발 방지), docs/domain-model.md "응답 구조".
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StaySearchResponseSerializationTest
    @Autowired
    constructor(
        private val hotelMappingRepository: HotelMappingRepository,
        private val roomTypeMappingRepository: RoomTypeMappingRepository,
    ) {
        private val objectMapper =
            JsonMapper
                .builder()
                .addModule(kotlinModule())
                .findAndAddModules()
                .build()

        private val checkIn = LocalDate.of(2026, 9, 1)
        private val checkOut = LocalDate.of(2026, 9, 4)

        @Test
        fun `검색 응답 JSON의 hotelId roomTypeId는 숫자이고 공급사 원본 코드는 노출되지 않는다`() {
            val hotelMapping =
                hotelMappingRepository.save(HotelMapping(SupplierCode.SUPPLIER_A, "A-10023", "Riverside Hotel Seoul"))
            val roomTypeMapping =
                roomTypeMappingRepository.save(RoomTypeMapping(hotelMapping, "DLX-TWN", "Deluxe Twin", 2))

            val client =
                FakeSupplierClient(SupplierCode.SUPPLIER_A) {
                    SupplierAvailabilityResult.Success(
                        listOf(
                            SupplierOffer(
                                externalHotelCode = "A-10023",
                                externalRoomTypeCode = "DLX-TWN",
                                breakfastIncluded = false,
                                currency = "KRW",
                                totalAmount = 300_000,
                                nightlyNetAmounts = null,
                                dailyRemainingRooms = listOf(DailyInventory(checkIn, 3)),
                            ),
                        ),
                    )
                }
            val service = StaySearchService(listOf(client), hotelMappingRepository, roomTypeMappingRepository)
            val controller = StaySearchController(service)

            val result = runBlocking { controller.search(checkIn, checkOut, 2, 0) }
            val json = objectMapper.writeValueAsString(result)
            val node = objectMapper.readTree(json)

            val hotelNode = node["results"][0]
            assertTrue(hotelNode["hotelId"].isNumber, "hotelId는 숫자로 직렬화되어야 한다: $json")
            assertEquals(hotelMapping.id, hotelNode["hotelId"].asLong())

            val roomTypeNode = hotelNode["roomTypes"][0]
            assertTrue(roomTypeNode["roomTypeId"].isNumber, "roomTypeId는 숫자로 직렬화되어야 한다: $json")
            assertEquals(roomTypeMapping.id, roomTypeNode["roomTypeId"].asLong())

            assertFalse(json.contains("A-10023"), "공급사 원본 숙소 코드가 응답 JSON에 노출되면 안 된다: $json")
            assertFalse(json.contains("externalHotelCode"), "supplier/externalHotelCode 필드명 자체가 노출되면 안 된다: $json")
        }
    }
