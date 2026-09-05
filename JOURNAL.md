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
- `MappingSyncRunner`(`ApplicationRunner`) 구현 및 Mock Supplier+실제 앱
  기동으로 통합 검증(매핑 테이블에 A/B 공급사 데이터 upsert 확인)
- `docs/supplier-api-spec.md` 규약을 실제 구현과 대조 점검

### 의사결정

- **매핑 생성 트리거: 앱 기동 시 1회(블로킹)** — 검색 요청 시점 트리거
  (동기화 비용이 첫 검색 사용자에게 전가, 동시 요청 시 락 필요), 지연 로딩
  (검색 API에 필터가 없어 "일부 조회"라는 개념이 성립 안 함), 배포 시점
  시드(신규 숙소 반영 경로가 없어짐), 외부 배치(인프라 복잡도가 과제
  규모에 안 맞음), webhook 구독(스펙에 없음) 등을 검토 후 전부 기각. 상세
  비교는 `docs/architecture.md` "매핑 생성 트리거" 참고.
  - `MappingSyncRunner`(`ApplicationRunner`)로 구현. 공급사 하나의
    `fetchHotels()` 실패가 앱 전체 기동을 막지 않도록 공급사별로 예외를
    격리(로그만 남기고 다음 공급사로 진행) — `fetchAvailability`의 "실패도
    정상 흐름" 철학과 같은 방향.

- **매핑 upsert: 네이티브 멀티로우 upsert(JdbcTemplate)** — 트리거 규모를
  전국 숙박업소(약 3~5만 건) 기준으로 잡으면, 레코드당 SELECT+INSERT/UPDATE
  방식은 약 35만 회 DB 왕복이 발생함을 계산으로 확인 — 트리거를 async로
  바꿔도 해결 안 되는 별개 병목이라 upsert 자체를 재설계. `INSERT ... ON
  DUPLICATE KEY UPDATE`를 청크(1000건)당 묶어 왕복을 약 50회로 줄이고,
  기존 유니크 제약에 신규/기존 판정을 위임. JPA 배치(`saveAll`) 대비 이점과
  Spring Batch를 채택하지 않은 근거(멱등 연산이라 재시작 체크포인트 이득이
  적음)는 `docs/architecture.md` "매핑 배치 upsert" 참고.

- **[재검토] 수동 재동기화 엔드포인트: 검토 후 1차 범위에서 제외** — 대규모
  대비로 `@Async`+상태 테이블 실행 방식까지 검토했었으나, 안내 문서가
  "언제 호출할지 판단 + 근거"만 요구하고 있어 "앱 기동 시 1회"만으로도
  충분한 답이 된다고 재판단. 재동기화가 필요하면 앱 재시작이 사실상 같은
  역할을 하고, 이 과제 규모에서 별도 엔드포인트 구현의 실익이 크지 않다고
  보고 제외 — `MappingSyncController`는 만들었다가 삭제.

- **`X-Api-Key` 인증 헤더 누락 발견 및 추가** — 스펙 대조 점검 중, "공통 규약"에
  명시된 인증 헤더를 `WebClientConfig`/어댑터 어디에서도 보내지 않고 있었던
  걸 발견. `WebClientConfig`에 공급사별 `api-key`를 `application.yaml`에서
  읽어 `defaultHeader`로 설정하도록 수정. 기존 어댑터 테스트는 응답 파싱만
  검증하고 `server.takeRequest()`로 요청 자체를 검증한 적이 없어서 이 누락이
  안 걸렸던 것 — 두 어댑터 테스트에 "X-Api-Key 헤더를 포함한다" 케이스를
  추가해 요청 쪽 규약도 테스트로 고정.
  - 시행착오: 수정 후 전체 테스트를 돌리자 무관한 테스트(`MappingSyncServiceTest`,
    `MappingSyncRunnerTest`) 3건이 실패 — 원인은 이전에 수동 `bootRun`으로
    실제 dev MySQL에 커밋해둔 매핑 데이터가 남아있었고, 일부 테스트가
    `findAll().size`로 테이블 전체 상태를 단언해서 그 잔여 데이터와 충돌한
    것. `@DataJpaTest`의 트랜잭션 롤백은 테스트가 만든 데이터만 되돌릴 뿐,
    테스트 시작 전에 이미 커밋돼 있던 데이터는 격리해주지 않는다는 걸 확인.
    dev DB를 수동으로 비워 해결 — 재발 방지용 정리 로직 추가는 아직 안 함.

- **표준 모델(`Stay` 등) 착수 전 정리: 공급사 인원 필터링 규칙 문서화 +
  `HotelId` 타입 통합** — `Stay`의 `hotelId` 필드 타입을 정하려던 중 두 가지
  선행 정리가 필요했음.
  - 공급사는 요청 인원(`adults+children`)을 수용 못 하는 객실 타입은 재고
    0이 아니라 응답에서 아예 제외한다는 규칙을 확인 — `docs/supplier-api-spec.md`
    "공통 규약"에 추가. 매핑엔 있지만 이번 응답엔 없는 객실 타입 처리(제외)는
    `readme.md` "가정" 섹션에 이미 있어 링크만 추가.
  - `mapping.HotelMapping`이 자체 nested `Id` 클래스를 갖는 대신, `domain.HotelId`
    (동일한 `(supplier, externalHotelCode)` 개념)를 `@EmbeddedId`로 그대로
    재사용하도록 리팩터. `Stay.hotelId`가 `mapping.HotelMapping.Id`(JPA 타입)를
    직접 참조하면 domain이 mapping을 알게 되어 계층 순수성이 깨지고, 그렇다고
    domain에 완전히 별개의 동일 개념 타입을 새로 만들면 같은 식별자가 두 타입으로
    중복되는 문제가 있어 절충 — domain이 식별자 타입을 소유하고 mapping은
    영속화 목적으로 재사용. `HotelMappingRepository` 제네릭 타입과 관련 테스트의
    생성 코드 전부 `HotelId`로 교체.

- **패키지 규칙 위반 발견 및 수정: `MappingBatchUpsertService` → `MappingBatchUpsertRepository`** —
  사용자가 패키지 구조를 다시 검토하다가, `mapping/`은 CLAUDE.md 규칙상
  "Entity/Repository"만 있어야 하는데 이름이 "Service"인 클래스가 그 안에
  들어가 있는 걸 지적. 이 클래스는 실제로 비즈니스 판단 없이 SQL 조립·실행만
  하는 순수 데이터 접근 계층이라 역할상 Repository에 가깝다고 판단해, `application/`으로
  옮기는 대신 이름을 `MappingBatchUpsertRepository`로 바꾸고 `@Component`를
  `@Repository`로 교체(Spring의 예외 변환(`DataAccessException`) 혜택도 추가로
  얻음) — `mapping/` 패키지에 그대로 유지.

### AI 활용
- 트리거·upsert 설계는 Claude와 대안을 비교(비용 전가, 동시성, 인프라
  복잡도, DB 왕복 횟수 등 기준)한 뒤 사용자가 직접 채택.
- 수동 재동기화는 async+상태 테이블 → 구현 보류 → 엔드포인트 자체 제외
  순으로 두 번 재검토됨. 둘 다 Claude 제안을 그대로 따르지 않고 사용자가
  재검토해 범위를 스스로 축소한 사례.
- 공급사별 실패 격리(`MappingSyncRunner`가 예외를 로그만 남기고 계속
  진행)는 `AggregatorApplicationTests.contextLoads()`가 fail-fast 방식의
  문제(공급사 하나만 안 떠도 앱 전체 기동 실패, 테스트도 실제 서비스
  가동 여부에 결합됨)를 잡아낸 뒤 Claude가 판단해 반영 — 사용자 확인 전.
- 사용자가 Claude에게 `docs/supplier-api-spec.md` 충족 여부를 점검해달라고
  요청 → Claude가 항목별 대조 후 `X-Api-Key` 누락과 요청 쪽 테스트 공백을
  보고, 사용자가 반영을 지시해 구현·테스트 보강까지 진행.
- `Stay.hotelId` 타입을 Claude가 "domain 순수성 유지를 위해 supplier+
  externalHotelCode로 분리"를 추천했으나, 사용자가 제3안(`domain.HotelId`
  하나로 통합해 mapping이 재사용)을 직접 설계해 지시 — Claude 제안을 그대로
  따르지 않고 더 나은 대안으로 대체한 사례.
- 사용자가 패키지 구조를 직접 재검토해 `MappingBatchUpsertService`가
  `mapping/`(CLAUDE.md 규칙상 Entity/Repository 전용)에 잘못 들어가 있는
  걸 발견 — Claude가 만들 때 놓쳤던 규칙 위반을 사용자가 코드 리뷰로 잡음.

### 참고 자료
- `docs/architecture.md` "매핑 생성 트리거", "매핑 배치 upsert"

---

## Day 4 - StaySearchService/StaySearchController 구현 (핵심 검색 흐름)

### 수행 내용
- `Stay`/`RoomTypeOffer`/`Price`/`NightlyRate` 표준 모델 필드 확정(`docs/domain-model.md`
  초안 그대로), 미사용 `RoomAvailability.kt` 스켈레톤 제거
- `StaySearchService`: 매핑 전체 조회(맵 캐싱) → 공급사별 50개 청크 분할 →
  병렬 `fetchAvailability` → 내부 식별자 resolve → `Stay` 조립
- `StaySearchController`: `GET /api/v1/stays/search`
- Mock Supplier + 실제 앱 기동으로 통합 검증 (curl로 실제 응답 확인,
  `suspend fun` 컨트롤러가 Spring MVC에서 정상 동작함을 확인)
- 통합 테스트 4개(전체 정상/한 공급사 실패/둘 다 실패/51개 이상 청크 분할),
  전체 스위트 37개 + ktlint 통과 확인 후 커밋
- Mock Supplier에 `delay` 모드 추가(초 단위 설정 가능), 이를 이용해 타임아웃
  값(connect 2초/response 4초) 확정 및 경계 동작(2초 성공/6초 타임아웃)을
  실제 앱+Mock으로 검증

### 의사결정

- **청크 분할: 공급사별 50개, `List.chunked(50)`** — `docs/supplier-api-spec.md`의
  hotelCodes 50개 제한을 지금 실제로 트리거하는 첫 호출부라 여기서 처리.
  Kotlin 표준 라이브러리로 충분해 별도 유틸 없이 인라인 처리.

- **응답 조립은 매핑이 아니라 응답을 순회** — 매핑 테이블 기준으로 순회하면서
  없는 항목을 `availableRooms: 0`으로 채우면 "정말 매진"과 "인원 초과 등으로
  응답에 아예 없음"이 구분 안 됨. `readme.md` "가정"에 이미 있는 원칙을 그대로
  구현에 반영 — 공급사 응답에 실제로 있는 offer만 순회해서 `RoomTypeOffer` 생성.

- **부분 실패 표현: 청크마다 실패를 별도 항목으로 기록** — 공급사 하나가 여러
  청크로 나뉘어 호출되면(51개 이상) 청크별로 성공/실패가 갈릴 수 있는데,
  `docs/domain-model.md`의 `partialFailures` 예시는 이 경우를 명시하지 않음.
  같은 `supplier` 값을 가진 항목이 여러 개 쌓이더라도 실패를 숨기지 않고
  전부 노출하는 쪽으로 처리 — `PartialFailure`에 청크 인덱스 등 필드를
  추가하지 않고 리스트에 자연스럽게 여러 건 쌓이게 두는 절충.

- **매핑 조회 결과를 메모리 맵으로 캐싱 후 조립** — 오퍼 하나당 매핑을 매번
  DB로 조회하지 않고, 검색 요청당 `HotelMapping`/`RoomTypeMapping` 전체를
  한 번씩만 조회해 `Map`으로 변환한 뒤 그 맵으로 resolve. 매핑 upsert 때
  겪었던 N+1 문제(JOURNAL Day 3)와 같은 함정을 조립 단계에서도 미리 피함.

- **매핑에 없는 응답 항목은 건너뜀(예외 아님)** — 공급사가 우리 매핑에 없는
  숙소/객실타입 코드로 응답하는 경우(원칙적으로 없어야 하지만 외부 시스템이라
  보장 안 됨) 전체 검색을 실패시키지 않고 해당 항목만 건너뜀 — 공급사별 실패
  격리(`MappingSyncRunner`, Day 3)와 같은 결의 방어적 설계.

- **[가정] 타임아웃 값(connect 2초/response 4초): 측정이 아니라 판단으로
  확정** — `docs/supplier-api-spec.md`에 공급사 응답 시간 SLA가 없고 Mock은
  로컬이라 지연이 0에 가까워, "정상 응답 속도를 실측해서 근거로 삼는" 방식
  자체가 성립하지 않음. 대신 (1) 고객 체감 대기 한계(일반적 웹 서비스 기준
  수 초 이내, 병렬 호출이라 가장 느린 공급사에 전체 응답 시간이 수렴)
  (2) connect는 response보다 짧게(연결 자체 실패는 더 빨리 포기해도 됨)
  라는 논리로 값을 판단해서 정함. 스펙이 알려주지 않은 공백을 해석한
  것이라 "결정"이 아니라 "가정"으로 분류 — `readme.md` "가정" 표에 반영.
  - 검증은 실측이 아니라 경계 동작 확인으로 대체: Mock에 `delay` 모드를
    추가(초 단위 지정 가능, 기존 `no-response`는 고정 30초라 정밀한 경계
    검증에 부적합)해서, 2초 지연→정상 응답(약 2.4초), 6초 지연→정확히
    4초에서 `TIMEOUT` 분류 + B는 정상이라 부분 응답(`partialFailures`에
    A만 기록)이 나가는 것까지 실제 앱으로 확인. 이 자체가 §3.2④(부분 실패
    허용)의 실제 동작 증거.

- **Swagger 컨트롤러 문서화: 인터페이스 분리 대신 컨트롤러에 직접 애노테이션** —
  컨트롤러에 `@Tag`/`@Operation`/`@Parameter`를 붙였더니 애노테이션이 많아
  복잡해 보인다는 피드백에, "인터페이스를 만들어 Swagger는 인터페이스에,
  구현체엔 비즈니스 로직만" 분리 방안을 검토. 컨트롤러 4개에 메서드가
  1~2개씩뿐인 지금 규모에서는 파일 수를 두 배로 늘릴 만큼의 이득이 없고
  (완전한 헥사고날/Spring Batch/관리자 엔드포인트를 규모 이유로 기각해온
  것과 같은 결), Kotlin에서 인터페이스 메서드의 Swagger 애노테이션을
  springdoc이 항상 매끄럽게 상속받는 것도 아니라 기각 — 현재 방식(컨트롤러에
  직접 부착) 유지. `CLAUDE.md`에 "신규 컨트롤러엔 Swagger 필수, 인터페이스
  분리는 안 함"을 규칙으로 추가해 앞으로도 이 판단이 자동으로 적용되게 함.

- **매핑 없는 공급사 처리: 숨기는 방향 → 명시하는 방향으로 최종 정리** —
  기동 시 공급사 매핑 동기화가 실패하면(다운 등) 검색 시점엔 그 공급사에
  물어볼 숙소 코드가 없어 `fetchAvailability` 호출 자체가 안 일어나고,
  `results`/`partialFailures` 둘 다 아무 항목 없이 응답이 나감 — "진짜
  상품 0개"와 "동기화 실패로 매핑이 아예 없음"이 구분 안 되는 문제.
  - 1차 시도: "우리는 공급사 상품을 대신 파는 입장이라 공급사 장애가 우리
    서비스 장애처럼 보이면 안 된다"는 사용자 근거로, 검색 API 응답은 그대로
    두고 Spring Boot Actuator 커스텀 `HealthIndicator`로 운영자에게만
    노출하는 방식을 구현·실측 검증까지 완료(`MappingSyncStatus` +
    `MappingSyncHealthIndicator`, liveness/readiness엔 영향 없음도 확인).
  - 사용자가 이 방식 자체를 재검토 후 롤백 지시 → `git revert`로 커밋 2개
    되돌림(히스토리는 남김).
  - 재정리한 최종 결정: **검색 API의 `partialFailures`에 `NO_MAPPING_DATA`
    사유로 그대로 노출**. 별도 상태 저장·인프라(Actuator) 없이, 검색
    시점에 이미 아는 정보(공급사 코드 목록이 비어있음)만으로 구현. 상세
    근거는 `docs/architecture.md` "매핑 없는 공급사 처리" 참고.
  - `SupplierFailureReason`에 `NO_MAPPING_DATA` 추가 — 다른 사유와 달리
    공급사 응답이 아니라 `StaySearchService`가 자체적으로 판정.

### AI 활용
- 사용자가 전체 흐름·청크 분할·부분실패 처리·테스트 시나리오까지 상세히
  설계해서 지시 → Claude는 그대로 구현하고, 문서에 없던 세부(청크별 부분
  실패 표현 방식)는 직접 판단해 근거와 함께 반영.
- 타임아웃 값도 사용자가 "측정이 아니라 판단으로, 논리는 이렇게" 하고 구체적
  논리(체감 한계, connect<response)와 제안 값(2초/4초)까지 제시 → Claude는
  그 논리를 그대로 문서화하고 Mock delay 모드 구현 + 경계 검증을 수행.
- 사용자가 Swagger 인터페이스 분리를 제안 → Claude가 지금 규모엔 과하다고
  반대 근거를 제시하며 반박 → 사용자가 Claude 판단을 받아들여 현재 방식
  유지로 정리. Claude가 사용자 제안을 그대로 따르지 않고 이견을 낸 사례.
- 매핑 없는 공급사 처리는 Claude가 제안한 Actuator 방식을 사용자가 처음엔
  받아들여 구현까지 갔다가, 다시 생각해보고 롤백을 지시 → 이후 사용자가
  직접 "새 상태 저장 없이 기존 정보만으로" 구현하라는 훨씬 단순한 방향을
  구체적으로(enum 케이스, 코드 스케치까지) 설계해서 지시 — Claude 제안이
  한 번 받아들여졌다가 나중에 뒤집힌 사례.

### 참고 자료
- `docs/domain-model.md` "Kotlin 도메인 모델", "응답 구조"
- `docs/supplier-adapter.md` "왜 도메인 모델을 바로 안 만들고 중간 타입을 두는가"
- `docs/architecture.md` "타임아웃 값", "매핑 없는 공급사 처리",
  `docs/supplier-adapter.md` "실패 판정 통일", `docs/mock-supplier.md` "모드"
