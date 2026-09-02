---
name: commit-convention
description: Use this skill whenever creating a git commit in this repository — running `git commit`, staging changes for commit, or being asked to "commit" work. Defines commit message format, type prefixes, and project-specific rules (e.g. no company/assignment keywords, one concern per commit).
---

# 커밋 컨벤션

## 기본 형식

```
<type>: <subject>

<body (선택)>
```

- `subject`는 50자 이내, 명령형으로 작성 ("~한다" 톤, "~했음" 금지)
- 제목 끝에 마침표 없음
- 본문이 필요하면 빈 줄 하나 띄우고 작성 (왜 이렇게 했는지 위주로, 무엇을 했는지는 diff로 보임)

## Type 목록

| type | 용도 |
|---|---|
| `chore` | 빌드 설정, 의존성, 프로젝트 초기 설정 등 |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변화 없는 구조 개선 |
| `docs` | README, docs/, JOURNAL 등 문서 |
| `test` | 테스트 코드 추가/수정 |
| `design` | 도메인 모델·API 설계 관련 (문서 또는 스켈레톤 코드) |

## 이 프로젝트에서의 원칙

1. **의미 단위로 자주 커밋한다.** "하루치 작업"을 한 커밋에 몰아넣지 않는다. 예: 매핑 엔티티 작성과 매핑 저장 로직은 커밋을 나눈다.
2. **시행착오를 숨기지 않는다.** 잘못된 접근을 되돌리는 커밋(`revert:` 또는 `fix:`)도 그대로 남긴다. 히스토리를 깔끔하게 보이려고 squash하지 않는다.
3. **의사결정이 담긴 커밋은 본문에 근거를 남긴다.** 특히 다음 항목들:
    - 표준 모델에서 무엇을 버렸는지 (예: Supplier B의 조식 정보를 어떻게 반영했는지)
    - 매핑 캐시 갱신 시점을 왜 그렇게 정했는지
    - 타임아웃 값을 왜 그렇게 잡았는지
    - Supplier B의 실패 판정(HTTP 200 + resultCode)을 A와 어떻게 통일했는지
4. **AI 활용은 커밋 메시지에도 남길 수 있다.** 예: 본문 하단에 짧게 `(AI 초안 생성 후 필드 검증 로직 직접 추가)` 등. 강제는 아니지만 JOURNAL.md에 없으면 여기라도 남기는 걸 권장.
5. **회사명·과제 관련 키워드 금지.** 커밋 메시지에 "여기어때", "과제", "채용", "코딩 테스트" 등 언급하지 않는다.

## 예시

```
chore: 프로젝트 초기 설정 (Spring Boot, Gradle, MySQL)

Kotlin + Gradle Kotlin DSL로 초기화.
DB는 로컬 개발 편의보다 실전 환경에 가까운 MySQL을 Docker Compose로 구성.
```

```
design: 통합 숙박 상품 도메인 모델 1차 설계

Supplier A(1박 단가+세금 별도)와 B(총액+세금 포함)의 요금 표현 차이를
흡수하기 위해 내부 모델은 '숙박 기간 총액'만 표준으로 삼고
일자별 단가는 선택 필드로 둠. 근거는 docs/domain-model.md 참고.
```

```
feat: Supplier B 실패 판정을 A와 통일

B는 HTTP 200을 반환하며 resultCode로만 실패를 알리므로,
공통 SupplierResult 타입으로 변환하는 시점에 resultCode != "0000"을
예외로 승격시켜 A의 4xx/5xx와 동일하게 처리되도록 함.
```

## 금지 사항

- `WIP`, `임시`, `수정` 등 의미 없는 메시지
- 여러 관심사를 한 커밋에 묶기 (예: 매핑 로직 + 검색 API를 한 커밋에)