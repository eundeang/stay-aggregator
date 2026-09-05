package com.eundeang.aggregator.application

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

/**
 * 공급사별 마지막 매핑 동기화 결과를 /actuator/health에 노출 — 운영 확인 전용.
 * 검색 API(StaySearchService)는 이 상태를 전혀 참조하지 않는다: 공급사 장애를
 * 클라이언트에게는 숨기고(§ "가정" — 우리는 공급사 상품을 판매하는 입장이라
 * 우리 서비스 장애처럼 보이면 안 됨) 운영자만 확인할 수 있게 분리한 결정
 * (JOURNAL.md 참고).
 *
 * 주의: Spring Boot는 커스텀 HealthIndicator를 liveness/readiness 그룹에
 * 자동으로 편입시키지 않는다 — 의도적으로 그대로 둔 것. 공급사 장애 때문에
 * 우리 앱이 재시작(liveness 실패)되거나 트래픽에서 빠지면(readiness 실패)
 * 안 되기 때문이다. 기본 `/actuator/health` 엔드포인트에서만 확인 가능.
 */
@Component
class MappingSyncHealthIndicator(
    private val mappingSyncStatus: MappingSyncStatus,
) : HealthIndicator {
    override fun health(): Health {
        val snapshot = mappingSyncStatus.snapshot()
        val builder = if (snapshot.values.all { it.succeeded }) Health.up() else Health.down()
        snapshot.forEach { (supplier, outcome) ->
            builder.withDetail(
                supplier.name,
                mapOf(
                    "succeeded" to outcome.succeeded,
                    "at" to outcome.at.toString(),
                    "error" to outcome.errorMessage,
                ),
            )
        }
        return builder.build()
    }
}
