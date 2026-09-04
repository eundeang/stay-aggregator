# Progress Journal

## Day 1 - 프로젝트 초기 설정 및 도메인 모델 설계 착수

### 수행 내용
- 기술 스택 결정, 패키지 구조 설계, 매핑 테이블(hotel_mapping/room_type_mapping)
  스키마 설계·구현

### 의사결정

- **DB: MySQL (Docker Compose)** — H2도 검토했으나, 인메모리 DB는 세팅은
  간단해도 실제 운영 환경과의 괴리가 있어 기각.

- **패키지 구조: 포트-어댑터(단순화 버전)** — `domain.SupplierClient` 인터페이스를
  포트로 두고 `supplier/{공급사}/`가 구현체로 붙는 구조. 완전한 헥사고날
  (`adapter/in`/`adapter/out` 풀 네이밍)도 검토했으나 7일 과제 규모 대비
  과하다고 판단해 기각 — "신규 Supplier 추가 시 무엇을 고쳐야 하는지"가
  명확해지는 최소 구조(인터페이스+구현체 분리)만 채택. 기술 계층형
  (`controller/service/repository/dto`)도 검토했으나 공급사가 늘수록 `dto/`
  밑에 A/B가 뒤섞이는 문제가 있어 기각.

- **매핑 테이블 설계**:
  - `hotel_mapping`: `UNIQUE(supplier, external_hotel_code)` — 동시성
    문제(매핑 갱신이 겹쳐 돌 경우)에도 DB 레벨에서 유일성을 보장하기 위함.
  - 내부 식별자를 surrogate key(Long)로 먼저 설계했다가, 자연키
    `(supplier, external_hotel_code)`로 재설계 — 서로 다른 값이 이미
    유일성을 보장하는데 의미 없는 대리키를 얹으면 로그/디버깅 중 역추적이
    한 단계 더 필요해진다고 판단해 뒤집음.
  - 공급사가 다르면 내부 식별자를 공유하지 않는 것을 기본 동작으로 삼음 —
    중복 상품 병합(숙소명 유사도 등)은 별도 설계 과제라 1차 범위에서 제외.

- **스키마 관리: Flyway (`ddl-auto: validate`)** — 자동 스키마 생성은 "왜
  이렇게 스키마를 바꿨는지"가 코드에 안 남음. 마이그레이션 파일 단위로
  관리해 스키마 변경 자체가 커밋 히스토리로 추적되게 함.

- **표준 모델 요금 필드**: 총액 필수(gross) + 일자별 단가 선택(net, null 가능)
  — 근거는 `docs/domain-model.md` "요금" 참고.

### AI 활용
- 패키지 구조·스키마·요금 필드 등 주요 설계는 Claude가 제시한 대안들의
  트레이드오프를 검토한 뒤 직접 채택 여부를 판단 (Claude 제안을 그대로
  받지 않고 근거를 따져 확정하는 방식으로 진행).

---

## Day 2 - Supplier A/B 어댑터 구현

### 수행 내용
- 표준 모델·포트·스펙 문서화(`docs/domain-model.md`, `docs/supplier-adapter.md`,
  `docs/supplier-api-spec.md`), Supplier A/B 어댑터(DTO+Client) 구현,
  MockWebServer 기반 테스트

### 의사결정

- **스펙 문서 확인 없이 구현 → 전면 재작성**: Mock 응답을 실제 스펙
  (`docs/supplier-api-spec.md`) 확인 없이 먼저 구현했다가, 실제 구조(숙소×
  객실타입 조합당 1행의 flat 구조)와 다르게 중첩 구조로 만든 걸 뒤늦게
  발견해 Mock/DTO/어댑터 매핑을 전부 재작성. 이후 "문서 먼저 확인, 그대로
  구현" 원칙을 스스로 교정.

- **Supplier B 실패 판정을 A와 통일**: B는 HTTP 200 + resultCode로만 실패를
  표현하는데, A의 4xx/5xx와 동일한 `SupplierFailureReason`으로 변환하는
  공통 타입(`SupplierAvailabilityResult`)을 둬서 호출부가 공급사별 실패
  표현 차이를 몰라도 되게 함.

- **`calculateAvailableRooms` 빈 리스트 → 예외**: 스펙에 없는 케이스라
  임의로 0을 반환하지 않고 `IllegalArgumentException`으로 처리 — 조용히
  0을 반환하면 "재고 0"과 "입력 오류(빈 기간 조회)"가 구분 안 되어 버그를
  숨기게 된다는 게 근거.

- **`MappingSyncService` 테스트: 실제 MySQL(H2 아님)** — Day 1의 DB 결정과
  일관되게 `@DataJpaTest`+`@AutoConfigureTestDatabase(NONE)`으로 임베디드
  DB 대체 없이 검증. 이 과정에서 TDD가 실제 버그 2건을 구현 전에 잡음 —
  (1) 매번 새 엔티티로 저장해 두 번째 동기화 시 유니크 제약 위반 (2) 기존
  레코드를 찾고도 필드를 갱신하지 않아 변경분이 반영 안 됨.

### AI 활용
- Mock/어댑터 구조를 스펙 문서 없이 먼저 설계·구현했다가, 사용자가 스펙
  문서 위치를 알려준 뒤에야 구조 불일치를 발견 — 문서 우선 확인 원칙이
  왜 필요한지 시행착오로 체감.

---

## Day 3 - 매핑 동기화 트리거·확장성 설계

### 수행 내용
- 매핑 생성 트리거 시점 설계, 대규모(전국 3~5만 건 가정) 대비 upsert
  확장성 검토 및 리팩터

### 의사결정

- **매핑 생성 트리거: 앱 기동 시 1회(블로킹) + 수동 재동기화** — 검색 요청
  시점 트리거(동기화 비용이 첫 검색 사용자에게 전가, 동시 요청 시 락 필요),
  지연 로딩(검색 API에 필터가 없어 "일부 조회"라는 개념이 성립 안 함),
  배포 시점 시드(신규 숙소 반영 경로가 없어짐), 외부 배치(인프라 복잡도가
  과제 규모에 안 맞음), webhook 구독(스펙에 없음) 등을 검토 후 전부 기각.
  상세 비교는 `docs/architecture.md` "매핑 생성 트리거" 참고.

- **매핑 upsert: 네이티브 멀티로우 upsert(JdbcTemplate)** — 트리거 규모를
  전국 숙박업소(약 3~5만 건) 기준으로 잡으면, 레코드당 SELECT+INSERT/UPDATE
  방식은 약 35만 회 DB 왕복이 발생함을 계산으로 확인 — 트리거를 async로
  바꿔도 해결 안 되는 별개 병목이라 upsert 자체를 재설계. `INSERT ... ON
  DUPLICATE KEY UPDATE`를 청크(1000건)당 묶어 왕복을 약 50회로 줄이고,
  기존 유니크 제약에 신규/기존 판정을 위임. JPA 배치(`saveAll`) 대비 이점과
  Spring Batch를 채택하지 않은 근거(멱등 연산이라 재시작 체크포인트 이득이
  적음)는 `docs/architecture.md` "매핑 배치 upsert" 참고.

- **[재검토] 수동 재동기화 실행 방식: async+상태 테이블 구현 보류** — 대규모
  대비로 `@Async`+상태 테이블 방향까지 검토했으나, 재검토 후 1차 범위에서는
  구현하지 않기로 결정. 이유: (a) 지금 Mock 데이터 규모(숙소 몇 개)로는
  검증이 안 되는 코드가 됨 (b) Supplier 어댑터·검색 API 등 우선순위가 더
  높은 항목이 남아있음. 매핑 동기화는 동기(블로킹) 방식 유지, "대규모 시
  전환 필요"라는 설계 판단만 문서화.

### AI 활용
- 트리거·upsert 설계는 Claude와 대안을 비교(비용 전가, 동시성, 인프라
  복잡도, DB 왕복 횟수 등 기준)한 뒤 사용자가 직접 채택.
- Async 구현은 Claude 제안을 한 번 받아들였다가, 사용자가 재검토 후
  우선순위(Supplier 어댑터·검색 API 우선)를 스스로 재조정해 구현 범위를
  축소 — Claude 제안을 그대로 따르지 않고 사용자 판단으로 뒤집은 사례.

### 참고 자료
- `docs/architecture.md` "매핑 생성 트리거", "매핑 배치 upsert"
