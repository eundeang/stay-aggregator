---
name: tdd-workflow
description: Use this skill when implementing domain logic (pure calculation functions in the domain package, e.g. availability/price aggregation) or supplier adapter DTO-parsing/failure-classification logic — anywhere the expected behavior is already fully specified in docs/domain-model.md or docs/supplier-api-spec.md. Do not use this for StaySearchService orchestration logic or other code whose exact behavior is still being decided.
---

# TDD 워크플로우 (이 프로젝트 전용)

## 적용 범위를 먼저 판단한다

이 스킬은 **"입력과 기대 출력이 이미 문서에 확정돼 있는" 코드에만** 적용한다.

- **적용**: `docs/domain-model.md`에 계산 규칙이 명시된 함수 (예: N박 재고 판정,
  세금 포함 총액 계산), `docs/supplier-api-spec.md`에 명시된 응답을 파싱하는
  어댑터 로직, 실패 판정 통일(`SupplierFailureReason` 매핑)
- **미적용**: `StaySearchService`의 오케스트레이션(병렬 호출 조합, 부분 실패
  응답 조립)처럼 구현하면서 세부가 정해지는 코드. 이런 코드는 먼저 구현하고
  나서 통합 테스트로 검증한다.

애매하면 사용자에게 "이 부분은 TDD로 갈지, 구현 후 테스트로 갈지" 물어본다.

## Red-Green-Refactor 사이클

1. **Red**: 문서에 명시된 규칙 하나를 골라 실패하는 테스트를 먼저 작성한다.
   Kotest FunSpec의 `test("자연어로 된 설명")` 문자열로 테스트 의도를 명확히
   드러낸다 (예: `test("기간 내 최소 재고를 반환한다")`).
2. 테스트를 실행해서 **반드시 실패하는 것을 확인**한다 (컴파일 에러든 assertion
   실패든). 실패 확인 없이 구현으로 넘어가지 않는다.
3. **Green**: 테스트를 통과시키는 **최소한의 구현**만 작성한다. 아직 다루지
   않은 케이스를 미리 처리하려 하지 않는다.
4. **Refactor**: 테스트가 통과하는 상태를 유지하며 코드를 정리한다.
5. 다음 케이스(다음 규칙/엣지 케이스)로 이동해 반복.

## 테스트 도구

- 프레임워크: **Kotest, `FunSpec` 스타일**. 테스트 클래스는
  `class XxxTest : FunSpec({ ... })` 형태로 작성한다.
- 단언(assertion): Kotest matcher (`shouldBe`, `shouldContainExactly`,
  `shouldThrow<T> { ... }` 등). AssertJ나 JUnit assertion을 섞어 쓰지 않는다.
- 테스트 설명은 `test("설명")` 문자열에 자연어(한국어)로 작성 — 함수 이름 규칙
  대신 이 문자열이 테스트 의도를 설명한다.
- 케이스를 논리적으로 묶고 싶으면 `context("그룹 설명") { test(...) { } }`로
  중첩한다.
- 여러 입력/출력 조합을 한 번에 다룰 때는 `kotest-framework-datatest`의
  `withData`를 활용해 표 형태로 테스트를 표현한다 (특히 실패 판정 매핑표처럼
  "입력별 기대값"이 표로 정리된 경우에 적합).
- 어댑터 테스트: 9090 Mock Supplier를 띄우지 않고 `MockWebServer`(OkHttp)로
  고정 응답을 흉내낸다. 응답 JSON은 `docs/supplier-api-spec.md`의 예시를 그대로
  사용.
- 도메인 순수 함수 테스트: Spring 컨텍스트 없이 순수 Kotest로 (빠르게 유지)

### 예시 — 도메인 계산 함수 (일반 케이스 + 데이터 기반)

```kotlin
class AvailableRoomsCalculatorTest : FunSpec({

    test("기간 내 최소 재고를 반환한다") {
        val daily = listOf(
            DailyInventory(LocalDate.parse("2026-09-01"), 3),
            DailyInventory(LocalDate.parse("2026-09-02"), 1),
            DailyInventory(LocalDate.parse("2026-09-03"), 5),
        )
        calculateAvailableRooms(daily) shouldBe 1
    }

    context("경계 케이스") {
        test("모든 날짜 재고가 0이면 0을 반환한다") {
            val daily = listOf(
                DailyInventory(LocalDate.parse("2026-09-01"), 0),
                DailyInventory(LocalDate.parse("2026-09-02"), 0),
            )
            calculateAvailableRooms(daily) shouldBe 0
        }

        test("1박(단일 날짜)이면 그 날짜 재고를 그대로 반환한다") {
            val daily = listOf(DailyInventory(LocalDate.parse("2026-09-01"), 4))
            calculateAvailableRooms(daily) shouldBe 4
        }
    }
})
```

### 예시 — 어댑터 실패 판정 (표 기반 테스트, `withData`)

```kotlin
class SupplierAFailureMappingTest : FunSpec({

    data class Case(val httpStatus: Int, val expected: SupplierFailureReason)

    context("HTTP 상태 코드별 실패 판정") {
        withData(
            Case(400, SupplierFailureReason.INVALID_REQUEST),
            Case(401, SupplierFailureReason.AUTH_FAILED),
            Case(429, SupplierFailureReason.RATE_LIMITED),
            Case(500, SupplierFailureReason.SUPPLIER_ERROR),
            Case(503, SupplierFailureReason.SUPPLIER_ERROR),
        ) { (httpStatus, expected) ->
            mockWebServer.enqueue(MockResponse().setResponseCode(httpStatus))

            val result = supplierAClient.fetchAvailability(/* ... */)

            result.shouldBeInstanceOf<SupplierAvailabilityResult.Failure>()
            (result as SupplierAvailabilityResult.Failure).reason shouldBe expected
        }
    }
})
```

`withData`를 쓰면 `docs/supplier-adapter.md`의 매핑표를 거의 그대로 테스트
케이스로 옮길 수 있어서, "표에 있는데 테스트가 없는 조합"을 놓치기 어렵다.

## 케이스 선정 우선순위

문서에 있는 경계 조건을 빠짐없이 커버한다. 예를 들어 재고 판정이면:
1. 일반 케이스 (날짜마다 다른 값, 최솟값이 중간 날짜에 있음)
2. 최솟값이 0인 경우 (예약 불가)
3. 단일 날짜(1박) 케이스
4. 모든 날짜가 동일한 값인 경우

어댑터 실패 판정이면 `docs/supplier-adapter.md`의 매핑표에 있는 모든 조합
(A의 각 HTTP 상태 코드, B의 각 resultCode)을 케이스로 만든다 — 표에 있는데
테스트가 없는 조합이 없어야 한다.

## 커밋

테스트와 구현을 같은 커밋에 넣어도 되지만(작은 단위라면), 리팩터링이 별도로
의미 있으면 `test:`/`feat:`/`refactor:`로 분리해도 좋다. 커밋 컨벤션
(`.claude/skills/commit-convention/SKILL.md`)을 따른다.