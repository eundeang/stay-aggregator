package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.SupplierAvailabilityResult
import com.eundeang.aggregator.domain.SupplierClient
import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierHotel
import com.eundeang.aggregator.domain.SupplierRoomType
import com.eundeang.aggregator.mapping.HotelMapping
import com.eundeang.aggregator.mapping.HotelMappingRepository
import com.eundeang.aggregator.mapping.MappingBatchUpsertService
import com.eundeang.aggregator.mapping.RoomTypeMappingRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import javax.sql.DataSource

/**
 * docs/architecture.md "매핑 생성 트리거" — 여러 공급사를 순회하며 각각의
 * fetchHotels() 결과를 매핑 테이블과 동기화하는 오케스트레이션 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MappingSyncRunnerTest
    @Autowired
    constructor(
        private val hotelMappingRepository: HotelMappingRepository,
        private val roomTypeMappingRepository: RoomTypeMappingRepository,
        dataSource: DataSource,
    ) {
        private val mappingSyncService = MappingSyncService(MappingBatchUpsertService(JdbcTemplate(dataSource)))

        @Test
        fun `여러 공급사의 숙소 목록을 각자의 supplier 코드로 동기화한다`() {
            val hotelA =
                SupplierHotel(
                    externalHotelCode = "A-10023",
                    hotelName = "Riverside Hotel Seoul",
                    roomTypes = listOf(SupplierRoomType("DLX-TWN", "Deluxe Twin", 2)),
                )
            val hotelB =
                SupplierHotel(
                    externalHotelCode = "B77120",
                    hotelName = "Riverside Hotel Seoul",
                    roomTypes = listOf(SupplierRoomType("STD", "Standard", 2)),
                )
            val runner =
                MappingSyncRunner(
                    supplierClients =
                        listOf(
                            FakeSupplierClient(SupplierCode.SUPPLIER_A, listOf(hotelA)),
                            FakeSupplierClient(SupplierCode.SUPPLIER_B, listOf(hotelB)),
                        ),
                    mappingSyncService = mappingSyncService,
                )

            runner.syncAll()

            val mappingA = hotelMappingRepository.findById(HotelMapping.Id(SupplierCode.SUPPLIER_A, "A-10023")).orElseThrow()
            val mappingB = hotelMappingRepository.findById(HotelMapping.Id(SupplierCode.SUPPLIER_B, "B77120")).orElseThrow()
            assertEquals("Riverside Hotel Seoul", mappingA.hotelName)
            assertEquals("Riverside Hotel Seoul", mappingB.hotelName)
            assertEquals(1, roomTypeMappingRepository.findAllByHotelMapping(mappingA).size)
            assertEquals(1, roomTypeMappingRepository.findAllByHotelMapping(mappingB).size)
        }

        private class FakeSupplierClient(
            override val supplier: SupplierCode,
            private val hotels: List<SupplierHotel>,
        ) : SupplierClient {
            override suspend fun fetchHotels(): List<SupplierHotel> = hotels

            override suspend fun fetchAvailability(
                externalHotelCodes: List<String>,
                checkIn: LocalDate,
                checkOut: LocalDate,
                adults: Int,
                children: Int,
            ): SupplierAvailabilityResult = throw UnsupportedOperationException("이 테스트에서는 사용되지 않음")
        }
    }
