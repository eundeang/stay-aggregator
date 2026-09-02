package com.eundeang.aggregator.mapping

import org.springframework.data.jpa.repository.JpaRepository

interface HotelMappingRepository : JpaRepository<HotelMapping, Long> {
    fun findBySupplierAndExternalHotelCode(supplier: SupplierCode, externalHotelCode: String): HotelMapping?
}
