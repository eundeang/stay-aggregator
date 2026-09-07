package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierHotel
import com.eundeang.aggregator.domain.SupplierRoomType
import com.eundeang.aggregator.mapping.HotelMappingRepository
import com.eundeang.aggregator.mapping.MappingBatchUpsertRepository
import com.eundeang.aggregator.mapping.RoomTypeMappingRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
        private val jdbcTemplate = JdbcTemplate(dataSource)
        private val mappingBatchUpsertService = MappingBatchUpsertRepository(jdbcTemplate)
        private lateinit var service: MappingSyncService

        @BeforeEach
        fun setUp() {
            service = MappingSyncService(mappingBatchUpsertService)
        }

        private fun findHotel(externalHotelCode: String) =
            hotelMappingRepository.findBySupplierAndExternalHotelCode(SupplierCode.SUPPLIER_A, externalHotelCode)!!

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

            val hotelMapping = findHotel("A-10023")
            assertEquals("Riverside Hotel Seoul", hotelMapping.hotelName)

            val roomTypes = roomTypeMappingRepository.findAllByHotelMapping(hotelMapping)
            assertEquals(1, roomTypes.size)
            assertEquals("DLX-TWN", roomTypes[0].externalRoomTypeCode)
            assertEquals("Deluxe Twin", roomTypes[0].roomTypeName)
            assertEquals(2, roomTypes[0].maxOccupancy)
        }

        @Test
        fun `동기화 시 room_type_mapping의 hotel_mapping_id가 raw SQL 쓰기 경로에서도 실제 hotel_mapping id와 일치한다`() {
            // MappingBatchUpsertRepository는 JPA가 아니라 JdbcTemplate로 직접 INSERT하므로,
            // hotel_mapping_id를 채우는 책임이 JPA 관계 매핑이 아니라 이 raw SQL 자체에 있다 —
            // 근거: docs/architecture.md "매핑 배치 upsert".
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

            val hotelMapping = findHotel("A-10023")
            val rawHotelMappingId =
                jdbcTemplate.queryForObject(
                    "SELECT hotel_mapping_id FROM room_type_mapping WHERE external_room_type_code = ?",
                    Long::class.java,
                    "DLX-TWN",
                )

            assertEquals(hotelMapping.id, rawHotelMappingId)
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

            val hotelMapping = findHotel("A-10023")
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
            val idBeforeRename = findHotel("A-10023").id

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
            val updated = findHotel("A-10023")
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
            val hotelMapping = findHotel("A-10023")
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

            val hotelA = findHotel("A-10023")
            val hotelB = findHotel("A-20045")

            val roomA = roomTypeMappingRepository.findByHotelMappingAndExternalRoomTypeCode(hotelA, "STD")!!
            val roomB = roomTypeMappingRepository.findByHotelMappingAndExternalRoomTypeCode(hotelB, "STD")!!

            assertEquals("Standard", roomA.roomTypeName)
            assertEquals("Standard Double", roomB.roomTypeName)
            assert(roomA.id != roomB.id) { "서로 다른 숙소의 같은 객실 타입 코드는 별도 레코드여야 한다" }
        }

        @Test
        fun `재동기화 시 신규 객실 타입이 추가되면 기존 hotel_mapping_id에 연결된다`() {
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
            val hotelMapping = findHotel("A-10023")
            val hotelIdBeforeReSync = hotelMapping.id

            service.syncHotels(
                SupplierCode.SUPPLIER_A,
                listOf(
                    SupplierHotel(
                        externalHotelCode = "A-10023",
                        hotelName = "Riverside Hotel Seoul",
                        roomTypes =
                            listOf(
                                SupplierRoomType("DLX-TWN", "Deluxe Twin", 2),
                                SupplierRoomType("STE", "Suite", 4),
                            ),
                    ),
                ),
            )
            entityManager.clear()

            val updatedHotelMapping = findHotel("A-10023")
            assertEquals(hotelIdBeforeReSync, updatedHotelMapping.id)

            val roomTypes = roomTypeMappingRepository.findAllByHotelMapping(updatedHotelMapping)
            assertEquals(2, roomTypes.size)
            assertTrue(roomTypes.any { it.externalRoomTypeCode == "STE" })
        }

        @Test
        fun `여러 청크에 걸쳐 기존 숙소와 신규 숙소가 섞여도 각자 안정적인 id로 연결된다`() {
            val smallChunkService = MappingSyncService(mappingBatchUpsertService, hotelChunkSize = 2)
            smallChunkService.syncHotels(
                SupplierCode.SUPPLIER_A,
                listOf(
                    SupplierHotel("A-1", "Hotel 1", listOf(SupplierRoomType("STD", "Standard", 2))),
                    SupplierHotel("A-2", "Hotel 2", listOf(SupplierRoomType("STD", "Standard", 2))),
                    SupplierHotel("A-3", "Hotel 3", listOf(SupplierRoomType("STD", "Standard", 2))),
                ),
            )
            val idsBeforeReSync =
                listOf("A-1", "A-2", "A-3").associateWith {
                    findHotel(it).id
                }

            // 청크 크기 2로 4개(기존 3 + 신규 1)를 동기화하면 청크 경계가
            // [A-1, A-2] / [A-3, A-4]로 나뉘어 "기존+기존" 청크와 "기존+신규" 청크가
            // 섞인 상황을 재현한다.
            smallChunkService.syncHotels(
                SupplierCode.SUPPLIER_A,
                listOf(
                    SupplierHotel("A-1", "Hotel 1", listOf(SupplierRoomType("STD", "Standard", 2))),
                    SupplierHotel("A-2", "Hotel 2", listOf(SupplierRoomType("STD", "Standard", 2))),
                    SupplierHotel("A-3", "Hotel 3", listOf(SupplierRoomType("STD", "Standard", 2))),
                    SupplierHotel("A-4", "Hotel 4", listOf(SupplierRoomType("STD", "Standard", 2))),
                ),
            )
            entityManager.clear()

            idsBeforeReSync.forEach { (code, id) ->
                val current = findHotel(code)
                assertEquals(id, current.id, "$code 는 재동기화 후에도 같은 id를 유지해야 한다")
                val roomTypes = roomTypeMappingRepository.findAllByHotelMapping(current)
                assertEquals(1, roomTypes.size)
            }
            val hotel4 = findHotel("A-4")
            assertEquals(1, roomTypeMappingRepository.findAllByHotelMapping(hotel4).size)
            assertEquals(4, hotelMappingRepository.findAll().size)
        }
    }
