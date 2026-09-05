package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.SupplierCode
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 공급사별 마지막 매핑 동기화 결과를 메모리에 보관. 검색 API 응답과는 무관하고
 * 운영 확인 전용(Actuator 헬스 인디케이터, [MappingSyncHealthIndicator] 참고) —
 * "공급사 장애가 클라이언트에겐 안 보이되 운영자에겐 보여야 한다"는 결정
 * (JOURNAL.md 참고)에 따라 분리.
 */
@Component
class MappingSyncStatus {
    private val outcomes = ConcurrentHashMap<SupplierCode, SyncOutcome>()

    fun recordSuccess(supplier: SupplierCode) {
        outcomes[supplier] = SyncOutcome(succeeded = true, at = Instant.now(), errorMessage = null)
    }

    fun recordFailure(
        supplier: SupplierCode,
        errorMessage: String,
    ) {
        outcomes[supplier] = SyncOutcome(succeeded = false, at = Instant.now(), errorMessage = errorMessage)
    }

    fun snapshot(): Map<SupplierCode, SyncOutcome> = outcomes.toMap()

    data class SyncOutcome(
        val succeeded: Boolean,
        val at: Instant,
        val errorMessage: String?,
    )
}
