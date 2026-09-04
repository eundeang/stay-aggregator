package com.eundeang.aggregator.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.io.Serializable

/**
 * 숙소의 전역 식별자 — (supplier, externalHotelCode). mapping.HotelMapping이
 * 이 타입을 그대로 @EmbeddedId로 재사용해, 같은 개념을 표현하는 타입이
 * domain/mapping에 중복 존재하지 않게 한다.
 */
@Embeddable
data class HotelId(
    @Enumerated(EnumType.STRING)
    @Column(name = "supplier", length = 20)
    val supplier: SupplierCode,
    @Column(name = "external_hotel_code", length = 100)
    val externalHotelCode: String,
) : Serializable
