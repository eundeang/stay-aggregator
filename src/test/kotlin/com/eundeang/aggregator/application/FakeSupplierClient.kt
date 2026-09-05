package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.SupplierAvailabilityResult
import com.eundeang.aggregator.domain.SupplierClient
import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierHotel
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/** 테스트 전용 SupplierClient. fetchAvailability 호출마다 어떤 코드로 호출됐는지 기록한다. */
class FakeSupplierClient(
    override val supplier: SupplierCode,
    private val handler: (List<String>) -> SupplierAvailabilityResult,
) : SupplierClient {
    val calls: MutableList<List<String>> = CopyOnWriteArrayList()

    override suspend fun fetchHotels(): List<SupplierHotel> = throw UnsupportedOperationException("이 테스트에서는 사용되지 않음")

    override suspend fun fetchAvailability(
        externalHotelCodes: List<String>,
        checkIn: LocalDate,
        checkOut: LocalDate,
        adults: Int,
        children: Int,
    ): SupplierAvailabilityResult {
        calls.add(externalHotelCodes)
        return handler(externalHotelCodes)
    }
}
