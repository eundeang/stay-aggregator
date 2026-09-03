package com.eundeang.aggregator.supplier.supplierb

import com.eundeang.aggregator.domain.DailyInventory
import com.eundeang.aggregator.domain.SupplierAvailabilityResult
import com.eundeang.aggregator.domain.SupplierClient
import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierFailureReason
import com.eundeang.aggregator.domain.SupplierHotel
import com.eundeang.aggregator.domain.SupplierOffer
import com.eundeang.aggregator.domain.SupplierRoomType
import kotlinx.coroutines.CancellationException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.net.http.HttpTimeoutException
import java.time.LocalDate

private const val SUCCESS_RESULT_CODE = "0000"

@Component
class SupplierBClient(
    @Qualifier("supplierBWebClient") private val webClient: WebClient,
) : SupplierClient {
    override val supplier: SupplierCode = SupplierCode.SUPPLIER_B

    override suspend fun fetchHotels(): List<SupplierHotel> {
        val response =
            webClient
                .get()
                .uri("/api/properties")
                .retrieve()
                .awaitBody<SupplierBPropertiesResponseDto>()
        check(response.resultCode == SUCCESS_RESULT_CODE) {
            "Supplier B fetchHotels failed: ${response.resultCode} ${response.resultMessage}"
        }
        return response.data!!.items.map { it.toSupplierHotel() }
    }

    override suspend fun fetchAvailability(
        externalHotelCodes: List<String>,
        checkIn: LocalDate,
        checkOut: LocalDate,
        adults: Int,
        children: Int,
    ): SupplierAvailabilityResult =
        try {
            val response =
                webClient
                    .get()
                    .uri { uriBuilder ->
                        uriBuilder
                            .path("/api/search")
                            .queryParam("propertyIds", externalHotelCodes.joinToString(","))
                            .queryParam("checkIn", checkIn)
                            .queryParam("checkOut", checkOut)
                            .queryParam("adults", adults)
                            .queryParam("children", children)
                            .build()
                    }.retrieve()
                    .awaitBody<SupplierBSearchResponseDto>()

            // B는 HTTP 상태 코드가 항상 200이라, resultCode로만 성공/실패를 판정한다 (실패 판정 통일의 핵심).
            if (response.resultCode == SUCCESS_RESULT_CODE) {
                SupplierAvailabilityResult.Success(response.data!!.items.map { it.toSupplierOffer() })
            } else {
                SupplierAvailabilityResult.Failure(mapResultCodeToReason(response.resultCode), response.resultMessage)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isTimeout(e)) {
                SupplierAvailabilityResult.Failure(SupplierFailureReason.TIMEOUT, "Supplier B 응답 지연/타임아웃")
            } else {
                SupplierAvailabilityResult.Failure(SupplierFailureReason.UNKNOWN, e.message ?: "Supplier B 호출 실패")
            }
        }
}

private fun mapResultCodeToReason(resultCode: String): SupplierFailureReason =
    when (resultCode) {
        "E400" -> SupplierFailureReason.INVALID_REQUEST
        "E401" -> SupplierFailureReason.AUTH_FAILED
        "E429" -> SupplierFailureReason.RATE_LIMITED
        "E500", "E503" -> SupplierFailureReason.SUPPLIER_ERROR
        else -> SupplierFailureReason.UNKNOWN
    }

private fun isTimeout(throwable: Throwable): Boolean {
    var cause: Throwable? = throwable
    while (cause != null) {
        if (cause is HttpTimeoutException) return true
        cause = cause.cause
    }
    return false
}

private fun SupplierBPropertyDto.toSupplierHotel(): SupplierHotel =
    SupplierHotel(
        externalHotelCode = propertyId,
        hotelName = propertyName,
        roomTypes =
            rooms.map {
                SupplierRoomType(
                    externalRoomTypeCode = it.roomId,
                    roomTypeName = it.roomName,
                    maxOccupancy = it.maxOccupancy,
                )
            },
    )

// B: totalPrice(세금 포함) → totalAmount 그대로. 일자별 단가 원본이 없어 nightlyNetAmounts는 null.
private fun SupplierBSearchItemDto.toSupplierOffer(): SupplierOffer =
    SupplierOffer(
        externalHotelCode = propertyId,
        externalRoomTypeCode = roomId,
        breakfastIncluded = breakfastIncluded,
        currency = currency,
        totalAmount = totalPrice,
        nightlyNetAmounts = null,
        dailyRemainingRooms =
            inventory.map {
                DailyInventory(date = LocalDate.parse(it.date), remainingRooms = it.remainingRooms)
            },
    )
