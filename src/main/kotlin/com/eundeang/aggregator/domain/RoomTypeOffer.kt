package com.eundeang.aggregator.domain

/**
 * 검색 시점 값. 매핑 테이블(RoomTypeMapping)의 고정 속성(이름, 정원)과
 * 공급사 응답의 조회 시점 속성(조식, 재고, 요금)을 합친 것.
 */
data class RoomTypeOffer(
    val roomTypeId: Long,
    val roomTypeName: String,
    val maxOccupancy: Int,
    val breakfastIncluded: Boolean,
    val availableRooms: Int,
    val price: Price,
)
