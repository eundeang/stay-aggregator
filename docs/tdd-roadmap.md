# TDD 구현 로드맵

전체 구현 항목을 "테스트 먼저 작성 가능(TDD)" / "구현 후 검증"으로 나눈다.
기준: 입력→출력 규칙이 이미 `docs/*.md`에 확정돼 있는가.

## Phase 1 — TDD 대상 (스펙 확정됨)

### 1-1. 도메인 계산 함수 ✅ 완료
- `calculateAvailableRooms(dailyRemainingRooms: List<DailyInventory>): Int`
  — 기간 내 최솟값. 근거: `docs/domain-model.md` "재고 / 예약 가능 객실 수"
- `calculateTotalAmount(nightlyRate, taxAmount 목록): Long` (Supplier A 전용 헬퍼)
  — `Σ(nightlyRate+taxAmount)`. 근거: `docs/supplier-api-spec.md` "요금 규약"
  — 구현 위치는 `supplier/suppliera`가 아니라 `domain`으로 결정 (순수 계산,
  근거는 JOURNAL.md Day 2 참고)
- 테스트 케이스: 일반 케이스, 최솟값 0(예약불가), 1박 단일 케이스, 전날짜 동일값

### 1-2. 매핑 생성/갱신 로직 (`MappingSyncService` 가칭)
- `syncHotels(supplier: SupplierCode, hotels: List<SupplierHotel>)`
    - 신규 외부 코드 → 새 `HotelMapping`/`RoomTypeMapping` 생성
    - 기존 외부 코드 재조회 → **같은 내부 식별자 유지**, `hotelName` 등 변경분만 갱신
    - 존재 여부 판정은 `(supplier, externalHotelCode)` 복합키 기준
- 근거: `docs/architecture.md` "같은 공급사 상품이 항상 같은 내부 식별자로
  매핑되는 것을 DB 레벨에서 보장"
- 테스트 케이스:
    - 신규 숙소 upsert 시 매핑 레코드가 생성되는가
    - 같은 외부 코드로 두 번 호출 시 레코드가 중복 생성되지 않고 갱신만 되는가
    - 숙소명이 바뀌면 `hotelName`만 갱신되고 식별자(PK)는 그대로인가
    - 객실 타입도 동일 원칙(숙소 안에서만 유일)으로 동작하는가
    - Repository는 실제 DB 대신 in-memory(fake) 또는 `@DataJpaTest`로 격리 테스트

### 1-3. Supplier A/B 어댑터
- 이미 상세 설계됨: `docs/supplier-adapter.md`, `docs/supplier-api-spec.md`
- 테스트 케이스: 이전 대화에서 정리한 실패 판정 매핑표 전체(A 6종, B 6종),
  정상 응답 파싱(`breakfastIncluded` 위치, `totalAmount` 계산, `nightlyNetAmounts`
  null 처리), 타임아웃 → `TIMEOUT` 매핑

## Phase 2 — 구현 후 검증 (스펙이 구현 중 확정됨)

### 2-1. StaySearchService
- 여러 `SupplierClient`를 어떻게 병렬 호출하고(coroutine `async`/`awaitAll` 등),
  실패한 공급사를 어떻게 `partialFailures`로 조립할지는 구현하며 결정
- 구현 후 통합 테스트로 검증: "A 성공+B 실패 시 A 결과만 응답에 포함되고
  partialFailures에 B가 기록되는가" 등

### 2-2. StaySearchController
- `GET /api/v1/stays/search` 요청/응답 계약 검증. `docs/domain-model.md`의
  응답 구조 예시 기준으로 통합 테스트 작성 (구현 후)

### 2-3. 연동 견고성 튜닝
- 타임아웃 판정 로직 자체는 Phase 1의 어댑터 테스트에 이미 포함됨
- 실제 타임아웃 "값"(3초? 5초?)은 Mock의 응답 지연 모드로 실측하며 조정 —
  이건 애초에 "테스트로 사전에 정의할 수 있는 값"이 아니라 실험적으로 정함

## 진행 순서

```
1-1 도메인 계산 함수  →  1-2 매핑 생성 로직  →  1-3 Supplier 어댑터
                                                        ↓
                            2-1 StaySearchService (Phase 1 결과물 조합)
                                                        ↓
                            2-2 컨트롤러  →  2-3 타임아웃 값 튜닝
```

1-1과 1-2는 서로 독립적이라 순서를 바꿔도 무방하지만, 1-3(어댑터)은 1-1의
계산 함수를 내부에서 쓰므로 그 뒤에 진행하는 게 자연스럽다.

## 각 Phase 1 항목 진행 방식

`.claude/skills/tdd-workflow/SKILL.md`의 Red-Green-Refactor 사이클을 따른다.
케이스 하나 → 실패 테스트 → 최소 구현 → 리팩터 → 다음 케이스, 순서로 진행하고
한 항목의 모든 케이스를 다 구현할 때까지는 다음 항목으로 넘어가지 않는다.

각 항목 완료 시 커밋 컨벤션(`.claude/skills/commit-convention/SKILL.md`)에 따라
`test:`/`feat:` 커밋을 남기고, `decision-logging` 스킬에 따라 JOURNAL.md에도
기록한다.