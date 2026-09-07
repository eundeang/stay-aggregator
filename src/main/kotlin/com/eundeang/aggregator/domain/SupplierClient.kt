package com.eundeang.aggregator.domain

import java.time.LocalDate

/**
 * 모든 Supplier 어댑터가 구현하는 포트.
 * 반환 타입은 Supplier 고유 DTO가 아니라 "공급사 중립" 값 객체 — 이 인터페이스를
 * 넘어서는 순간 A/B의 요청/응답 형식은 완전히 사라진다.
 */
interface SupplierClient {
    val supplier: SupplierCode

    /** 숙소 목록 조회 (①, 정적 콘텐츠). 매핑 생성에 사용. */
    suspend fun fetchHotels(): List<SupplierHotel>

    /** 재고·요금 조회 (②). 실패도 정상 흐름으로 표현 — 예외를 던지지 않는다. */
    suspend fun fetchAvailability(
        externalHotelCodes: List<String>,
        checkIn: LocalDate,
        checkOut: LocalDate,
        adults: Int,
        children: Int,
    ): SupplierAvailabilityResult
}

/** 숙소 목록 항목 — 매핑 생성 전용, 요금/재고 없음 */
data class SupplierHotel(
    val externalHotelCode: String,
    val hotelName: String,
    val roomTypes: List<SupplierRoomType>,
)

data class SupplierRoomType(
    val externalRoomTypeCode: String,
    val roomTypeName: String,
    val maxOccupancy: Int,
)

/**
 * 재고·요금 조회 결과 — 성공/실패를 코드에서 항상 명시적으로 다루게 강제한다.
 * A의 HTTP 4xx/5xx와 B의 resultCode!=0000을 모두 Failure로 통일해서 반환하는 게
 * 이 타입의 핵심 존재 이유. 근거: docs/supplier-adapter.md "실패 판정 통일".
 */
sealed class SupplierAvailabilityResult {
    data class Success(
        val offers: List<SupplierOffer>,
    ) : SupplierAvailabilityResult()

    data class Failure(
        val reason: SupplierFailureReason,
        val message: String,
    ) : SupplierAvailabilityResult()
}

enum class SupplierFailureReason {
    TIMEOUT, // 무응답/응답 지연으로 타임아웃 발생
    SUPPLIER_ERROR, // 공급사 측 오류 (5xx, E500/E503 등)
    RATE_LIMITED, // 429 / E429
    INVALID_REQUEST, // 4xx / E400 (파라미터 오류 등, 재시도 무의미)
    AUTH_FAILED, // 401 / E401
    NO_MAPPING_DATA, // 이 공급사의 매핑이 비어있어 조회 자체를 시도하지 못함 — 공급사 응답과 무관한 우리 시스템 내부 사유
    UNKNOWN, // 위 분류에 안 맞는 경우
}

/** 요금·재고 한 건 — 외부 코드 기준. 아직 내부 식별자로 resolve 되지 않은 상태. */
data class SupplierOffer(
    val externalHotelCode: String,
    val externalRoomTypeCode: String,
    val breakfastIncluded: Boolean,
    val currency: String,
    val totalAmount: Long, // 항상 세금 포함(gross)으로 변환해서 반환
    val nightlyNetAmounts: List<NightlyNetAmount>?, // A만 값 있음, B는 null
    val dailyRemainingRooms: List<DailyInventory>, // 재고 판정(min)은 Application 계층에서
)

data class NightlyNetAmount(
    val date: LocalDate,
    val amount: Long,
)

data class DailyInventory(
    val date: LocalDate,
    val remainingRooms: Int,
)
