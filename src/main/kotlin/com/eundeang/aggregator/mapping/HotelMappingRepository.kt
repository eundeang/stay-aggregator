package com.eundeang.aggregator.mapping

import com.eundeang.aggregator.domain.HotelId
import org.springframework.data.jpa.repository.JpaRepository

interface HotelMappingRepository : JpaRepository<HotelMapping, HotelId>
