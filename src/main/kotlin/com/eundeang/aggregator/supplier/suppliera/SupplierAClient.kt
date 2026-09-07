package com.eundeang.aggregator.supplier.suppliera

import com.eundeang.aggregator.domain.DailyInventory
import com.eundeang.aggregator.domain.NightlyNetAmount
import com.eundeang.aggregator.domain.SupplierAvailabilityResult
import com.eundeang.aggregator.domain.SupplierClient
import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierFailureReason
import com.eundeang.aggregator.domain.SupplierHotel
import com.eundeang.aggregator.domain.SupplierOffer
import com.eundeang.aggregator.domain.SupplierRoomType
import com.eundeang.aggregator.domain.calculateTotalAmount
import com.eundeang.aggregator.supplier.isTimeout
import kotlinx.coroutines.CancellationException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchange
import java.time.LocalDate

@Component
class SupplierAClient(
    @Qualifier("supplierAWebClient") private val webClient: WebClient,
) : SupplierClient {
    override val supplier: SupplierCode = SupplierCode.SUPPLIER_A

    override suspend fun fetchHotels(): List<SupplierHotel> {
        val response =
            webClient
                .get()
                .uri("/v1/hotels")
                .retrieve()
                .awaitBody<SupplierAHotelsResponseDto>()
        return response.items.map { it.toSupplierHotel() }
    }

    override suspend fun fetchAvailability(
        externalHotelCodes: List<String>,
        checkIn: LocalDate,
        checkOut: LocalDate,
        adults: Int,
        children: Int,
    ): SupplierAvailabilityResult =
        try {
            webClient
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/v1/availability")
                        .queryParam("hotelCodes", externalHotelCodes.joinToString(","))
                        .queryParam("checkIn", checkIn)
                        .queryParam("checkOut", checkOut)
                        .queryParam("adults", adults)
                        .queryParam("children", children)
                        .build()
                }.awaitExchange { response ->
                    if (response.statusCode().is2xxSuccessful) {
                        val body = response.awaitBody<SupplierAAvailabilityResponseDto>()
                        SupplierAvailabilityResult.Success(body.toSupplierOffers())
                    } else {
                        val message =
                            runCatching { response.awaitBody<SupplierAErrorResponseDto>().message }
                                .getOrElse { "Supplier A returned ${response.statusCode().value()}" }
                        SupplierAvailabilityResult.Failure(mapStatusToReason(response.statusCode()), message)
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isTimeout(e)) {
                SupplierAvailabilityResult.Failure(SupplierFailureReason.TIMEOUT, "Supplier A 응답 지연/타임아웃")
            } else {
                SupplierAvailabilityResult.Failure(SupplierFailureReason.UNKNOWN, e.message ?: "Supplier A 호출 실패")
            }
        }
}

private fun mapStatusToReason(status: HttpStatusCode): SupplierFailureReason =
    when {
        status.value() == 400 -> SupplierFailureReason.INVALID_REQUEST
        status.value() == 401 -> SupplierFailureReason.AUTH_FAILED
        status.value() == 429 -> SupplierFailureReason.RATE_LIMITED
        status.is5xxServerError -> SupplierFailureReason.SUPPLIER_ERROR
        else -> SupplierFailureReason.UNKNOWN
    }

private fun SupplierAHotelDto.toSupplierHotel(): SupplierHotel =
    SupplierHotel(
        externalHotelCode = hotelCode,
        hotelName = hotelName,
        roomTypes =
            roomTypes.map {
                SupplierRoomType(
                    externalRoomTypeCode = it.roomTypeCode,
                    roomTypeName = it.roomTypeName,
                    maxOccupancy = it.maxOccupancy,
                )
            },
    )

// A: dailyRates[]의 nightlyRate → nightlyNetAmounts 그대로, Σ(nightlyRate+taxAmount) → totalAmount(세금 포함)
// breakfastIncluded/currency는 dailyRates 안이 아니라 item 최상위(객실 타입당 1개 값)에서 읽는다.
private fun SupplierAAvailabilityResponseDto.toSupplierOffers(): List<SupplierOffer> =
    items.map { item ->
        SupplierOffer(
            externalHotelCode = item.hotelCode,
            externalRoomTypeCode = item.roomTypeCode,
            breakfastIncluded = item.breakfastIncluded,
            currency = item.currency,
            totalAmount = calculateTotalAmount(item.dailyRates.map { it.nightlyRate to it.taxAmount }),
            nightlyNetAmounts =
                item.dailyRates.map {
                    NightlyNetAmount(date = LocalDate.parse(it.date), amount = it.nightlyRate)
                },
            dailyRemainingRooms =
                item.dailyRates.map {
                    DailyInventory(date = LocalDate.parse(it.date), remainingRooms = it.remainingRooms)
                },
        )
    }
