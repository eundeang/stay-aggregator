# 숙박 상품 통합 모델 설계

Supplier A·B의 표현 차이를 흡수하는 표준 모델. 각 판단의 근거를 기록.

## 숙소 / 객실 단위

- **숙소**: `(supplier, external_hotel_code)` 단위. mapping 테이블과 동일한 단위.
- **객실**: 물리 객실이 아닌 "객실 타입" 단위. 두 공급사 모두 타입 단위로만 요금·재고를
  주므로 자연스럽게 결정됨.
- **조식 포함 여부(`breakfastIncluded`)는 매핑 테이블에 저장하지 않는다.** 두 공급사
  모두 이 값을 숙소 목록 API(①, 정적)가 아니라 재고·요금 API(②, 매 조회마다 값이
  바뀔 수 있음)에서만 제공한다. 즉 "객실 타입의 고정 속성"이 아니라 "그 시점 요금에
  딸린 속성"으로 취급 — 검색 결과 아이템(`RoomTypeOffer`)에만 존재하고 영속화하지
  않는다.

## 요금

**결정: 총액(세금 포함) 필수 + 일자별 단가는 선택(null 가능)**

| | Supplier A | Supplier B |
|---|---|---|
| 원본 단위 | 일자별 단가(net) + taxAmount | 숙박 전체 총액(gross) |
| 표준 모델 매핑 | `Σ(nightlyRate+taxAmount)`로 총액 계산, `nightlyRates`에 원본 그대로 채움 | `totalPrice` 그대로 사용, `nightlyRates`는 `null` |

**근거**
- 두 공급사가 100% 채울 수 있는 값은 "총액(세금 포함)"뿐이다. 이걸 필수 필드로 삼아야
  모든 상품이 결측 없이 비교 가능해진다.
- 일자별 단가는 B에 원본 데이터가 없다. 억지로 `totalPrice / 박수`로 나눠 추정할 수도
  있지만, 이는 실제 값이 아닌 계산값을 A의 실측값과 같은 필드에 섞는 셈이 되어 신뢰도
  문제가 생긴다. **정보를 조작하지 않고 있는 그대로(없으면 null) 노출**하는 쪽을
  택함 — 이것이 "무엇을 잃는지 알고 선택했는지"에 대한 답.
- 대신 모델의 표준 `totalAmount`는 **항상 세금 포함(gross) 금액**으로 통일한다. A는
  계산해서 맞추고, B는 원본이 이미 그 형태라 그대로 쓴다. 표준 모델에서
  `taxIncluded`는 (구현 범위에서는) 항상 `true`로 취급.

## 일자별 단가의 세전/세후

**결정: net(세전) 그대로 유지, 필드명으로 명시**

`nightlyRates`는 필수 필드가 아니라 "있으면 보여주는" 부가 정보다. `totalAmount`처럼
고객이 실제로 결제할 금액이 아니므로, 굳이 세금을 더해 gross로 가공할 필요가 없다고
판단. 원본(A)이 net으로 주는 값을 그대로 전달하되, 필드명을 `nightlyNetAmount`로
명확히 해서 `totalAmount`(gross)와 성격이 다르다는 걸 이름만으로 알 수 있게 한다.

**근거**
- 가공할수록 "표준 모델이 원본 데이터를 왜곡했는지"를 나중에 설명해야 할 거리가
  늘어난다. 필수 필드(`totalAmount`)만 가공하고, 선택 필드는 원본 그대로 전달하는
  편이 일관된 원칙이다.
- 필드명에 세전/세후를 명시하면, 클라이언트가 이 값을 오해해서 총액과 단순 비교하는
  실수를 줄일 수 있다.

## 재고 / 예약 가능 객실 수

**결정: 기간 내 일별 `remainingRooms`의 최솟값(min)**

**근거**
- N박 검색에서 "이 객실 타입을 N박 전부 예약 가능한 수"는, 그 중 재고가 가장 적은
  날짜에 의해 제한된다. 예: 3박 중 하루만 재고 1개면 전체 숙박 가능 수는 1이다.
- 체크인일만 보거나 평균을 내면 실제로는 예약 불가능한 상품을 예약 가능하다고
  잘못 노출할 위험이 있다 (예: 체크인일 재고 5, 다른 날 0인데 체크인일만 보면
  "5개 가능"으로 잘못 표시됨).

## 예약 불가 상품 노출

**결정: 재고 0인 상품도 응답에 포함, `availableRooms: 0`으로 노출**

**근거**
- 프론트/클라이언트가 "이 방은 있지만 지금은 매진"이라는 정보를 활용할 수 있게
  하기 위함 (예: 매진 표시, 다른 날짜 추천 등). 아예 빼버리면 이 정보 자체가
  사라진다.
- 클라이언트가 필요하면 `availableRooms == 0`으로 필터링하면 되므로, 서버가
  선제적으로 정보를 제거하지 않는 편을 택함.

## 응답 구조

**결정: Nested — 숙소 하나에 객실 타입 배열**

```json
{
  "results": [
    {
      "hotelId": 1,
      "hotelName": "...",
      "sourceSupplier": "SUPPLIER_A",
      "roomTypes": [
        {
          "roomTypeId": 3,
          "roomTypeName": "...",
          "maxOccupancy": 2,
          "breakfastIncluded": false,
          "availableRooms": 3,
          "price": {
            "currency": "KRW",
            "totalAmount": 429000,
            "taxIncluded": true,
            "nightlyRates": [
              { "date": "2026-09-01", "nightlyNetAmount": 132000 },
              { "date": "2026-09-02", "nightlyNetAmount": 165000 },
              { "date": "2026-09-03", "nightlyNetAmount": 132000 }
            ]
          }
        }
      ]
    }
  ],
  "partialFailures": [
    { "supplier": "SUPPLIER_B", "reason": "TIMEOUT" }
  ]
}
```

**근거**
- 각 공급사 원본 응답은 flat(객실 타입 row마다 숙소 정보 중복)이라 flat이 변환은
  더 쉽지만, 클라이언트 입장에서 "숙소 하나 → 객실 타입 여러 개"를 다루는 게
  훨씬 자연스럽다. 서버 쪽 변환 비용을 조금 더 들이는 대신 클라이언트 편의를
  택함.
- `partialFailures`는 응답 최상위에 별도 배열로 둔다 — 개별 상품에 실패 여부를
  섞으면 "이 상품이 실패해서 없는 건지, 원래 없는 건지"가 모호해지므로 분리.

## Kotlin 도메인 모델 (초안)

```kotlin
data class Stay(
    val hotelId: Long,              // HotelMapping.id — 공급사 원본 코드는 노출 안 함
    val hotelName: String,
    val sourceSupplier: SupplierCode,
    val roomTypes: List<RoomTypeOffer>,
)

data class RoomTypeOffer(
    val roomTypeId: Long,              // RoomTypeMapping.id
    val roomTypeName: String,
    val maxOccupancy: Int,
    val breakfastIncluded: Boolean,    // 매핑 아님, 조회 시점 값
    val availableRooms: Int,           // min(daily remainingRooms), 0 가능
    val price: Price,
)

data class Price(
    val currency: String,
    val totalAmount: Long,             // 항상 세금 포함(gross)
    val taxIncluded: Boolean = true,
    val nightlyRates: List<NightlyRate>? = null,  // 없으면 null (B는 항상 null)
)

data class NightlyRate(
    val date: LocalDate,
    val nightlyNetAmount: Long,        // 세전(net). totalAmount(세후)와 성격이 다름을 명시
)
```