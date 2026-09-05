package com.eundeang.aggregator.application

import com.eundeang.aggregator.domain.SupplierClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 앱 기동 시 1회(블로킹) 매핑 동기화. 근거: docs/architecture.md "매핑 생성 트리거".
 *
 * 공급사 하나의 fetchHotels() 실패가 앱 전체 기동을 막지 않도록 공급사별로
 * 격리해서 처리한다 — SupplierClient.fetchAvailability의 "실패도 정상 흐름"
 * 철학과 동일한 방향. 실패한 공급사는 로그를 남기고 [MappingSyncStatus]에
 * 기록한 뒤 다음 공급사로 계속 진행 — 검색 API 응답에는 드러나지 않고
 * (StaySearchService는 이 상태를 모른다) 운영 확인(Actuator)에서만 보인다.
 */
@Component
class MappingSyncRunner(
    private val supplierClients: List<SupplierClient>,
    private val mappingSyncService: MappingSyncService,
    private val mappingSyncStatus: MappingSyncStatus,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) = syncAll()

    fun syncAll() =
        runBlocking {
            supplierClients.forEach { client ->
                try {
                    val hotels = client.fetchHotels()
                    mappingSyncService.syncHotels(client.supplier, hotels)
                    mappingSyncStatus.recordSuccess(client.supplier)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("${client.supplier} 매핑 동기화 실패, 다음 공급사로 계속 진행", e)
                    mappingSyncStatus.recordFailure(client.supplier, e.message ?: e.javaClass.simpleName)
                }
            }
        }

    companion object {
        private val logger = LoggerFactory.getLogger(MappingSyncRunner::class.java)
    }
}
