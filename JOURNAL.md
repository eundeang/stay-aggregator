# Progress Journal

## Day 1 - 프로젝트 초기 설정 및 도메인 모델 설계 착수

### 수행 내용
- 기술 스택 결정 (Kotlin, Spring Boot, MySQL)
- 패키지 구조 설계
- 커밋 컨벤션 정의 및 Claude Code Skill로 등록
- 매핑 테이블(hotel_mapping, room_type_mapping) 스키마 설계 및 구현
- 매핑 테이블 PK를 surrogate key(Long)에서 자연키(복합키)로 재설계
- 표준 도메인 모델의 요금/재고 필드 설계 진행 중

### 의사결정

- **DB: MySQL (Docker Compose)** — 로컬 개발 편의보다 실전에 가까운 환경을 택함.
  H2도 검토했으나, 인메모리 DB는 세팅은 간단해도 실제 운영 환경과의 괴리가 있어
  기각.

- **패키지 구조: 포트-어댑터(단순화 버전)** — `domain.SupplierClient` 인터페이스를
  포트로 두고 `supplier/{공급사}/`가 구현체로 붙는 구조.
  - 검토했다 기각한 대안: 기술 계층형(`controller/service/repository/dto`).
    공급사가 여러 개 붙는 구조에서 `dto/` 밑에 A/B DTO가 섞이면 "이게 어느
    공급사 거였지"를 매번 찾아야 하는 문제가 있어 기각.
  - 완전한 헥사고날(`adapter/in`, `adapter/out` 풀 네이밍)도 검토했으나, 7일
    과제 규모에 과하다고 판단해 `supplier/`, `web/` 같은 익숙한 이름을 쓰되
    포트-어댑터의 본질(인터페이스+구현체 분리)만 가져오는 절충안 선택.
  - 이 구조를 택한 이유: "신규 Supplier 추가 시 무엇을 고쳐야 하는지"에 대한
    답이 명확해짐 — `supplier/{new}/`에 구현체 1개 추가, 나머지 레이어는
    무수정.

- **스키마 관리: Flyway (ddl-auto: update 대신 validate)** — 자동 스키마 생성은
  빠르지만 "왜 이렇게 스키마를 바꿨는지"가 코드에 안 남음. 마이그레이션 파일
  단위로 관리하면 스키마 변경 자체가 커밋 히스토리로 추적됨.

- **매핑 테이블 유니크 제약** —
  - `hotel_mapping`: `UNIQUE(supplier, external_hotel_code)`. 같은 공급사
    상품이 항상 같은 내부 식별자로 매핑되는 것을 애플리케이션 로직이 아니라
    DB 레벨에서 보장하기로 함 (매핑 갱신 배치가 동시에 여러 번 돌 경우의
    동시성 문제 대비).
  - `room_type_mapping`: 유니크 키를 외부 숙소 코드가 아니라 이미 발급된
    `hotel_mapping_id`(내부 FK)로 잡음. 외부 코드 조합 키보다 정규화 원칙에
    맞고 인덱스 효율도 나음.
  - 내부 식별자는 별도 채번 체계 없이 매핑 레코드의 PK를 그대로 사용하기로
    결정 (단순화).

- **중복 상품 병합: 1차 범위에서 제외** — Supplier A의 'A-10023'과 B의
  'B77120'이 실제로는 같은 숙소(Riverside Hotel Seoul)이지만, 두 공급사를
  묶어주는 공통 키가 스펙상 없음. `supplier` 컬럼을 매핑 테이블에 둬서 공급사가
  다르면 내부 식별자를 공유하지 않는 것을 기본 동작으로 삼음. 병합 판단(숙소명
  유사도 등)은 별도 설계 과제라 선택 구현으로 남겨둠.

- **[수정] 매핑 테이블 PK: surrogate key → 자연키** — 위 "매핑 테이블 유니크
  제약"에서 "내부 식별자는 매핑 레코드의 surrogate PK를 그대로 사용"하기로
  했던 결정을 뒤집음. `hotel_mapping`의 내부 숙소 식별자를 의미 없는
  surrogate `Long` 대신 `(supplier, external_hotel_code)` 복합 자연키로
  바꿈 — 공급사+외부코드 자체가 이미 유일성을 보장하는 값이라, 로그나
  디버깅 중에 surrogate id를 다시 supplier/external_hotel_code로
  역추적할 필요 없이 식별자 자체가 의미를 갖게 하기 위함.
  - `room_type_mapping`은 `hotel_mapping`을 `@MapsId` 없이 일반
    `@ManyToOne` + 복합 `@JoinColumns`로 참조. `@MapsId`로 자식 엔티티의
    PK 일부를 부모 키에서 파생시키는 방식은 Kotlin data class(불변 필드,
    기본 생성자 부재)와 결합할 때 복잡도가 커져서, `room_type_mapping`
    자체 PK는 surrogate id로 유지하고 FK만 복합키로 거는 절충 선택.
  - V1 마이그레이션은 로컬 개발용 컨테이너에만 적용된 상태(다른 환경/팀원
    공유 없음)라 V2를 새로 추가하지 않고 V1 파일을 직접 수정. 로컬 MySQL
    볼륨을 초기화(`docker compose down -v`)한 뒤 재기동해서 새 스키마
    적용과 앱 기동을 재검증.
  - `feat: 매핑 엔티티·레포지토리 추가` 커밋(surrogate key 설계)을
    대체하는 변경이라는 점을 커밋 메시지에 남김.

- **커밋 분리 기준** — 매핑 관련 변경(Flyway 설정 / 마이그레이션 SQL /
  Entity·Repository 코드)을 한 커밋에 묶으려다, "이 커밋만 revert해도 다른
  관심사가 안 깨지는가"를 기준으로 3개로 분리하기로 함
  (`chore: Flyway 설정` / `design: 매핑 스키마` / `feat: 매핑 Entity·Repository`).

- **문서 구조: CLAUDE.md ↔ docs/architecture.md 분리** — 초기엔 설계 근거까지
  전부 CLAUDE.md 한 파일에 넣었으나(약 215 단어), 이 파일은 세션마다 자동으로
  전체 로드되는 걸 알게 되어 "규칙"만 남기고 "왜"에 해당하는 근거는
  `docs/architecture.md`로 분리 (약 44% 컨텍스트 절감). CLAUDE.md에는 링크만
  남김.

- **[해결됨 → Day 2] 표준 모델 요금 필드** — 총액 필수(gross)+일자별 단가
  선택(net, null 가능)으로 결정. 근거는 `docs/domain-model.md` "요금" 참고.

### AI 활용
- 패키지 구조·스키마 설계·문서 구조 등 대부분의 설계 판단을 Claude와의 대화로
  진행함. Claude가 제안한 대안들의 트레이드오프를 검토한 뒤 최종 선택은 직접
  판단. (예: 요금 필드 설계에서 Claude가 절충안(총액 필수+일자별 선택)을
  추천했으나, 왜 그게 맞는지 근거를 검토한 뒤 채택 여부 결정)
- 문서(CLAUDE.md) 최적화가 필요하다고 판단해 Claude에게 요청 → 세션마다 자동
  로드되는 특성을 알게 되어 문서를 역할별로 분리하는 계기가 됨.

---

## Day 2 - Supplier A/B 어댑터 구현

### 수행 내용
- `docs/domain-model.md`, `docs/supplier-adapter.md`, `docs/supplier-api-spec.md`,
  `docs/mock-supplier.md` 작성 (표준 모델·포트·스펙·Mock 원칙 문서화)
- `SupplierCode`를 `mapping` → `domain` 패키지로 이동
- `domain/SupplierClient.kt` 포트 및 공급사 중립 값 객체 정의
- Mock Supplier 응답을 `docs/supplier-api-spec.md` 스펙에 맞게 재작성
- Supplier A/B 어댑터(DTO + Client) 구현, MockWebServer 기반 테스트 작성

### 의사결정

- **Mock 응답 구조 재작성** — 애초에 `docs/supplier-api-spec.md`를 읽지 않고
  구현했던 Mock의 재고·요금 응답이 실제 스펙(숙소×객실타입 조합당 1행의 flat
  구조)과 다르게 중첩(nested) 구조로 만들어져 있었음. 뒤늦게 스펙 문서를
  확인하고 발견 — Mock/DTO/어댑터 매핑 로직을 전부 flat 구조로 다시 씀.
  `breakfastIncluded`/`currency` 위치도 dailyRates 안이 아니라 item
  최상위라는 걸 이 과정에서 정정.
  - 시행착오: 어댑터 매핑 함수(`toSupplierOffers` 등)를 스펙 확인 없이 먼저
    구현했다가, 나중에 스펙 문서를 읽고서야 구조가 다르다는 걸 발견해 다시
    작성함. "문서를 먼저 읽고 그대로 구현" 원칙(`domain-model`,
    `tdd-workflow` 스킬)이 왜 필요한지 체감.
  - Mock 데이터 불변조건(재고가 날짜마다 달라야 하고 최소 한 객실 타입은
    매진(0) 날짜를 포함해야 함, `docs/mock-supplier.md`)도 처음엔 고정값으로
    구현했다가 같은 과정에서 순환 리스트로 수정.

- **어댑터 테스트 도구: MockWebServer(OkHttp) — 9090 실제 Mock 서버 아님** —
  처음엔 `mock` 모듈을 `testImplementation(project(":mock"))`으로 붙여 실제
  Mock 서버를 랜덤 포트로 띄워 검증하는 방식으로 구현했으나, 이 프로젝트의
  `tdd-workflow` 스킬이 어댑터 테스트는 MockWebServer로 스펙 예시 JSON을
  직접 흉내내도록 정해두고 있어 전면 교체함.
  - 검토했다 기각한 대안(실제 Mock 서버 기동 방식)의 문제: `mock` 모듈
    패키지가 `com.eundeang.aggregator.mock`으로 루트 앱의 컴포넌트 스캔
    범위 안에 있어서, 테스트 의존성으로 붙이자마자 `AggregatorApplicationTests`가
    `MockSupplierApplication`까지 자기 컨텍스트로 스캔해 CGLIB로 감싸려다
    실패하는 문제가 실제로 발생함 (`mocksupplier`로 패키지를 분리해 근본
    해결은 했지만, 결과적으로 MockWebServer 방식으로 가면서 이 의존성 자체를
    제거).
  - 테스트 프레임워크는 사용자 지정으로 Kotest FunSpec 스타일 채택
    (JUnit5+Assertions에서 전환).

- **WebClient 도입 시 Spring Boot 4.1 모듈 분리 재발** — `spring-webflux`만
  추가했을 때 `WebClient.Builder` 자동설정 빈이 없어 `NoSuchBeanDefinitionException`
  발생. Flyway 때와 같은 패턴 — Boot 4.1부터 기술별 자동설정이 세분화된 모듈
  (`spring-boot-webclient`)로 쪼개져 있어, `spring-webflux`(WebClient 클래스
  자체)만으로는 Boot 자동설정이 로드되지 않음. `org.springframework.boot:spring-boot-webclient`
  추가로 해결.

- **WebClient 도입 자체에 대한 확인** — WebClient 추가가 사용자 승인 없이
  이뤄진 것처럼 보여 확인 요청을 받았으나, `docs/architecture.md`에 이미
  "WebClient 지정, WebFlux 전면 도입은 안 함(spring-webflux 단일 의존성만)"이
  기록돼 있었고 이번 작업 지시에도 WebClient 사용이 명시돼 있어 기존 결정을
  그대로 따른 것임을 확인받고 진행.

- **`calculateAvailableRooms` 빈 리스트 처리: IllegalArgumentException** —
  스펙에 명시 안 된 케이스라 임의로 정하지 않고 사용자에게 트레이드오프
  제시 후 확정. 빈 기간 조회는 호출부(StaySearchService 등)의 버그로 보고,
  조용히 0을 돌려주면 "재고 0"과 "입력 오류"가 구분 안 되어 버그를 숨기게
  된다는 게 근거.

- **`calculateTotalAmount` 위치: domain 패키지 (어댑터 아님)** — 사용자는
  "Supplier A 전용 헬퍼"라고 표현했지만, 시그니처가 `List<Pair<Long,Long>>`로
  A의 DTO 타입과 무관한 순수 계산이라 `supplier/suppliera`가 아니라 domain에
  둠. `docs/supplier-adapter.md`의 "어댑터는 중립화까지만, 비즈니스 규칙은
  위 계층" 원칙과 `calculateAvailableRooms`와 같은 위치에 둬야 일관된다는
  게 근거. `SupplierAClient`는 이 함수를 호출하도록 리팩터.

- **`MappingSyncService` 테스트: `@DataJpaTest` + 실제 MySQL(H2 아님)** —
  Day 1에 이미 "MySQL을 실전 환경에 가깝게 쓰기로 하고 H2를 기각"한 결정과
  일관되게, `@AutoConfigureTestDatabase(replace = NONE)`으로 임베디드 DB
  대체 없이 실제 dev MySQL(Flyway로 마이그레이션된 스키마)을 그대로 사용.
  `@DataJpaTest`가 테스트당 트랜잭션을 자동 롤백해줘서 실제 DB를 써도
  케이스 간 격리는 그대로 유지됨.
  - 테스트 프레임워크는 이번엔 Kotest가 아니라 JUnit5로 감 — 순수 함수/HTTP
    어댑터 테스트와 달리 `@DataJpaTest`의 생성자 주입·트랜잭션 롤백을
    Kotest FunSpec에서 쓰려면 `kotest-extensions-spring`이 추가로 필요하고
    버전 호환을 검증해야 하는 리스크가 있어, tdd-workflow 스킬이 허용하는
    "JUnit5(+Assertions)" 조합으로 대체. Boot 4.1의 테스트 어노테이션 패키지
    위치(`org.springframework.boot.data.jpa.test.autoconfigure`,
    `org.springframework.boot.jdbc.test.autoconfigure`)도 이번에 처음
    확인함 (Flyway/WebClient 때와 같은 모듈 분리 패턴).
  - 시행착오로 드러난 버그 2건 (둘 다 테스트가 먼저 잡음):
    1. `RoomTypeMapping`을 매번 새 객체로 `save()`하면 surrogate id라 항상
       insert로 취급돼, 두 번째 동기화에서 DB 유니크 제약 위반
       (`DataIntegrityViolationException`) 발생 → 기존 레코드 조회 후
       upsert하도록 수정.
    2. 기존 레코드를 찾아도 필드를 갱신하지 않고 그대로 재저장해서 이름/정원
       변경이 반영 안 됨 → 찾은 엔티티의 필드를 직접 갱신하도록 수정.
       (참고로 `HotelMapping`은 자연키라 매번 새 객체로 `save()`해도 JPA가
       merge로 처리해서 이 문제가 없었음 — 두 엔티티의 PK 전략 차이가 그대로
       구현 차이로 이어짐.)

### AI 활용
- Mock/어댑터 구조를 스펙 문서 없이 먼저 설계·구현했다가, 사용자가 실제
  스펙 문서(`docs/supplier-api-spec.md`) 위치를 알려준 뒤에야 구조 불일치를
  발견 — Claude가 문서를 먼저 확인하지 않고 진행한 것이 시행착오의 원인.
  이후 문서 우선 확인 원칙을 지키도록 스스로 교정.
- 테스트 방식(실제 Mock 서버 vs MockWebServer)은 사용자가 `tdd-workflow`
  스킬을 통해 명시적으로 지정 — Claude가 임의로 선택한 방식을 사용자 지정
  방식으로 전면 교체함.
- `calculateTotalAmount` 위치는 사용자가 "네가 판단해서 제안해줘"라고
  위임 — Claude가 domain 패키지를 제안했고 사용자 이견 없이 그대로 채택.

### 참고 자료
- `docs/supplier-api-spec.md`, `docs/supplier-adapter.md`, `docs/mock-supplier.md`,
  `docs/domain-model.md`

---

## Day 3 - 매핑 생성 트리거 설계

### 수행 내용
- `MappingSyncService.syncHotels`를 "언제" 호출할지(앱 기동 시? 주기적으로?
  검색 시점?) 대안 비교 및 결정

### 의사결정

- **매핑 생성 트리거: 앱 기동 시 1회(블로킹) + 수동 재동기화** — 검색 요청
  시점 트리거, 부분 조회, 배포 시점 시드, 외부 배치 프로세스, 공급사 webhook
  구독, TTL 기반 지연 갱신까지 총 7가지 대안(기각 5·보류 1·불가 1)을 검토한
  뒤에도 기존 결정의 타당성이 더 명확해져 그대로 유지함. 상세 비교는
  `docs/architecture.md` "매핑 생성 트리거: 왜 앱 기동 시 1회 + 수동
  재동기화인가" 참고.
  - 검토했다 기각한 대안: (1) 검색 요청 시점 트리거 — 동기화 비용이 첫 검색
    사용자에게 전가되고 동시 요청 시 락/조율이 필요해지며 "정적(숙소 목록)
    vs 동적(재고·요금) 분리" 설계 의도와 어긋남 (2) 요청받은 숙소만 부분
    조회 — 검색 API에 지역/키워드 필터가 없어 "이번 요청에 필요한 일부"라는
    개념 자체가 성립하지 않음 (3) 빌드/배포 시점 시드 고정 — "자주 안
    바뀜"과 "아예 안 바뀜"을 혼동, 신규 숙소 반영 경로가 없어짐 (4) 외부
    배치 프로세스 분리 — 인프라 복잡도가 7일 과제 규모에 안 맞음 (5) 공급사
    webhook/이벤트 구독 — 스펙에 해당 기능이 없어 검토 대상 자체가 아님(불가)
  - 보류(기각 아님, 향후 확장 여지): TTL 기반 지연 갱신(Cache-Aside) — 검색
    시점 트리거의 완화판이지만 TTL 값도 "얼마나 자주 바뀌는지" 근거가 없어
    임의값이 되는 문제는 동일하게 남아 1차 범위에서는 채택 안 함

### AI 활용
- 매핑 생성 트리거 시점의 대안들을 Claude와 비교 검토(비용 전가, 동시성,
  설계 의도 정합성, 인프라 복잡도 등 기준)한 뒤, 기존에 잠정 채택했던
  "앱 기동 시 1회+수동 재동기화" 결정을 그대로 확정함.

### 참고 자료
- `docs/architecture.md` "매핑 생성 트리거"