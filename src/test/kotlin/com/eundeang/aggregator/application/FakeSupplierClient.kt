package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.SupplierAvailabilityResult
import com.eundeang.aggregator.domain.SupplierClient
import com.eundeang.aggregator.domain.SupplierCode
import com.eundeang.aggregator.domain.SupplierHotel
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 테스트 전용 SupplierClient. fetchHotels()는 생성 시 넘긴 목록을 그대로 반환하고,
 * fetchAvailability() 호출마다 어떤 코드로 호출됐는지 [calls]에 기록한 뒤 [availabilityHandler]로 위임한다.
 * 각 테스트는 필요한 쪽만 채우면 된다 — 매핑 동기화 테스트는 hotels만, 검색 테스트는 trailing lambda만.
 */
class FakeSupplierClient(
    override val supplier: SupplierCode,
    private val hotels: List<SupplierHotel> = emptyList(),
    private val availabilityHandler: (List<String>) -> SupplierAvailabilityResult = {
        throw UnsupportedOperationException("이 테스트에서는 사용되지 않음")
    },
) : SupplierClient {
    val calls: MutableList<List<String>> = CopyOnWriteArrayList()

    override suspend fun fetchHotels(): List<SupplierHotel> = hotels

    override suspend fun fetchAvailability(
        externalHotelCodes: List<String>,
        checkIn: LocalDate,
        checkOut: LocalDate,
        adults: Int,
        children: Int,
    ): SupplierAvailabilityResult {
        calls.add(externalHotelCodes)
        return availabilityHandler(externalHotelCodes)
    }
}
