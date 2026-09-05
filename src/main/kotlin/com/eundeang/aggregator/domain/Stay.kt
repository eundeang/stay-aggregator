package com.eundeang.aggregator.domain

/**
 * 표준 통합 모델 — 숙소 하나(공급사 기준) + 그 공급사가 이번 검색에 실제로
 * 반환한 객실 타입들. 근거: docs/domain-model.md "Kotlin 도메인 모델".
 */
data class Stay(
    val hotelId: HotelId,
    val hotelName: String,
    val sourceSupplier: SupplierCode,
    val roomTypes: List<RoomTypeOffer>,
)
