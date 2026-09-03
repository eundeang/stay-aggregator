package com.eundeang.aggregator.mock.supplierb

import com.eundeang.aggregator.mock.ModeStore
import com.eundeang.aggregator.mock.MockMode
import com.eundeang.aggregator.mock.NO_RESPONSE_DELAY_MS
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
        return BPropertiesResponse(OK_CODE, "OK", BPropertiesData(SupplierBData.properties))
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
                        roomId = room.roomId,
                        totalPrice = rate.pricePerNight * dates.size,
                        taxIncluded = true,
                        inventory = dates.map { date -> BInventoryDay(date.toString(), rate.remainingRooms) },
                    )
                }
            }

        return BSearchResponse(OK_CODE, "OK", BSearchData(items))
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
