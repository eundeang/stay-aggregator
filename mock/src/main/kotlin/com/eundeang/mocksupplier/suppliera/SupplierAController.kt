package com.eundeang.mocksupplier.suppliera

import com.eundeang.mocksupplier.ModeStore
import com.eundeang.mocksupplier.MockMode
import com.eundeang.mocksupplier.NO_RESPONSE_DELAY_MS
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/a/v1")
class SupplierAController(private val modeStore: ModeStore) {

    @GetMapping("/hotels")
    fun hotels(): ResponseEntity<Any> {
        errorResponse()?.let { return it }
        return ResponseEntity.ok(AHotelsResponse(SupplierAData.hotels))
    }

    @GetMapping("/availability")
    fun availability(
        @RequestParam hotelCodes: String,
        @RequestParam checkIn: String,
        @RequestParam checkOut: String,
        @RequestParam(required = false) adults: Int?,
        @RequestParam(required = false) children: Int?,
    ): ResponseEntity<Any> {
        errorResponse()?.let { return it }

        val codes = hotelCodes.split(",").map { it.trim() }
        val dates = datesBetween(LocalDate.parse(checkIn), LocalDate.parse(checkOut))

        val items = SupplierAData.hotels
            .filter { it.hotelCode in codes }
            .flatMap { hotel ->
                hotel.roomTypes.map { roomType ->
                    val rate = SupplierAData.rates.getValue(roomType.roomTypeCode)
                    AAvailabilityItem(
                        hotelCode = hotel.hotelCode,
                        hotelName = hotel.hotelName,
                        roomTypeCode = roomType.roomTypeCode,
                        roomTypeName = roomType.roomTypeName,
                        maxOccupancy = roomType.maxOccupancy,
                        breakfastIncluded = rate.breakfastIncluded,
                        currency = SupplierAData.CURRENCY,
                        dailyRates = dates.mapIndexed { index, date ->
                            ADailyRate(
                                date = date.toString(),
                                remainingRooms = rate.remainingRoomsCycle[index % rate.remainingRoomsCycle.size],
                                nightlyRate = rate.nightlyRate,
                                taxAmount = rate.taxAmount,
                            )
                        },
                    )
                }
            }

        return ResponseEntity.ok(AAvailabilityResponse(items))
    }

    /** ERROR/NO_RESPONSE 모드 처리. null이면 정상 응답을 계속 진행해도 된다는 뜻. */
    private fun errorResponse(): ResponseEntity<Any>? {
        return when (modeStore.get("a")) {
            MockMode.NORMAL -> null
            MockMode.ERROR ->
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(AErrorResponse("SERVICE_UNAVAILABLE", "Supplier A is temporarily unavailable"))
            MockMode.NO_RESPONSE -> {
                Thread.sleep(NO_RESPONSE_DELAY_MS)
                null
            }
        }
    }

    private fun datesBetween(checkIn: LocalDate, checkOut: LocalDate): List<LocalDate> {
        val nights = ChronoUnit.DAYS.between(checkIn, checkOut)
        return (0 until nights).map { checkIn.plusDays(it) }
    }
}
