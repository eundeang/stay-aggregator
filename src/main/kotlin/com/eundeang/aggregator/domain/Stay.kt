package com.eundeang.aggregator.domain

/**
 * 표준 통합 모델 — 숙소 하나(공급사 기준) + 그 공급사가 이번 검색에 실제로
 * 반환한 객실 타입들. 근거: docs/domain-model.md "Kotlin 도메인 모델".
 *
 * `hotelId`는 공급사 원본 코드(`HotelId`)가 아니라 실제 발급된 내부 숙소 ID
 * (`HotelMapping.id`) — API 응답에 공급사 원본 식별자가 그대로 노출되지 않도록
 * 하기 위함. 근거: docs/architecture.md "매핑 테이블".
 */
data class Stay(
    val hotelId: Long,
    val hotelName: String,
    val sourceSupplier: SupplierCode,
    val roomTypes: List<RoomTypeOffer>,
)
