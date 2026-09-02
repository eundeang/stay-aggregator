# 프로젝트 개요

여러 숙박 공급사(A, B ...) 상품을 내부 표준 모델로 통합하는 Aggregator.
설계 근거(왜 이렇게 했는지)는 `docs/architecture.md` 참고.

## 스택

Kotlin / Spring Boot 3.4+ / Gradle(Kotlin DSL) / MySQL(Docker Compose) /
WebClient(Supplier 연동 전용, RestTemplate 금지) / Flyway(`ddl-auto: validate`)

## 패키지 구조 (포트-어댑터 단순화)

```
domain/       표준 모델 + SupplierClient 인터페이스(포트)
application/  StaySearchService — 병렬 호출·정규화·병합
supplier/{a,b}/  SupplierClient 구현체 + 공급사 전용 DTO
mapping/      공급사 코드 ↔ 내부 식별자 Entity/Repository
web/          StaySearchController
config/       WebClientConfig (타임아웃 등)
mock/         Mock Supplier (포트 9090, 별도 프로세스)
```

**규칙**
- 공급사 DTO는 `supplier/{공급사}/` 밖으로 나가지 않는다 (Client 구현체에서 즉시 domain으로 변환)
- 신규 공급사 추가 = `supplier/{new}/`에 구현체 1개 추가, 나머지 레이어 수정 금지
- 매핑 테이블엔 식별자만 저장. 요금/재고는 저장 안 함 (원본이 외부에 있음)

## DB

- 마이그레이션: `db/migration/V{n}__*.sql`
- `hotel_mapping`: UNIQUE(supplier, external_hotel_code)
- `room_type_mapping`: UNIQUE(hotel_mapping_id, external_room_type_code)
- 공급사가 다르면 내부 식별자 공유 안 함 (중복 병합은 미구현/선택)

## 로컬 실행

```bash
docker compose up -d   # MySQL: stay_aggregator / root/local
```
Mock Supplier는 앱과 다른 포트(9090)에서 별도 기동.

## 커밋

`.claude/skills/commit-convention/SKILL.md` 참조 (자동 로드됨, 여기서 재설명 안 함)

## Out of Scope

인증/인가, 결제, 관리자 기능, 프론트엔드, 실제 외부 API, 지역/키워드 필터, 정렬/페이징