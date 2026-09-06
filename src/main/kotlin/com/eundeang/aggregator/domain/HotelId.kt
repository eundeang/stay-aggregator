package com.eundeang.aggregator.domain

/**
 * 공급사 원본 기준 숙소 식별자 — (supplier, externalHotelCode). 공급사 응답을
 * 내부 매핑(HotelMapping)에 연결하는 조회 키로만 쓰인다. 실제 API에 노출되는
 * 내부 숙소 ID는 `HotelMapping.id`(Long) — 근거: docs/architecture.md "매핑 테이블".
 */
data class HotelId(
    val supplier: SupplierCode,
    val externalHotelCode: String,
)
