package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.HotelId
import com.eundeang.aggregator.domain.NightlyRate
import com.eundeang.aggregator.domain.Price
import com.eundeang.aggregator.domain.RoomTypeOffer
import com.eundeang.aggregator.domain.Stay
import com.eundeang.aggregator.domain.SupplierAvailabilityResult
import com.eundeang.aggregator.domain.SupplierClient
import com.eundeang.aggregator.domain.SupplierFailureReason
import com.eundeang.aggregator.domain.SupplierOffer
import com.eundeang.aggregator.domain.calculateAvailableRooms
import com.eundeang.aggregator.mapping.HotelMappingRepository
import com.eundeang.aggregator.mapping.RoomTypeMappingRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * 공급사 재고·요금 API(②)는 숙소 코드를 최대 50개까지만 받는다
 * (과제 안내 문서(부록 A) "조회 흐름"). 그 이상을 한 번에 보내면 안 되므로
 * 공급사별로 보유 숙소 코드를 이 크기로 나눠 여러 번 호출한다.
 */
private const val HOTEL_CODES_CHUNK_SIZE = 50

/**
 * 검색 오케스트레이션: 매핑 전체 조회 → 공급사별 청크 병렬 호출 → 내부 식별자
 * resolve → 표준 모델(Stay/RoomTypeOffer) 조립. 근거: docs/supplier-adapter.md
 * "왜 도메인 모델을 바로 안 만들고 중간 타입을 두는가".
 */
@Service
class StaySearchService(
    private val supplierClients: List<SupplierClient>,
    private val hotelMappingRepository: HotelMappingRepository,
    private val roomTypeMappingRepository: RoomTypeMappingRepository,
) {
    suspend fun search(
        checkIn: LocalDate,
        checkOut: LocalDate,
        adults: Int,
        children: Int,
    ): StaySearchResult {
        val hotelMappings = hotelMappingRepository.findAll()
        val hotelMappingById = hotelMappings.associateBy { HotelId(it.supplier, it.externalHotelCode) }
        val roomTypeMappingByKey =
            roomTypeMappingRepository.findAll().associateBy {
                HotelId(it.hotelMapping.supplier, it.hotelMapping.externalHotelCode) to it.externalRoomTypeCode
            }
        val hotelCodesBySupplier = hotelMappings.groupBy({ it.supplier }) { it.externalHotelCode }

        // 매핑이 비어있는 공급사는 조용히 건너뛰지 않고 NO_MAPPING_DATA로 명시한다 —
        // "실제로 상품이 0개인 정상 상황"과 "동기화 실패로 매핑 자체가 없는 비정상
        // 상황"이 API 응답에서 구분 안 되는 문제였음 (근거: JOURNAL.md, docs/architecture.md
        // "매핑 없는 공급사 처리").
        val (suppliersWithMapping, suppliersWithoutMapping) =
            supplierClients.partition { hotelCodesBySupplier[it.supplier].orEmpty().isNotEmpty() }
        val noMappingFailures =
            suppliersWithoutMapping.map { PartialFailure(it.supplier, SupplierFailureReason.NO_MAPPING_DATA) }

        val callResults =
            coroutineScope {
                suppliersWithMapping
                    .flatMap { client ->
                        hotelCodesBySupplier.getValue(client.supplier).chunked(HOTEL_CODES_CHUNK_SIZE).map { chunk ->
                            async { client.supplier to client.fetchAvailability(chunk, checkIn, checkOut, adults, children) }
                        }
                    }.map { it.await() }
            }

        val callFailures =
            callResults.mapNotNull { (supplier, result) ->
                (result as? SupplierAvailabilityResult.Failure)?.let { PartialFailure(supplier, it.reason) }
            }
        val partialFailures = noMappingFailures + callFailures

        // 매핑을 순회하지 않고 응답에 실제로 있는 offer만 순회한다 — 인원 초과 등으로
        // 응답에서 아예 빠진 객실 타입을 재고 0으로 억지로 채우지 않기 위함
        // (근거: readme.md "가정", 과제 안내 문서(부록 A) "공통 규약").
        val offersByHotel: Map<HotelId, List<SupplierOffer>> =
            callResults
                .flatMap { (supplier, result) ->
                    when (result) {
                        is SupplierAvailabilityResult.Success -> result.offers.map { offer -> supplier to offer }
                        is SupplierAvailabilityResult.Failure -> emptyList()
                    }
                }.groupBy({ (supplier, offer) -> HotelId(supplier, offer.externalHotelCode) }) { (_, offer) -> offer }

        val results =
            offersByHotel.mapNotNull { (hotelId, offers) ->
                val hotelMapping = hotelMappingById[hotelId] ?: return@mapNotNull null
                val roomTypes =
                    offers.mapNotNull { offer ->
                        val roomTypeMapping = roomTypeMappingByKey[hotelId to offer.externalRoomTypeCode] ?: return@mapNotNull null
                        RoomTypeOffer(
                            roomTypeId = roomTypeMapping.id,
                            roomTypeName = roomTypeMapping.roomTypeName,
                            maxOccupancy = roomTypeMapping.maxOccupancy,
                            breakfastIncluded = offer.breakfastIncluded,
                            availableRooms = calculateAvailableRooms(offer.dailyRemainingRooms),
                            price =
                                Price(
                                    currency = offer.currency,
                                    totalAmount = offer.totalAmount,
                                    nightlyRates = offer.nightlyNetAmounts?.map { NightlyRate(it.date, it.amount) },
                                ),
                        )
                    }
                Stay(
                    hotelId = hotelId,
                    hotelName = hotelMapping.hotelName,
                    sourceSupplier = hotelId.supplier,
                    roomTypes = roomTypes,
                )
            }

        return StaySearchResult(results, partialFailures)
    }
}
