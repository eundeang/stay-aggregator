package com.eundeang.aggregator.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.JdkClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import java.net.http.HttpClient
import java.time.Duration

// TODO: §3.2④ 연동 견고성 설계에서 근거와 함께 확정
private val CONNECT_TIMEOUT = Duration.ofSeconds(3)
private val RESPONSE_TIMEOUT = Duration.ofSeconds(5)

@Configuration
class WebClientConfig {
    @Bean
    @Qualifier("supplierAWebClient")
    fun supplierAWebClient(
        builder: WebClient.Builder,
        @Value("\${supplier.a.base-url}") baseUrl: String,
    ): WebClient = buildWebClient(builder, baseUrl)

    @Bean
    @Qualifier("supplierBWebClient")
    fun supplierBWebClient(
        builder: WebClient.Builder,
        @Value("\${supplier.b.base-url}") baseUrl: String,
    ): WebClient = buildWebClient(builder, baseUrl)

    // builder는 Boot가 프로토타입 빈으로 제공 — 앱 전역 Jackson 설정을 그대로 물려받으면서
    // A/B 호출부마다 독립된 인스턴스를 쓸 수 있다.
    private fun buildWebClient(
        builder: WebClient.Builder,
        baseUrl: String,
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
            .clientConnector(connector)
            .build()
    }
}
