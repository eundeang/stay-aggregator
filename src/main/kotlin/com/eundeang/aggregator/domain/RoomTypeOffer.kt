package com.eundeang.aggregator.domain

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 검색 시점 값. 매핑 테이블(RoomTypeMapping)의 고정 속성(이름, 정원)과
 * 공급사 응답의 조회 시점 속성(조식, 재고, 요금)을 합친 것.
 */
@Schema(description = "객실 타입 하나의 검색 결과")
data class RoomTypeOffer(
    @Schema(description = "내부 객실 타입 ID")
    val roomTypeId: Long,
    @Schema(description = "객실 타입명")
    val roomTypeName: String,
    @Schema(description = "최대 수용 인원")
    val maxOccupancy: Int,
    @Schema(description = "조식 포함 여부 — 매핑 고정 속성이 아니라 조회 시점 값")
    val breakfastIncluded: Boolean,
    @Schema(description = "예약 가능 객실 수 (0이면 매진, 검색 기간 내 최솟값)")
    val availableRooms: Int,
    @Schema(description = "요금 정보")
    val price: Price,
)
