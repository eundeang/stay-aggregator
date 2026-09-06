package com.eundeang.aggregator.mapping

import com.eundeang.aggregator.domain.SupplierCode
import org.springframework.data.jpa.repository.JpaRepository

interface HotelMappingRepository : JpaRepository<HotelMapping, Long> {
    fun findBySupplierAndExternalHotelCode(
        supplier: SupplierCode,
        externalHotelCode: String,
    ): HotelMapping?
}
