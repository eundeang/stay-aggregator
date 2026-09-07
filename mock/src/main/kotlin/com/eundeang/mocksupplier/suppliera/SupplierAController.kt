package com.eundeang.mocksupplier.suppliera

import com.eundeang.mocksupplier.ModeStore
import com.eundeang.mocksupplier.MockMode
import com.eundeang.mocksupplier.NO_RESPONSE_DELAY_MS
import com.eundeang.mocksupplier.datesBetween
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(name = "Supplier A", description = "요구사항 문서의 Supplier A 재현")
@RestController
@RequestMapping("/a/v1")
class SupplierAController(private val modeStore: ModeStore) {

    @Operation(summary = "숙소 목록 (①)", description = "파라미터 없음. 현재 모드(/control/a/mode)와 무관하게 항상 정상 응답.")
    @GetMapping("/hotels")
    fun hotels(): ResponseEntity<Any> {
        errorResponse()?.let { return it }
        return ResponseEntity.ok(AHotelsResponse(SupplierAData.hotels))
    }

    @Operation(summary = "재고·요금 (②)", description = "현재 모드(normal/error/no-response/delay)에 따라 응답이 달라진다.")
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
            MockMode.DELAY -> {
                Thread.sleep(modeStore.getDelaySeconds("a") * 1000)
                null
            }
        }
    }
}
