package com.eundeang.aggregator.mapping

import com.eundeang.aggregator.domain.SupplierCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * `id`는 실제 발급되는 내부 숙소 ID(surrogate PK) — API 응답의 hotelId로 노출되는 값.
 * (supplier, externalHotelCode)는 공급사 원본 식별자로, UNIQUE 제약(V2 마이그레이션)만
 * 걸려 있다. 근거: docs/architecture.md "매핑 테이블".
 */
@Entity
@Table(name = "hotel_mapping")
class HotelMapping(
    @Enumerated(EnumType.STRING)
    @Column(name = "supplier", nullable = false, length = 20)
    val supplier: SupplierCode,
    @Column(name = "external_hotel_code", nullable = false, length = 100)
    val externalHotelCode: String,
    @Column(name = "hotel_name", nullable = false)
    var hotelName: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
