package com.eundeang.mocksupplier.supplierb

import com.eundeang.mocksupplier.ModeStore
import com.eundeang.mocksupplier.MockMode
import com.eundeang.mocksupplier.NO_RESPONSE_DELAY_MS
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val ERROR_CODE = "E503"
private const val OK_CODE = "0000"

@RestController
@RequestMapping("/b/api")
class SupplierBController(private val modeStore: ModeStore) {

    @GetMapping("/properties")
    fun properties(): BPropertiesResponse {
        applyDelay()
        if (modeStore.get("b") == MockMode.ERROR) {
            return BPropertiesResponse(ERROR_CODE, "Supplier B is temporarily unavailable", null)
        }
        return BPropertiesResponse(OK_CODE, "SUCCESS", BPropertiesData(SupplierBData.properties))
    }

    @GetMapping("/search")
    fun search(
        @RequestParam propertyIds: String,
        @RequestParam checkIn: String,
        @RequestParam checkOut: String,
        @RequestParam(required = false) adults: Int?,
        @RequestParam(required = false) children: Int?,
    ): BSearchResponse {
        applyDelay()
        if (modeStore.get("b") == MockMode.ERROR) {
            return BSearchResponse(ERROR_CODE, "Supplier B is temporarily unavailable", null)
        }

        val ids = propertyIds.split(",").map { it.trim() }
        val dates = datesBetween(LocalDate.parse(checkIn), LocalDate.parse(checkOut))

        val items = SupplierBData.properties
            .filter { it.propertyId in ids }
            .flatMap { property ->
                property.rooms.map { room ->
                    val rate = SupplierBData.rates.getValue(room.roomId)
                    BSearchItem(
                        propertyId = property.propertyId,
                        propertyName = property.propertyName,
                        roomId = room.roomId,
                        roomName = room.roomName,
                        maxOccupancy = room.maxOccupancy,
                        breakfastIncluded = rate.breakfastIncluded,
                        currency = SupplierBData.CURRENCY,
                        totalPrice = rate.pricePerNight * dates.size,
                        taxIncluded = true,
                        inventory = dates.mapIndexed { index, date ->
                            BInventoryDay(date.toString(), rate.remainingRoomsCycle[index % rate.remainingRoomsCycle.size])
                        },
                    )
                }
            }

        return BSearchResponse(OK_CODE, "SUCCESS", BSearchData(items))
    }

    private fun applyDelay() {
        if (modeStore.get("b") == MockMode.NO_RESPONSE) {
            Thread.sleep(NO_RESPONSE_DELAY_MS)
        }
    }

    private fun datesBetween(checkIn: LocalDate, checkOut: LocalDate): List<LocalDate> {
        val nights = ChronoUnit.DAYS.between(checkIn, checkOut)
        return (0 until nights).map { checkIn.plusDays(it) }
    }
}
