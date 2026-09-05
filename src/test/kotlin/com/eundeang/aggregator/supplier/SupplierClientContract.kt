package com.eundeang.aggregator.supplier

import com.eundeang.aggregator.domain.SupplierAvailabilityResult
import com.eundeang.aggregator.domain.SupplierClient
import com.eundeang.aggregator.domain.SupplierFailureReason
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.springframework.http.client.reactive.JdkClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import java.net.http.HttpClient
import java.time.Duration
import java.time.LocalDate

private val CHECK_IN = LocalDate.of(2026, 9, 1)
private val CHECK_OUT = LocalDate.of(2026, 9, 3)
const val CONTRACT_TEST_API_KEY = "test-api-key"

/** docs/supplier-adapter.md "실패 판정 통일" 표의 각 행 — 공급사 표현 방식과 무관한 의미 단위. */
enum class FailureScenario(
    val expectedReason: SupplierFailureReason,
) {
    INVALID_REQUEST(SupplierFailureReason.INVALID_REQUEST),
    AUTH_FAILED(SupplierFailureReason.AUTH_FAILED),
    RATE_LIMITED(SupplierFailureReason.RATE_LIMITED),
    SERVER_ERROR(SupplierFailureReason.SUPPLIER_ERROR),
    SERVICE_UNAVAILABLE(SupplierFailureReason.SUPPLIER_ERROR),
}

/**
 * 모든 SupplierClient 구현체가 지켜야 하는 공통 계약. 신규 공급사를 추가할 때
 * 이 함수를 각자의 FunSpec에서 호출하면, 실패 판정 통일·타임아웃·X-Api-Key
 * 전송이 자동으로 검증된다 — 사람이 케이스를 빠뜨려도 하네스가 잡아준다.
 * 근거: docs/supplier-adapter.md "실패 판정 통일", JOURNAL.md.
 *
 * [newClient]/[enqueueFailure]만 공급사별로 다르고(요청/응답 형식 차이),
 * 그 외 검증 로직은 전부 공유된다.
 */
fun FunSpec.supplierClientContract(
    label: String,
    newClient: (server: MockWebServer, readTimeoutMillis: Long) -> SupplierClient,
    enqueueFailure: (server: MockWebServer, scenario: FailureScenario) -> Unit,
) {
    context("[$label] 공통 계약: 실패 판정 통일") {
        FailureScenario.entries.forEach { scenario ->
            test("${scenario.name} -> ${scenario.expectedReason}로 분류된다") {
                val server = MockWebServer()
                server.start()
                val client = newClient(server, 5_000)
                enqueueFailure(server, scenario)

                val result = client.fetchAvailability(listOf("ANY"), CHECK_IN, CHECK_OUT, 2, 0)

                result.shouldBeInstanceOf<SupplierAvailabilityResult.Failure>().reason shouldBe scenario.expectedReason
                server.shutdown()
            }
        }
    }

    test("[$label] 공통 계약: 무응답이면 TIMEOUT으로 분류된다") {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.NO_RESPONSE })
        val client = newClient(server, 300)

        val result = client.fetchAvailability(listOf("ANY"), CHECK_IN, CHECK_OUT, 2, 0)

        result.shouldBeInstanceOf<SupplierAvailabilityResult.Failure>().reason shouldBe SupplierFailureReason.TIMEOUT
        server.shutdown()
    }

    test("[$label] 공통 계약: 요청에 X-Api-Key 헤더를 포함한다") {
        val server = MockWebServer()
        server.start()
        val client = newClient(server, 5_000)
        enqueueFailure(server, FailureScenario.INVALID_REQUEST)

        client.fetchAvailability(listOf("ANY"), CHECK_IN, CHECK_OUT, 2, 0)

        server.takeRequest().getHeader("X-Api-Key") shouldBe CONTRACT_TEST_API_KEY
        server.shutdown()
    }
}

/** MockWebServer를 가리키는 WebClient — JdkClientHttpConnector 기반, 공급사 공통. */
fun webClientFor(
    server: MockWebServer,
    readTimeoutMillis: Long,
    apiKey: String = CONTRACT_TEST_API_KEY,
): WebClient {
    val jdkHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    val connector = JdkClientHttpConnector(jdkHttpClient).apply { setReadTimeout(Duration.ofMillis(readTimeoutMillis)) }
    return WebClient
        .builder()
        .baseUrl(server.url("/").toString())
        .defaultHeader("X-Api-Key", apiKey)
        .clientConnector(connector)
        .build()
}
