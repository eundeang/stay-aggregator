package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.HotelId
import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierHotel
import com.eundeang.aggregator.domain.SupplierRoomType
import com.eundeang.aggregator.mapping.HotelMappingRepository
import com.eundeang.aggregator.mapping.MappingBatchUpsertService
import com.eundeang.aggregator.mapping.RoomTypeMappingRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * docs/architecture.md "같은 공급사 상품이 항상 같은 내부 식별자로 매핑되는 것을
 * DB 레벨에서 보장". H2 대신 실제 MySQL로 검증 (JOURNAL.md Day 1 결정 근거와 동일).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MappingSyncServiceTest
    @Autowired
    constructor(
        private val hotelMappingRepository: HotelMappingRepository,
        private val roomTypeMappingRepository: RoomTypeMappingRepository,
        private val entityManager: EntityManager,
        dataSource: DataSource,
    ) {
        private val mappingBatchUpsertService = MappingBatchUpsertService(JdbcTemplate(dataSource))
        private lateinit var service: MappingSyncService

        @BeforeEach
        fun setUp() {
            service = MappingSyncService(mappingBatchUpsertService)
        }

        @Test
        fun `신규 숙소를 동기화하면 HotelMapping과 RoomTypeMapping이 생성된다`() {
            val hotels =
                listOf(
                    SupplierHotel(
                        externalHotelCode = "A-10023",
                        hotelName = "Riverside Hotel Seoul",
                        roomTypes =
                            listOf(
                                SupplierRoomType("DLX-TWN", "Deluxe Twin", 2),
                            ),
                    ),
                )

            service.syncHotels(SupplierCode.SUPPLIER_A, hotels)

            val hotelMapping =
                hotelMappingRepository
                    .findById(HotelId(SupplierCode.SUPPLIER_A, "A-10023"))
                    .orElseThrow()
            assertEquals("Riverside Hotel Seoul", hotelMapping.hotelName)

            val roomTypes = roomTypeMappingRepository.findAllByHotelMapping(hotelMapping)
            assertEquals(1, roomTypes.size)
            assertEquals("DLX-TWN", roomTypes[0].externalRoomTypeCode)
            assertEquals("Deluxe Twin", roomTypes[0].roomTypeName)
            assertEquals(2, roomTypes[0].maxOccupancy)
        }

        @Test
        fun `같은 외부 코드로 두 번 동기화해도 레코드가 중복 생성되지 않는다`() {
            val hotels =
                listOf(
                    SupplierHotel(
                        externalHotelCode = "A-10023",
                        hotelName = "Riverside Hotel Seoul",
                        roomTypes =
                            listOf(
                                SupplierRoomType("DLX-TWN", "Deluxe Twin", 2),
                            ),
                    ),
                )

            service.syncHotels(SupplierCode.SUPPLIER_A, hotels)
            service.syncHotels(SupplierCode.SUPPLIER_A, hotels)

            assertEquals(1, hotelMappingRepository.findAll().size)

            val hotelMapping =
                hotelMappingRepository
                    .findById(HotelId(SupplierCode.SUPPLIER_A, "A-10023"))
                    .orElseThrow()
            assertEquals(1, roomTypeMappingRepository.findAllByHotelMapping(hotelMapping).size)
        }

        @Test
        fun `숙소명이 바뀌면 hotelName만 갱신되고 식별자는 그대로 유지된다`() {
            service.syncHotels(
                SupplierCode.SUPPLIER_A,
                listOf(
                    SupplierHotel(
                        externalHotelCode = "A-10023",
                        hotelName = "Riverside Hotel Seoul",
                        roomTypes = emptyList(),
                    ),
                ),
            )
            val idBeforeRename =
                hotelMappingRepository
                    .findById(HotelId(SupplierCode.SUPPLIER_A, "A-10023"))
                    .orElseThrow()
                    .id

            service.syncHotels(
                SupplierCode.SUPPLIER_A,
                listOf(
                    SupplierHotel(
                        externalHotelCode = "A-10023",
                        hotelName = "Riverside Hotel Seoul (Renamed)",
                        roomTypes = emptyList(),
                    ),
                ),
            )
            // JDBC upsert는 Hibernate 세션을 거치지 않아 1차 캐시가 갱신을 모른다
            entityManager.clear()

            assertEquals(1, hotelMappingRepository.findAll().size)
            val updated =
                hotelMappingRepository
                    .findById(HotelId(SupplierCode.SUPPLIER_A, "A-10023"))
                    .orElseThrow()
            assertEquals("Riverside Hotel Seoul (Renamed)", updated.hotelName)
            assertEquals(idBeforeRename, updated.id)
        }

        @Test
        fun `객실 타입명과 정원이 바뀌면 갱신되고 식별자는 그대로 유지된다`() {
            service.syncHotels(
                SupplierCode.SUPPLIER_A,
                listOf(
                    SupplierHotel(
                        externalHotelCode = "A-10023",
                        hotelName = "Riverside Hotel Seoul",
                        roomTypes = listOf(SupplierRoomType("DLX-TWN", "Deluxe Twin", 2)),
                    ),
                ),
            )
            val hotelMapping =
                hotelMappingRepository
                    .findById(HotelId(SupplierCode.SUPPLIER_A, "A-10023"))
                    .orElseThrow()
            val idBeforeUpdate =
                roomTypeMappingRepository
                    .findByHotelMappingAndExternalRoomTypeCode(hotelMapping, "DLX-TWN")!!
                    .id

            service.syncHotels(
                SupplierCode.SUPPLIER_A,
                listOf(
                    SupplierHotel(
                        externalHotelCode = "A-10023",
                        hotelName = "Riverside Hotel Seoul",
                        roomTypes = listOf(SupplierRoomType("DLX-TWN", "Deluxe Twin Renamed", 3)),
                    ),
                ),
            )
            entityManager.clear()

            assertEquals(1, roomTypeMappingRepository.findAllByHotelMapping(hotelMapping).size)
            val updated = roomTypeMappingRepository.findByHotelMappingAndExternalRoomTypeCode(hotelMapping, "DLX-TWN")!!
            assertEquals("Deluxe Twin Renamed", updated.roomTypeName)
            assertEquals(3, updated.maxOccupancy)
            assertEquals(idBeforeUpdate, updated.id)
        }

        @Test
        fun `같은 객실 타입 코드라도 숙소가 다르면 별도 레코드로 존재한다`() {
            service.syncHotels(
                SupplierCode.SUPPLIER_A,
                listOf(
                    SupplierHotel(
                        externalHotelCode = "A-10023",
                        hotelName = "Riverside Hotel Seoul",
                        roomTypes = listOf(SupplierRoomType("STD", "Standard", 2)),
                    ),
                    SupplierHotel(
                        externalHotelCode = "A-20045",
                        hotelName = "Namsan Garden Stay",
                        roomTypes = listOf(SupplierRoomType("STD", "Standard Double", 2)),
                    ),
                ),
            )

            val hotelA =
                hotelMappingRepository
                    .findById(HotelId(SupplierCode.SUPPLIER_A, "A-10023"))
                    .orElseThrow()
            val hotelB =
                hotelMappingRepository
                    .findById(HotelId(SupplierCode.SUPPLIER_A, "A-20045"))
                    .orElseThrow()

            val roomA = roomTypeMappingRepository.findByHotelMappingAndExternalRoomTypeCode(hotelA, "STD")!!
            val roomB = roomTypeMappingRepository.findByHotelMappingAndExternalRoomTypeCode(hotelB, "STD")!!

            assertEquals("Standard", roomA.roomTypeName)
            assertEquals("Standard Double", roomB.roomTypeName)
            assert(roomA.id != roomB.id) { "서로 다른 숙소의 같은 객실 타입 코드는 별도 레코드여야 한다" }
        }
    }
