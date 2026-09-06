package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.DailyInventory
import com.eundeang.aggregator.domain.SupplierAvailabilityResult
import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierFailureReason
import com.eundeang.aggregator.domain.SupplierOffer
import com.eundeang.aggregator.mapping.HotelMapping
import com.eundeang.aggregator.mapping.HotelMappingRepository
import com.eundeang.aggregator.mapping.RoomTypeMapping
import com.eundeang.aggregator.mapping.RoomTypeMappingRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import java.time.LocalDate

/**
 * docs/supplier-adapter.md "왜 도메인 모델을 바로 안 만들고 중간 타입을 두는가" —
 * SupplierClient는 fake로 대체하고 매핑은 실제 MySQL에 세팅해 조립 로직을 검증한다.
 * 오케스트레이션 세부는 구현하며 정해진 것이라(Phase 2) 구현 후 통합 테스트로 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StaySearchServiceTest
    @Autowired
    constructor(
        private val hotelMappingRepository: HotelMappingRepository,
        private val roomTypeMappingRepository: RoomTypeMappingRepository,
    ) {
        private val checkIn = LocalDate.of(2026, 9, 1)
        private val checkOut = LocalDate.of(2026, 9, 4)

        private fun seedHotel(
            supplier: SupplierCode,
            externalHotelCode: String,
            hotelName: String,
            externalRoomTypeCode: String,
            roomTypeName: String,
            maxOccupancy: Int = 2,
        ): HotelMapping {
            val hotelMapping = hotelMappingRepository.save(HotelMapping(supplier, externalHotelCode, hotelName))
            roomTypeMappingRepository.save(RoomTypeMapping(hotelMapping, externalRoomTypeCode, roomTypeName, maxOccupancy))
            return hotelMapping
        }

        private fun successOffer(
            externalHotelCode: String,
            externalRoomTypeCode: String,
        ) = SupplierAvailabilityResult.Success(
            listOf(
                SupplierOffer(
                    externalHotelCode = externalHotelCode,
                    externalRoomTypeCode = externalRoomTypeCode,
                    breakfastIncluded = false,
                    currency = "KRW",
                    totalAmount = 300_000,
                    nightlyNetAmounts = null,
                    dailyRemainingRooms = listOf(DailyInventory(checkIn, 3)),
                ),
            ),
        )

        @Test
        fun `모든 공급사가 정상이면 각 숙소가 결과에 포함되고 partialFailures는 비어있다`() {
            val hotelA = seedHotel(SupplierCode.SUPPLIER_A, "A-10023", "Riverside Hotel Seoul", "DLX-TWN", "Deluxe Twin")
            val hotelB = seedHotel(SupplierCode.SUPPLIER_B, "B77120", "Riverside Hotel Seoul", "R-401", "Deluxe Twin Room")
            val clientA = FakeSupplierClient(SupplierCode.SUPPLIER_A) { successOffer("A-10023", "DLX-TWN") }
            val clientB = FakeSupplierClient(SupplierCode.SUPPLIER_B) { successOffer("B77120", "R-401") }
            val service = StaySearchService(listOf(clientA, clientB), hotelMappingRepository, roomTypeMappingRepository)

            val result = runBlocking { service.search(checkIn, checkOut, 2, 0) }

            assertEquals(2, result.results.size)
            assertTrue(result.partialFailures.isEmpty())
            assertTrue(result.results.any { it.hotelId == hotelA.id })
            assertTrue(result.results.any { it.hotelId == hotelB.id })
        }

        @Test
        fun `한 공급사가 실패하면 나머지 결과만 포함되고 partialFailures에 기록된다`() {
            val hotelA = seedHotel(SupplierCode.SUPPLIER_A, "A-10023", "Riverside Hotel Seoul", "DLX-TWN", "Deluxe Twin")
            seedHotel(SupplierCode.SUPPLIER_B, "B77120", "Riverside Hotel Seoul", "R-401", "Deluxe Twin Room")
            val clientA = FakeSupplierClient(SupplierCode.SUPPLIER_A) { successOffer("A-10023", "DLX-TWN") }
            val clientB =
                FakeSupplierClient(SupplierCode.SUPPLIER_B) {
                    SupplierAvailabilityResult.Failure(SupplierFailureReason.TIMEOUT, "B 응답 지연")
                }
            val service = StaySearchService(listOf(clientA, clientB), hotelMappingRepository, roomTypeMappingRepository)

            val result = runBlocking { service.search(checkIn, checkOut, 2, 0) }

            assertEquals(1, result.results.size)
            assertEquals(hotelA.id, result.results.single().hotelId)
            assertEquals(1, result.partialFailures.size)
            assertEquals(PartialFailure(SupplierCode.SUPPLIER_B, SupplierFailureReason.TIMEOUT), result.partialFailures.single())
        }

        @Test
        fun `두 공급사 모두 실패하면 결과는 비고 partialFailures에 둘 다 기록된다`() {
            seedHotel(SupplierCode.SUPPLIER_A, "A-10023", "Riverside Hotel Seoul", "DLX-TWN", "Deluxe Twin")
            seedHotel(SupplierCode.SUPPLIER_B, "B77120", "Riverside Hotel Seoul", "R-401", "Deluxe Twin Room")
            val clientA =
                FakeSupplierClient(SupplierCode.SUPPLIER_A) {
                    SupplierAvailabilityResult.Failure(SupplierFailureReason.SUPPLIER_ERROR, "A 내부 오류")
                }
            val clientB =
                FakeSupplierClient(SupplierCode.SUPPLIER_B) {
                    SupplierAvailabilityResult.Failure(SupplierFailureReason.TIMEOUT, "B 응답 지연")
                }
            val service = StaySearchService(listOf(clientA, clientB), hotelMappingRepository, roomTypeMappingRepository)

            val result = runBlocking { service.search(checkIn, checkOut, 2, 0) }

            assertTrue(result.results.isEmpty())
            assertEquals(2, result.partialFailures.size)
            assertTrue(result.partialFailures.contains(PartialFailure(SupplierCode.SUPPLIER_A, SupplierFailureReason.SUPPLIER_ERROR)))
            assertTrue(result.partialFailures.contains(PartialFailure(SupplierCode.SUPPLIER_B, SupplierFailureReason.TIMEOUT)))
        }

        @Test
        fun `보유 숙소가 50개를 넘으면 여러 청크로 나눠 호출한다`() {
            (1..51).forEach { i ->
                seedHotel(SupplierCode.SUPPLIER_A, "A-$i", "Hotel $i", "STD", "Standard")
            }
            val clientA = FakeSupplierClient(SupplierCode.SUPPLIER_A) { SupplierAvailabilityResult.Success(emptyList()) }
            val service = StaySearchService(listOf(clientA), hotelMappingRepository, roomTypeMappingRepository)

            runBlocking { service.search(checkIn, checkOut, 2, 0) }

            assertEquals(2, clientA.calls.size)
            assertEquals(51, clientA.calls.sumOf { it.size })
            assertTrue(clientA.calls.all { it.size <= 50 })
        }

        @Test
        fun `공급사 매핑이 비어있으면 조회 자체를 시도하지 않고 NO_MAPPING_DATA로 기록된다`() {
            val hotelA = seedHotel(SupplierCode.SUPPLIER_A, "A-10023", "Riverside Hotel Seoul", "DLX-TWN", "Deluxe Twin")
            // SUPPLIER_B는 seedHotel을 호출하지 않아 매핑이 비어있음
            val clientA = FakeSupplierClient(SupplierCode.SUPPLIER_A) { successOffer("A-10023", "DLX-TWN") }
            val clientB = FakeSupplierClient(SupplierCode.SUPPLIER_B) { successOffer("B77120", "R-401") }
            val service = StaySearchService(listOf(clientA, clientB), hotelMappingRepository, roomTypeMappingRepository)

            val result = runBlocking { service.search(checkIn, checkOut, 2, 0) }

            assertEquals(1, result.results.size)
            assertEquals(hotelA.id, result.results.single().hotelId)
            assertEquals(1, result.partialFailures.size)
            assertEquals(
                PartialFailure(SupplierCode.SUPPLIER_B, SupplierFailureReason.NO_MAPPING_DATA),
                result.partialFailures.single(),
            )
            assertTrue(clientB.calls.isEmpty()) { "매핑이 없는 공급사는 fetchAvailability를 호출하지 않아야 한다" }
        }
    }
