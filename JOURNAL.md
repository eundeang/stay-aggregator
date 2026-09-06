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
- 사용자가 "하네스 아키텍처를 적용하면 공급사 추가가 더 안정적이겠냐"고
  질문 → Claude가 "배선(자동 편입)은 이미 돼 있고 검증(계약 테스트)이
  빠져있다"고 진단하며 공통 계약 테스트 하네스를 제안 → 사용자가 그대로
  구현 지시.

- **SupplierClient 공통 계약을 테스트 하네스로 분리** — `SupplierAClientTest`/
  `SupplierBClientTest`가 구조는 거의 동일한데(실패 판정 6종, X-Api-Key
  헤더) 공유되는 게 없어서, 신규 공급사를 추가할 때 케이스를 빠뜨려도 아무도
  못 잡는 상태였음. `supplierClientContract(label, newClient, enqueueFailure)`
  함수로 뽑아내 각 공급사 테스트는 "이 상황을 어떻게 표현하는지"만 채우면
  나머지(실패 판정 5종, 타임아웃, 헤더)는 자동 검증되게 함 — DTO 파싱처럼
  공급사마다 다른 부분만 각자 테스트에 남김.
  - 검증: `SupplierAClient`의 401 판정을 일부러 `RATE_LIMITED`로 깨뜨려
    실행 → 하네스가 정확히 그 케이스만 잡아내는 것을 확인 후 원복. 리팩터
    전후로 테스트 개수(공급사당 9개, 전체 38개)와 결과가 그대로임도 확인.

- 사용자가 "그건 행동 검증(하네스는 사실 의존성이 설계대로 되어 있는지
  검증하는 것 아니냐)"고 지적 → `supplierClientContract`는 그냥 재사용되는
  테스트 코드일 뿐, "하네스 아키텍처"의 진짜 의미는 **구조·의존성이
  설계대로인지를 정적으로 검증**하는 것이라는 걸 사용자가 두 차례에 걸쳐
  바로잡음. Claude가 처음에 개념을 잘못 나눴던 걸 인정하고 방향 수정.
- **Konsist로 아키텍처 의존성 테스트 추가** — `ArchitectureTest.kt`가
  domain 순수성(다른 계층 의존 금지), 계층별 의존 방향(mapping/supplier→
  domain만, application→domain+mapping, web→application+domain,
  config→의존 없음), `SupplierClient` 구현체의 `@Component` 존재,
  `supplier/` DTO의 `internal` 여부, `mapping/` 패키지에 Entity/
  Embeddable/Repository 외 다른 게 없는지를 매 실행마다 검증.
  - 검증: `SupplierARoomTypeDto`에서 `internal`을 일부러 지우고 실행 →
    "공급사 DTO는 internal이어야 한다" 테스트가 정확히 그 지점에서
    실패하는 것 확인 후 원복.
  - Konsist API(0.17.3)가 `dependsOn`/`assertArchitecture`를 Kotlin
    "멤버 확장 함수"로 선언해서, 문서의 `scope.assertArchitecture { }`
    형태로 쓰려면 `KoArchitectureCreator.assertArchitecture`를 멤버로
    직접 import해야 함 — javap로 바이트코드만 봐서는 이 구분이 안 보여서
    한동안 헤맴.

### 참고 자료
- `docs/domain-model.md` "Kotlin 도메인 모델", "응답 구조"
- `docs/supplier-adapter.md` "왜 도메인 모델을 바로 안 만들고 중간 타입을 두는가",
  "신규 공급사가 제대로 만들어졌는지 검증: 공통 계약 테스트 하네스"
- `docs/architecture.md` "타임아웃 값", "매핑 없는 공급사 처리",
  "아키텍처 규칙을 코드로 검증: Konsist", `docs/mock-supplier.md` "모드"

---

## Day 5 - 대규모(5만 건) 시나리오에서 검색 API(③) 설계 검토

### 수행 내용
- `StaySearchService.search()` 실제 코드를 읽어 (1) 매핑 캐싱 범위 (2) 공급사별
  동시 호출 개수 제한 두 가지를 사실 확인 — 추측 없이 코드/grep 결과로만 판단
- `docs/architecture.md`에 "대규모(5만 건) 시나리오 검토" 섹션 추가
- `docs/judgment-checklist.md` 신설, 이번 항목 반영

### 의사결정

- **매핑 캐싱: 요청마다 DB 풀스캔 확인, 캐싱은 문서화만 하고 구현 보류** —
  `hotelMappingRepository.findAll()`/`roomTypeMappingRepository.findAll()`이
  검색 요청마다 재실행되고, `@Cacheable`/Caffeine 등 요청 간 재사용되는
  애플리케이션 레벨 캐시는 없음(grep 0건 확인). Day 4 저널의 "매핑 조회 결과를
  메모리 맵으로 캐싱"은 **요청 내부** N+1 방지였을 뿐 **요청 간** 캐시가
  아니었음 — 이번에 그 차이를 문서로 명확히 구분함. 5만 건 규모에서는 동시
  사용자가 많을 때 DB 부하 병목이 될 수 있으나, 매핑이 앱 기동 시 1회만
  바뀌는 현재 구조에선 "느릴 수 있음"이지 "틀린 결과"는 아니라서 지금
  구현하진 않음. 실제로 필요해지면 Caffeine + 명시적 무효화(TTL은 보조
  안전망만) 방식을 쓰기로 방향만 남김. 상세: `docs/architecture.md`
  "대규모(5만 건) 시나리오 검토" 1번.

- **공급사별 동시 호출 제한: 코드로 고치지 않고 문서화만 하기로 판단** —
  `StaySearchService.search()`가 청크(50개)마다 `async`를 무제한으로
  띄우는 것을 확인(`Semaphore`/`limitedParallelism` 등 제한 코드 0건).
  5만 건이면 공급사당 청크 약 1,000개가 동시에 발사돼, 우리 쪽 자원 고갈로
  인한 오탐 타임아웃이나 공급사 rate limit(429, `RATE_LIMITED`) 대량
  유발 위험이 있음. `Semaphore(N)`이나 `Dispatchers.IO.limitedParallelism(N)`
  자체는 코드 몇 줄로 고칠 수 있지만, **N 값의 근거가 없다**(공급사
  rate limit 스펙 미명시, Mock으로 재현 불가)는 게 구현을 보류한 이유 —
  앞서 "TTL 기반 지연 갱신"을 근거 없는 임의값 문제로 보류시킨 것과 같은
  판단 기준을 여기에도 그대로 적용. 검증 없이 구현만 해두는 건 이 프로젝트가
  지켜온 "판단엔 근거를 남긴다" 원칙에 안 맞는다고 판단. 상세:
  `docs/architecture.md` "대규모(5만 건) 시나리오 검토" 2번.

- **정렬/페이징 비범위 문서화: 이미 반영돼 있음을 확인만 함** — `readme.md`
  "비범위" 섹션에 이미 명시돼 있어 추가 조치 없음.

### AI 활용
- 사용자가 "매핑 캐싱이 요청당인지 애플리케이션 레벨인지", "동시 호출 제한이
  있는지"를 추측하지 말고 코드로 확인하라고 명시적으로 요구 → Claude가
  코드 인용 + grep 결과로 사실만 보고, 개선 방향은 "구현하지 않은 이유"와
  "필요해지면 어떻게 할지"를 구분해 문서화. 동시 호출 제한의 구현 여부
  판단은 사용자가 시간을 보고 정하라고 위임 → Claude가 "N 값 근거 없음 +
  Mock으로 검증 불가"를 이유로 문서화만 하는 쪽으로 판단.

### 참고 자료
- `docs/architecture.md` "대규모(5만 건) 시나리오 검토", "매핑 생성 트리거"
  (TTL 지연 갱신 보류 항목과의 논리적 일관성)
- `docs/judgment-checklist.md`

---

## Day 6 - 과제 안내 문서(부록 A) 원문 재현 위반 소지 제거

### 수행 내용
- 사용자가 "과제 안내 문서는 부록 A의 Supplier API 스펙을 저장소에 커밋·게시하면
  안 되고, README에는 본인 말로 요약만 하라"는 지침을 근거로 `docs/supplier-api-spec.md`가
  위반 소지가 있다고 지적 — 실제로 해당 파일을 확인해 정확한 엔드포인트 경로,
  전체 JSON 응답 예시, 에러/resultCode 표 전체를 담고 있음을 확인하고 삭제
- `docs/mock-supplier.md`, `docs/supplier-adapter.md`에도 같은 성격(정확한
  엔드포인트 경로, 전체 실패 코드 표)의 재현이 있어 "우리가 무엇을 왜 이렇게
  설계했는지"만 남기고 코드 값 나열은 제거
- 코드 주석·Swagger `@Tag` 설명 등 저장소 전반에서 `docs/supplier-api-spec.md`를
  가리키던 참조를 "과제 안내 문서(부록 A)"로 교체 (파일이 없어졌으므로 dangling
  링크 정리 목적, 원문 재게시는 아님)
- 저장소가 Public GitHub(`origin/main`)에 이미 push된 상태였고, 문제의 파일을
  추가/수정한 커밋(`1519f3c`, `9d16985`)이 이미 origin/main의 조상 커밋임을
  `git merge-base --is-ancestor`로 확인 — 즉 삭제 전 스펙 원문이 현재도 공개
  저장소에서 열람 가능한 상태였음

### 의사결정

- **`docs/supplier-api-spec.md` 삭제, 나머지 문서는 "설계 판단"만 남기고
  재현 제거** — Mock 구현 코드(DTO, Mock 컨트롤러의 실제 응답 생성 로직)에
  스펙값이 들어가는 것은 과제 지침상 허용되는 영역이라 손대지 않았다. 반면
  마크다운 문서가 "이 문서가 유일한 스펙 소스"라며 엔드포인트·JSON·에러 코드
  전체를 표로 재현하는 것은 지침이 명시적으로 금지하는 행위라 판단 — 문서에는
  "왜 이렇게 설계했는지"만 남기고, 상황을 지칭할 땐 코드 값 나열 대신 우리가
  이미 코드에서 쓰는 이름(`SupplierFailureReason`의 `RATE_LIMITED` 등)이나
  ①/② 같은 우리 표기를 쓰도록 정리.
- **JOURNAL.md의 과거 기록은 수정하지 않음** — 과거 항목들이
  `docs/supplier-api-spec.md`를 인용하지만, 코드 값을 나열한 게 아니라 "그
  문서를 참고해서 확인했다"는 사실 기록이라 시행착오를 숨기지 않는다는
  프로젝트 원칙(`commit-convention` 스킬)에 따라 그대로 둠 — 대신 이번 Day 6
  항목으로 정정 사실 자체를 남김.
- **Git history 재작성 여부는 사용자 확인 후 진행** — 파일 삭제만으로는
  과거 커밋(`git log -p`)에서 원문이 그대로 보이고, 이미 Public origin/main에
  push돼 있어 실질적 노출이 계속됨. `git filter-repo`로 전체 히스토리에서
  해당 내용을 제거하고 강제 push하는 방안을 사용자에게 제시 — 원격 저장소
  히스토리를 되돌릴 수 없게 덮어쓰는 작업이라 실행 전 반드시 확인받기로 함.

### AI 활용
- 사용자가 "이건 점수가 아니라 전형 제외 문제"라며 최우선 처리를 요구하고
  구체적 위반 근거(부록 A 원문 재현 금지, Mock 코드는 예외)까지 제시 →
  Claude가 지적된 파일 외에 같은 성격의 문제가 다른 문서 2개에도 있는지
  직접 grep으로 전수 확인해 추가로 찾아냄(`docs/mock-supplier.md`,
  `docs/supplier-adapter.md`) — 사용자가 지목한 범위보다 넓게 점검.
- Git history 노출 여부(이미 push됐는지, public인지)를 추측하지 않고
  `git merge-base --is-ancestor`, `gh repo view`로 직접 확인 후 보고.

### 참고 자료
- `docs/mock-supplier.md`, `docs/supplier-adapter.md`, `readme.md` "Mock Supplier"

---

## Day 7 - hotel_mapping 내부 surrogate ID 도입 (Case 1)

### 수행 내용
- 사용자가 재검토를 요청 — `hotel_mapping`의 PK가 `(supplier,
  external_hotel_code)` 복합키뿐이라, API 응답에 그대로 노출되면 공급사
  원본 코드가 그대로 드러난다는 문제를 지적. `domain-model.md` 원안은
  `RoomTypeOffer.roomTypeId`처럼 `hotelId`도 실제 발급된 내부 PK(Long)여야
  하는데 구현이 그렇지 않음을 확인 — Case 1~4로 나눠 순서대로 진행하기로
  사용자와 합의 (Case 2: room_type_mapping FK 전환, Case 3: batch upsert
  대응, Case 4: 검색 API 응답 타입 전환).
- Case 1(이번 커밋 범위): `hotel_mapping`에 `id BIGINT AUTO_INCREMENT` PK
  추가, `(supplier, external_hotel_code)`는 UNIQUE 제약으로 유지.
- TDD: `HotelMappingRepositoryTest` 작성(Red, 컴파일 실패로 확인) →
  `HotelMapping`/`HotelMappingRepository` 구현(Green, 신규 테스트 4건
  포함 전체 10개 테스트 클래스 통과 확인) → `HotelId`에서 이제 쓰이지
  않는 JPA 어노테이션(`@Embeddable`/`@Column`/`@Enumerated`) 제거(Refactor,
  재확인 통과).

### 의사결정

- **`hotel_mapping.id`(surrogate Long) 도입, `(supplier,
  external_hotel_code)`는 UNIQUE로 격하** — Day 3에서 "domain.HotelId를
  `@EmbeddedId`로 재사용"하기로 정한 결정을 부분적으로 뒤집음. 당시엔
  "같은 내부 식별자로 매핑되는가"만 보장하면 된다고 판단했는데, 실제로는
  "그 식별자가 공급사 원본 코드를 그대로 노출하지 않아야 한다"는 요구까지
  있었다는 걸 놓쳤었다. `docs/architecture.md`에는 이미 "이미 발급된 내부
  `hotel_mapping_id`"라는 표현으로 목표 설계가 서술돼 있었으나 구현이
  따라가지 못한 상태였음.
  - 검토했던 대안: V1 마이그레이션을 직접 수정 vs V2로 별도 추가. 이미
    Public 저장소에 반영된 스키마 변경 이력을 지우지 않고 "기존 설계 →
    문제 발견 → 개선"의 흐름을 커밋 히스토리에 남기는 쪽(V2)을 택함 —
    데이터 보존이 필요 없는 로컬 개발 DB뿐이라 마이그레이션 자체는 단순
    ALTER TABLE로 충분했음.
- **`HotelId`를 순수 도메인 값 타입으로 되돌림** — surrogate PK 도입으로
  `HotelMapping`이 더 이상 `HotelId`를 `@EmbeddedId`로 쓰지 않게 되면서,
  `@Embeddable`/`@Column`/`@Enumerated`/`Serializable`이 전부 불필요한
  코드가 됨. `HotelId`는 이제 "공급사 원본 (supplier, externalHotelCode)
  조회 키"라는 순수 도메인 역할만 남기고 JPA 관심사를 제거.
- **`StaySearchService`의 grouping 로직은 이번 케이스에서 그대로 유지** —
  `Stay.hotelId` 타입 전환(Case 4)까지는 기존 동작(응답의 hotelId가
  `HotelId` 복합 객체)을 그대로 보존해야 각 케이스를 독립적으로 커밋할 수
  있음. `hotelMappingById`/`hotelCodesBySupplier`를 엔티티의 새 flat
  프로퍼티(`supplier`/`externalHotelCode`) 기반으로 재계산하도록만 수정.

### AI 활용
- 사용자가 Case별 의존관계(1→2→3→4)와 각 케이스의 테스트 관점(특히 Case 3의
  "재동기화해도 hotelId 유지", Case 4의 실제 JSON 직렬화 테스트 필요성)을
  구체적으로 설계해 지시 — Claude가 제안한 4단계 개요에 성공 기준 4가지를
  추가로 명시.
- Red 확인은 추측 없이 실제로 `./gradlew test --tests
  HotelMappingRepositoryTest`를 실행해 컴파일 실패 로그를 확인한 뒤 진행.

### 참고 자료
- `docs/architecture.md` "매핑 테이블: 왜 이 스키마인가"
- `docs/domain-model.md` "Kotlin 도메인 모델 (초안)"

---

## Day 8 - room_type_mapping을 hotel_mapping_id FK로 전환 (Case 2)

### 수행 내용
- 원래 계획은 Case 2(스키마/엔티티만)와 Case 3(배치 upsert 쓰기 경로)을
  분리하는 것이었으나, 설계를 시작하기 전 검토 중 두 케이스가 실제로는
  독립적이지 않다는 걸 발견 — `RoomTypeMapping.hotelMapping`의 `@ManyToOne`을
  `hotel_mapping_id` 단일 FK로 바꾸면, 실제 쓰기 경로인
  `MappingBatchUpsertRepository`의 raw SQL(JPA 아님)이 여전히 옛 복합키만
  쓰고 있어 새로 생성되는 행의 `hotel_mapping_id`가 비게 되고, 그러면 기존
  통합 테스트(`MappingSyncServiceTest`, `MappingSyncRunnerTest`)가 깨짐.
  즉 원래 경계대로 가면 "Case 2 커밋 = 빌드는 되지만 동기화 기능이 깨진
  상태"가 되어, "각 커밋이 테스트 통과 상태를 유지해야 한다"는 원칙과
  충돌. 사용자에게 발견 사실을 보고하고 범위 재조정을 확인받음.
- **재조정된 범위**: Case 2를 "RoomType 외부 식별자 → 내부 Hotel FK 완결된
  end-to-end 전환"으로 확대. 스키마(V3 마이그레이션) + 엔티티 + 배치 upsert
  쓰기 경로(`MappingBatchUpsertRepository`/`MappingSyncService`)를 한
  커밋으로 묶음. Case 3는 "1차 구현"이 아니라 하드닝(재동기화 시 ID 안정성
  회귀 테스트, 청크 경계 테스트, legacy 컬럼/제약 제거)으로 역할 재정의.
- TDD: `RoomTypeMappingRepositoryTest`(신규) + `MappingSyncServiceTest`에
  회귀 테스트 1건 추가 → Red 확인(`hotel_mapping_id` 컬럼이 없어
  `SQLSyntaxErrorException`) → V3 마이그레이션 + 엔티티 + 배치 upsert
  재작성(Green) → 1차 Green 시도에서 `room_type_mapping.supplier`가 여전히
  NOT NULL인데 아무도 안 채워서 전체 테스트 14건 실패(시행착오, 아래
  기록) → legacy 컬럼 NOT NULL 해제로 해결 → 전체 10개 테스트 클래스(41건)
  통과 확인 → 커밋.

### 의사결정

- **Case 2/3 경계를 "스키마 vs 쓰기 경로"에서 "end-to-end 전환 vs 하드닝"으로
  재정의** — 처음에는 케이스를 작게 쪼개는 게 안전하다고 생각했으나, 기존
  동기화 경로가 JPA가 아니라 raw SQL이라는 사실 때문에 "작게 쪼개면 오히려
  중간 상태가 깨진다"는 역설이 발생. 하나의 테스트를 Green으로 만드는 데
  필수적인 production 경로는 같은 케이스에 묶어야 한다는 원칙으로 재정리.
- **매핑 upsert 청크 조립 책임을 `MappingBatchUpsertRepository`에서
  `MappingSyncService`로 이동** — `hotel_mapping_id`를 채우려면 "숙소 upsert
  → 그 청크의 id를 bulk SELECT로 조회 → room type upsert"가 같은 청크
  경계 안에서 순서대로 일어나야 하는데, 이전처럼 두 upsert 메서드가 각자
  내부에서 독립적으로 청크를 나누면 이 순서를 보장할 수 없다. 대안(레코드당
  SELECT, INSERT-SELECT-JOIN 한 문장)과 비교한 근거는
  `docs/architecture.md` "매핑 배치 upsert" 참고.
- **`room_type_mapping`의 legacy `supplier`/`external_hotel_code` 컬럼은
  삭제 대신 NOT NULL만 해제** — 스키마 전환과 legacy 정리를 한 커밋에
  묶으면 diff가 커지고 "무엇이 새 기능이고 무엇이 정리인지" 구분이
  어려워진다. 확장(컬럼 추가) → 전환(쓰기 경로 변경) → 정리(legacy 삭제,
  Case 3)로 나눠 각 단계를 독립적으로 검증 가능하게 함.

### AI 활용
- 사용자가 Case 2 초기 설계(스키마/엔티티만)를 먼저 구체적으로 제시했고,
  Claude가 실제 코드(raw SQL 쓰기 경로)를 확인하는 과정에서 그 경계가
  기존 테스트를 깨뜨린다는 걸 발견해 실행 전에 보고 → 사용자가 "억지로
  쪼개기보다 동작 가능한 vertical slice로 재정의하자"고 판단해 범위를
  직접 재설계(청크 조립 책임 이동, legacy 컬럼 단계적 삭제 등)해 지시.
- Green 1차 시도가 예상 밖의 이유(legacy NOT NULL 컬럼)로 실패한 것을
  숨기지 않고 원인 분석 후 마이그레이션을 수정 — 이미 로컬 dev DB에
  적용된 마이그레이션 파일을 수정해야 해서 dev DB를 초기화(disposable
  Docker Compose 볼륨이라 안전).

### 참고 자료
- `docs/architecture.md` "매핑 배치 upsert: 왜 JPA 배치 대신 네이티브
  멀티로우 upsert인가"

---

## Day 9 - 매핑 동기화 하드닝 + legacy 컬럼/제약 제거 (Case 3)

### 수행 내용
- Case 2가 이미 end-to-end로 동작하는 상태라, Case 3는 "처음 동작하게
  만들기"가 아니라 회귀 테스트 보강 + legacy 정리로 진행:
  - `MappingSyncServiceTest`에 2건 추가: (1) 재동기화 시 신규 객실 타입이
    기존 `hotel_mapping_id`에 연결되는가, (2) 청크 크기를 일부러 작게
    (2) 잡아 "기존 3개+신규 1개, chunk=2"로 청크 경계에 기존/신규가
    걸치는 상황을 재현해 각 숙소가 안정적인 id를 유지하는가.
  - 이 2건 모두 Case 2 구현이 이미 올바르게 처리하고 있어 추가 구현 변경
    없이 즉시 통과(Green) — 새 버그를 못 찾았다는 것도 하드닝의 정상적인
    결과로 그대로 기록.
  - 청크 크기를 테스트에서 조정할 수 있도록 `MappingSyncService`에
    `hotelChunkSize` 생성자 파라미터(기본값 1000) 추가 — 실제 운영 동작은
    바뀌지 않음.
  - `V4__drop_room_type_mapping_legacy_hotel_key.sql`: `room_type_mapping`의
    legacy `supplier`/`external_hotel_code` 컬럼과 그 위의 복합 FK/UNIQUE
    제거. 코드베이스 전체를 grep해 이 컬럼/제약을 참조하는 곳이 없음을
    확인한 뒤 제거.
- 최종 스키마를 `SHOW CREATE TABLE`로 직접 확인해 `CLAUDE.md`의
  `hotel_mapping: UNIQUE(supplier, external_hotel_code)` /
  `room_type_mapping: UNIQUE(hotel_mapping_id, external_room_type_code)`
  서술과 정확히 일치함을 확인 — 별도 문서 수정 불필요.

### 의사결정

- **하드닝 케이스에서 버그를 못 찾아도 테스트는 그대로 남긴다** — Case 3의
  목적은 "새 기능 구현"이 아니라 "이미 만든 기능이 엣지케이스에서도
  맞는지 확인하고 회귀 방지선을 긋는 것"이라, 테스트가 처음부터
  통과했다고 해서 무가치한 게 아니라 오히려 Case 2 설계가 맞았다는
  증거로 남긴다.
- **legacy 컬럼 제거 전 코드베이스 전체를 grep으로 재확인** — Case 2에서
  "컬럼은 남기고 NOT NULL만 해제"로 결정했던 이유(쓰기 경로 전환과 정리를
  분리)가 유효했는지, 즉 지금 시점에 정말 아무 코드도 그 컬럼/제약명을
  참조하지 않는지 추측 대신 직접 검색으로 확인한 뒤 삭제 마이그레이션을
  작성.

### AI 활용
- 사용자가 Case 3의 테스트 항목을 구체적으로 지정(신규 객실 타입 연결,
  청크 경계 안정성, legacy 제거 등) → Claude가 청크 경계 재현을 위해
  `hotelChunkSize`를 테스트에서 주입 가능하게 만드는 방법을 판단해 추가.

### 참고 자료
- `CLAUDE.md` "DB" 섹션 (최종 스키마와 대조 확인)

---

## Day 10 - 검색 API 응답을 내부 Long ID로 전환 (Case 4, 최종)

### 수행 내용
- `Stay.hotelId` 타입을 `HotelId`(공급사 원본 코드 조합)에서 `Long`
  (`HotelMapping.id`)으로 변경. 공급사 호출용 grouping(`offersByHotel:
  Map<HotelId, ...>`)은 그대로 유지 — "외부 호출 계층(공급사 원본 코드) →
  매핑 경계 → 도메인/고객 API(내부 ID)"라는 계층 역할을 코드 구조로 명확히
  구분.
- `StaySearchServiceTest`의 `hotelId` assertion들을 `HotelId(...)` 값
  비교에서 실제 `HotelMapping.id`(Long) 비교로 갱신 (`seedHotel` 헬퍼가
  이제 `HotelMapping`을 반환하도록 변경).
- **신규**: `StaySearchResponseSerializationTest` 추가 — 서비스/도메인
  계층 테스트만으로는 "도메인 타입은 Long인데 실제 JSON 직렬화는 여전히
  객체로 나가는" 문제를 못 잡는다는 지적에 따라, 컨트롤러가 실제로 만드는
  JSON을 Jackson으로 직접 파싱해 `hotelId`/`roomTypeId`가 숫자인지, 공급사
  원본 코드(`"A-10023"`, `externalHotelCode`)가 응답 문자열에 전혀 없는지를
  검증. `@WebMvcTest`+Mockito(suspend 함수 스터빙 문제) 대신, 기존 테스트
  스타일(@DataJpaTest + 실제 리포지토리 + FakeSupplierClient)로 컨트롤러를
  직접 생성해 호출한 뒤 Jackson 3(`tools.jackson`, Spring Boot 4.1.1
  기본값)로 직렬화하는 방식을 선택 — 이 프로젝트 테스트 전반이 이미
  이 패턴이라 새 테스트 인프라(MockMvc, Mockito) 도입 비용을 피함.
- Red 확인: 이 테스트를 Case 4 구현 전에 먼저 실행해 `hotelId`가 숫자가
  아니라는 이유로 실패하는 것을 확인 → `Stay`/`StaySearchService` 수정
  후 재실행해 통과 확인.
- `docs/domain-model.md`의 JSON 예시(`"hotelId": "..."` → `1`,
  `"roomTypeId": "..."` → `3`)와 Kotlin 도메인 모델 초안(`HotelMapping.Id`
  → `Long`)을 실제 구현과 일치시킴.
- `README.md` "설계 의사결정 요약 > 1. 숙박 상품 통합 모델"에 "API 응답의
  hotelId/roomTypeId는 공급사 원본 코드가 아니라 발급된 내부 PK" 한
  문장 반영 — 표준 모델의 핵심 필드 판단이 바뀐 경우라 `decision-logging`
  스킬의 README 갱신 기준에 해당.
- `docs/tdd-roadmap.md`에 이번 4-Case 작업을 새 항목(2-4)으로 추가하고
  완료 표시.

### 의사결정

- **API 테스트(실제 JSON 직렬화 확인)를 서비스 레벨 테스트와 별도로
  둠** — Case 4 착수 전 사용자가 "도메인 모델에서는 hotelId가 Long이어도
  실제 컨트롤러 JSON은 여전히 예전 방식일 수 있다"를 구체적으로 지적.
  기존 테스트 스위트가 전부 서비스/리포지토리 레벨이라 이 간극을 잡을
  수 없었던 걸 확인하고, 컨트롤러가 만드는 실제 JSON 문자열까지 검증하는
  테스트를 신규 계층에 추가 — 이 프로젝트에 없던 첫 "API 계약" 테스트.
- **MockMvc/Mockito 대신 기존 DataJpaTest 패턴 재사용** — suspend 컨트롤러
  메서드를 Mockito로 스텁하려면 별도 처리(runBlocking 안에서 스텁, 또는
  mockito-kotlin 추가)가 필요해 복잡도가 늘어남. 이미 프로젝트 전체가
  `@DataJpaTest` + 실제 리포지토리 + Fake 어댑터로 통합 테스트를 짜는
  스타일이라, 새 인프라를 추가하는 대신 그 스타일을 컨트롤러까지 그대로
  확장.

### AI 활용
- 사용자가 4개 케이스의 성공 기준 4가지(API에 공급사 코드 미노출, 재동기화
  시 hotelId/roomTypeId 안정성, 스키마·도메인·JSON·문서 전체 일치)를
  직접 제시 → Claude가 각 케이스 구현 후 이 기준으로 스스로 점검.
- Case 4의 API 테스트 방식(WebMvcTest+Mockito vs 기존 패턴 재사용)은
  Claude가 실제 컴파일 문제(suspend 함수 스터빙)를 검토한 뒤 판단해
  선택 — 사용자 확인 전에 결정.

### 참고 자료
- `docs/domain-model.md` "응답 구조", "Kotlin 도메인 모델 (초안)"
- `README.md` "설계 의사결정 요약 > 1. 숙박 상품 통합 모델"

---

## Day 11 - nightlyNetAmount 예시 수정 + bounded concurrency 확인 + 요청 파라미터 검증

### 수행 내용
- `docs/domain-model.md` JSON 예시의 `nightlyNetAmount` 세 값 합이
  `totalAmount`와 정확히 같아(132000+165000+132000=429000) 세금이 0인
  것처럼 보이던 문제를 수정 — net(세전) 합이 gross(세후)보다 작도록
  값 변경(120000+150000+120000=390000, totalAmount 429000은 유지).
- "수천~수만 숙소 검색 시 bounded concurrency 전략 문서화" 요청을 받고
  `docs/architecture.md`를 확인한 결과, **이미 존재함을 확인** — "대규모
  (5만 건) 시나리오 검토" 섹션의 "2. 공급사별 병렬 호출 개수 제한: 없음"
  항목(Day 5)이 `Semaphore(N)`/`Dispatchers.IO.limitedParallelism(N)`
  전략과 그걸 지금 구현하지 않은 이유(rate limit 스펙 부재로 N값 근거
  없음)까지 이미 상세히 담고 있었다. 중복 작성 대신 기존 문서를 그대로
  가리키는 쪽으로 판단 — 새로 쓰지 않은 것도 판단이라 기록.
- 검색 API 요청 파라미터 검증 추가: `checkOut`이 `checkIn` 이후가 아니면,
  `adults`가 1 미만이면, `children`이 음수면 각각 400(`ResponseStatusException`)을
  던지도록 `StaySearchController`에 반영. TDD로 진행:
  `StaySearchControllerValidationTest` 5건 작성 → Red 확인(검증 로직이
  없어 4건이 예외 없이 통과해버려 실패) → 컨트롤러에 3개 검증 추가(Green)
  → 전체 12개 테스트 클래스(49건) 통과 확인.
- 커밋 전 `ktlintCheck`를 처음으로 실행해, Case 2에서 작성한
  `MappingBatchUpsertRepository.findHotelIdsByExternalCodes`의 줄바꿈
  스타일 위반 1건을 발견 — `ktlintFormat`으로 정리. 지금까지 커밋마다
  `./gradlew test`만 돌리고 lint는 확인하지 않고 있었다는 뜻이라, 앞으로는
  커밋 전 `ktlintCheck`도 같이 확인하기로 함.

### 의사결정

- **bounded concurrency는 새 결정이 아니라 "이미 있는 결정 확인"** —
  요청받은 내용을 곧바로 작성하지 않고 먼저 기존 문서를 검색해 중복
  여부를 확인. 이미 충분히 상세한 문서(원인 분석 + 검토한 대안 + 채택
  안 + 왜 아직 구현 안 했는지)가 있어서, 거기 몇 줄을 덧붙이기보다
  "이미 되어 있다"고 사용자에게 보고하는 쪽을 택함 — 문서 중복은 시간이
  지나며 두 버전이 갈라지는 위험이 있어 지양.
- **요청 파라미터 유효성 기준은 [가정]** — 스펙에 파라미터 검증 규칙이
  없어 우리가 판단해서 채운 항목이라 `decision-logging` 스킬 기준대로
  README "가정" 표에도 반영. 검증 범위를 "구조적으로 무의미한 조합만
  거부"로 최소화하고, 과거 날짜 제한처럼 스펙 근거 없는 추가 제약은
  넣지 않음 (근거 없는 임의 규칙을 늘리지 않는다는 이 프로젝트의
  기존 원칙과 동일선상).
- **검증은 `@Validated`+Bean Validation 대신 컨트롤러 내 수동 체크로
  구현** — `adults`/`children`은 애노테이션(`@Min`)으로 가능하지만
  `checkOut > checkIn`은 두 파라미터를 함께 봐야 하는 교차 검증이라
  애노테이션만으로는 어차피 안 됨. 검증 방식을 파라미터 셋과 교차
  파라미터 셋으로 나누기보다, 3개 규칙 전부를 같은 방식(수동 체크 +
  `ResponseStatusException`)으로 통일 — 이 규모에 새 검증 프레임워크
  의존성/애노테이션 처리 도입은 과하다고 판단.

### AI 활용
- 사용자가 남은 3개 항목(숫자 예시 수정, concurrency 문서화, 검증)을
  순서대로 이어가라고 지시 → Claude가 각 항목 착수 전 먼저 관련 문서를
  검색해 이미 되어 있는 부분(concurrency)과 새로 해야 하는 부분(검증)을
  구분해 보고.

### 참고 자료
- `docs/architecture.md` "대규모(5만 건) 시나리오 검토 > 2. 공급사별
  병렬 호출 개수 제한"
- `README.md` "가정"
