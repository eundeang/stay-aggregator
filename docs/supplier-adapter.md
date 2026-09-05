# Supplier 연동 어댑터 설계

## 계층 구조

```
supplier/suppliera/SupplierADto.kt      A 전용 DTO — private, 이 파일 밖으로 안 나감
supplier/suppliera/SupplierAClient.kt   SupplierClient 구현체. DTO를 즉시 변환해서 반환
supplier/supplierb/SupplierBDto.kt      B 전용 DTO
supplier/supplierb/SupplierBClient.kt   SupplierClient 구현체

domain/SupplierClient.kt                포트 + 공급사 중립 값 객체 (SupplierHotel,
                                         SupplierOffer, SupplierAvailabilityResult 등)

application/StaySearchService.kt        SupplierClient 목록을 병렬 호출 → mapping
                                         테이블로 내부 식별자 resolve → 최종 도메인
                                         모델(Stay, RoomTypeOffer)로 조립
```

**경계 원칙**: `SupplierADto.kt`, `SupplierBDto.kt`에 정의된 클래스는 각자의
`SupplierAClient.kt` / `SupplierBClient.kt` 밖으로 절대 나가지 않는다.
`SupplierClient` 인터페이스가 반환하는 타입(`SupplierHotel`, `SupplierOffer` 등)은
A/B 어느 쪽 이름도 쓰지 않는 **공급사 중립 이름**을 쓴다 (예: `hotelCode`가 아니라
`externalHotelCode`).

## 왜 도메인 모델을 바로 안 만들고 중간 타입(`SupplierOffer`)을 두는가

`SupplierClient`는 내부 식별자(매핑 결과)를 모른다 — 매핑은 DB에 있고 조회는
Application 계층의 책임이다. 그래서 어댑터는 **외부 코드 기준**의 중립 값 객체까지만
만들고, 그걸 내부 식별자로 바꿔 최종 `Stay`/`RoomTypeOffer`(`docs/domain-model.md`에서
설계한 도메인 모델)로 조립하는 건 `StaySearchService`가 담당한다.

재고 판정(기간 내 최솟값 계산)도 어댑터가 아니라 이 조립 단계에서 수행한다 — 어댑터는
"공급사 응답을 있는 그대로 중립화"하는 역할까지만, 비즈니스 규칙(min 계산, 세금
포함 총액 계산 등 도메인 모델 설계 문서의 결정들)은 그 위 계층이 담당한다는 원칙.

## 실패 판정 통일

A(HTTP 상태 코드)와 B(HTTP 200 + `resultCode`)를 각 어댑터 내부에서 아래처럼 동일한
`SupplierFailureReason`으로 변환한다.

| 상황 | Supplier A | Supplier B | → SupplierFailureReason |
|---|---|---|---|
| 잘못된 요청 | HTTP 400 | resultCode E400 | INVALID_REQUEST |
| 인증 실패 | HTTP 401 | resultCode E401 | AUTH_FAILED |
| 호출 한도 초과 | HTTP 429 | resultCode E429 | RATE_LIMITED |
| 공급사 내부 오류 | HTTP 500 | resultCode E500 | SUPPLIER_ERROR |
| 일시적 장애 | HTTP 503 | resultCode E503 | SUPPLIER_ERROR |
| 무응답/응답 지연 | WebClient 타임아웃 예외 | 동일 | TIMEOUT |
| (공급사 응답과 무관 — 우리 시스템 내부 사유) | 이 공급사의 매핑이 비어있어 조회 자체를 시도 못 함 | 동일 | NO_MAPPING_DATA |

`NO_MAPPING_DATA`는 위 6가지와 달리 **어댑터가 판정하는 게 아니라
`StaySearchService`가 판정**한다 — 공급사를 호출하기도 전에, 매핑 테이블에
그 공급사의 숙소 코드가 하나도 없다는 걸 이미 알고 있기 때문. 근거:
`docs/architecture.md` "매핑 없는 공급사 처리".

호출부(`StaySearchService`)는 A인지 B인지 전혀 몰라도 되고, `SupplierAvailabilityResult`
가 `Success`인지 `Failure`인지, `Failure`면 `reason`이 뭔지만 보고 판단한다.

## 신규 Supplier 추가 시 고쳐야 하는 것 / 안 고쳐도 되는 것

**고쳐야 하는 것**
1. `supplier/supplierc/SupplierCDto.kt` — 신규 공급사 전용 DTO 작성
2. `supplier/supplierc/SupplierCClient.kt` — `SupplierClient` 구현, DTO → 중립 값
   객체 변환 + 실패 판정을 `SupplierFailureReason`으로 통일
3. `SupplierCode` enum에 `SUPPLIER_C` 추가
4. `WebClientConfig`에 C용 `WebClient` 빈 추가 (base-url, 타임아웃)
5. `application.yml`에 C의 접속 정보 추가
6. Spring이 `List<SupplierClient>`를 자동으로 주입하도록 `@Component`만 붙이면
   `StaySearchService`는 수정 없이 자동으로 C를 병렬 호출 대상에 포함

**고치지 않아도 되는 것**
- `domain/` 전체 (Stay, RoomTypeOffer, Price 등 표준 모델)
- `application/StaySearchService.kt` (SupplierClient 목록을 순회하는 로직은
  공급사 개수·종류에 무관하게 동작)
- `mapping/` 전체 (매핑 테이블은 `supplier` 컬럼 값만 다르게 저장될 뿐 스키마 변경 없음)
- `web/StaySearchController.kt`

이게 가능한 이유는 `StaySearchService`가 특정 공급사를 이름으로 알지 못하고
`List<SupplierClient>`(Spring이 `@Component`로 등록된 모든 구현체를 자동 주입)만
알기 때문이다.

## 신규 공급사가 "제대로" 만들어졌는지 검증: 공통 계약 테스트 하네스

위 절이 "배선"(신규 구현체가 자동으로 시스템에 편입되는 것)을 다룬다면, 이
절은 "그 구현체가 실제로 올바르게 동작하는지"를 다룬다. 배선은 잘 되지만
구현이 틀린 경우(예: 실패 판정 하나를 빼먹거나, `CancellationException`을
삼켜버리거나) 아무것도 자동으로 잡아주지 않는다는 게 문제였다 — 리뷰어가
눈으로 봐야만 알 수 있었다.

`src/test/kotlin/.../supplier/SupplierClientContract.kt`의
`supplierClientContract(label, newClient, enqueueFailure)`가 이 하네스다.
`SupplierClient` 구현체라면 누구나 지켜야 하는 계약(아래)을 정의해두고, 각
공급사 테스트는 "이 공급사가 이 상황을 어떻게 표현하는지"(`newClient`,
`enqueueFailure`)만 채워 넣으면 나머지는 자동으로 검증된다.

**하네스가 검증하는 것** (신규 공급사 추가 시 자동으로 확인됨)
- 실패 판정 5종(잘못된 요청/인증 실패/호출 한도 초과/내부 오류/일시적 장애)이
  전부 올바른 `SupplierFailureReason`으로 매핑되는가
- 무응답 시 `TIMEOUT`으로 분류되는가
- 요청에 `X-Api-Key` 헤더를 포함하는가

**하네스가 검증하지 않는 것** (공급사마다 다른, 각자의 테스트에서 검증)
- 숙소 목록/재고·요금 응답의 필드 파싱(각 공급사 DTO 구조가 다르므로)
- `breakfastIncluded` 위치, 요금 계산 방식(A: 합산, B: 그대로) 등 응답 구조 특이사항

신규 공급사(예 Supplier C) 추가 시 `SupplierCClientTest`에서
`supplierClientContract(...)`를 호출하기만 하면, 위 계약을 빠뜨렸는지 즉시
테스트 실패로 드러난다 — 실제로 `SupplierAClient`의 401 판정을 일부러
깨뜨려서 하네스가 잡아내는 것까지 확인했다(JOURNAL.md 참고).