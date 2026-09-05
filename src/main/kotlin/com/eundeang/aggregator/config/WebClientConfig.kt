package com.eundeang.aggregator.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.JdkClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import java.net.http.HttpClient
import java.time.Duration

// 근거: docs/architecture.md "타임아웃 값" — 공급사 응답 시간에 대한 스펙/SLA가
// 없어 측정이 아니라 판단으로 정함(가정, readme.md "가정" 참고).
private val CONNECT_TIMEOUT = Duration.ofSeconds(2)
private val RESPONSE_TIMEOUT = Duration.ofSeconds(4)

@Configuration
class WebClientConfig {
    @Bean
    @Qualifier("supplierAWebClient")
    fun supplierAWebClient(
        builder: WebClient.Builder,
        @Value("\${supplier.a.base-url}") baseUrl: String,
        @Value("\${supplier.a.api-key}") apiKey: String,
    ): WebClient = buildWebClient(builder, baseUrl, apiKey)

    @Bean
    @Qualifier("supplierBWebClient")
    fun supplierBWebClient(
        builder: WebClient.Builder,
        @Value("\${supplier.b.base-url}") baseUrl: String,
        @Value("\${supplier.b.api-key}") apiKey: String,
    ): WebClient = buildWebClient(builder, baseUrl, apiKey)

    // builder는 Boot가 프로토타입 빈으로 제공 — 앱 전역 Jackson 설정을 그대로 물려받으면서
    // A/B 호출부마다 독립된 인스턴스를 쓸 수 있다.
    private fun buildWebClient(
        builder: WebClient.Builder,
        baseUrl: String,
        apiKey: String,
    ): WebClient {
        val jdkHttpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build()
        val connector =
            JdkClientHttpConnector(jdkHttpClient).apply {
                setReadTimeout(RESPONSE_TIMEOUT)
            }
        return builder
            .baseUrl(baseUrl)
            .defaultHeader("X-Api-Key", apiKey)
            .clientConnector(connector)
            .build()
    }
}
