package com.eundeang.aggregator.mapping

import org.springframework.data.jpa.repository.JpaRepository

interface HotelMappingRepository : JpaRepository<HotelMapping, HotelMapping.Id>
