package com.eundeang.aggregator.supplier.suppliera

import com.eundeang.aggregator.domain.SupplierAvailabilityResult
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

/**
 * docs/supplier-api-spec.md "Supplier A" 섹션의 응답 예시를 그대로 사용한
 * MockWebServer 기반 어댑터 테스트. 9090 Mock Supplier는 띄우지 않는다.
 */
class SupplierAClientTest :
    FunSpec({

        lateinit var server: MockWebServer
        lateinit var client: SupplierAClient

        beforeEach {
            server = MockWebServer()
            server.start()
            client = SupplierAClient(webClientFor(server, readTimeoutMillis = 5_000))
        }

        afterEach {
            server.shutdown()
        }

        suspend fun assertFailureReason(
            httpStatus: Int,
            expected: SupplierFailureReason,
        ) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(httpStatus)
                    .setBody("""{ "error": "SOME_ERROR", "message": "supplier A failure" }""")
                    .addHeader("Content-Type", "application/json"),
            )

            val result =
                client.fetchAvailability(
                    externalHotelCodes = listOf("A-10023"),
                    checkIn = LocalDate.of(2026, 9, 1),
                    checkOut = LocalDate.of(2026, 9, 3),
                    adults = 2,
                    children = 0,
                )

            val failure = result.shouldBeInstanceOf<SupplierAvailabilityResult.Failure>()
            failure.reason shouldBe expected
        }

        test("숙소 목록 응답을 SupplierHotel로 변환한다") {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """
                        {
                          "items": [
                            {
                              "hotelCode": "A-10023",
                              "hotelName": "Riverside Hotel Seoul",
                              "roomTypes": [
                                { "roomTypeCode": "DLX-TWN", "roomTypeName": "Deluxe Twin", "maxOccupancy": 2 }
                              ]
                            }
                          ]
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json"),
            )

            val hotels = client.fetchHotels()

            hotels.size shouldBe 1
            hotels[0].externalHotelCode shouldBe "A-10023"
            hotels[0].hotelName shouldBe "Riverside Hotel Seoul"
            hotels[0].roomTypes[0].externalRoomTypeCode shouldBe "DLX-TWN"
            hotels[0].roomTypes[0].maxOccupancy shouldBe 2
        }

        test("재고 요금 정상 응답을 SupplierOffer로 변환한다") {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """
                        {
                          "items": [
                            {
                              "hotelCode": "A-10023",
                              "hotelName": "Riverside Hotel Seoul",
                              "roomTypeCode": "DLX-TWN",
                              "roomTypeName": "Deluxe Twin",
                              "maxOccupancy": 2,
                              "breakfastIncluded": false,
                              "currency": "KRW",
                              "dailyRates": [
                                { "date": "2026-09-01", "remainingRooms": 3, "nightlyRate": 120000, "taxAmount": 12000 },
                                { "date": "2026-09-02", "remainingRooms": 1, "nightlyRate": 130000, "taxAmount": 13000 }
                              ]
                            }
                          ]
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json"),
            )

            val result =
                client.fetchAvailability(
                    externalHotelCodes = listOf("A-10023"),
                    checkIn = LocalDate.of(2026, 9, 1),
                    checkOut = LocalDate.of(2026, 9, 3),
                    adults = 2,
                    children = 0,
                )

            val success = result.shouldBeInstanceOf<SupplierAvailabilityResult.Success>()
            val offer = success.offers.single()
            offer.externalHotelCode shouldBe "A-10023"
            offer.externalRoomTypeCode shouldBe "DLX-TWN"
            offer.breakfastIncluded shouldBe false // dailyRates 안이 아니라 item 최상위에서 읽어야 함
            offer.currency shouldBe "KRW"
            offer.totalAmount shouldBe (120_000L + 12_000L + 130_000L + 13_000L) // Σ(nightlyRate+taxAmount)
            offer.nightlyNetAmounts!!.map { it.amount } shouldBe listOf(120_000L, 130_000L)
            offer.dailyRemainingRooms.map { it.remainingRooms } shouldBe listOf(3, 1)
        }

        test("HTTP 400은 INVALID_REQUEST로 변환된다") {
            assertFailureReason(400, SupplierFailureReason.INVALID_REQUEST)
        }

        test("HTTP 401은 AUTH_FAILED로 변환된다") {
            assertFailureReason(401, SupplierFailureReason.AUTH_FAILED)
        }

        test("HTTP 429는 RATE_LIMITED로 변환된다") {
            assertFailureReason(429, SupplierFailureReason.RATE_LIMITED)
        }

        test("HTTP 500은 SUPPLIER_ERROR로 변환된다") {
            assertFailureReason(500, SupplierFailureReason.SUPPLIER_ERROR)
        }

        test("HTTP 503은 SUPPLIER_ERROR로 변환된다") {
            assertFailureReason(503, SupplierFailureReason.SUPPLIER_ERROR)
        }

        test("무응답이면 TIMEOUT으로 변환된다") {
            server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.NO_RESPONSE })
            val timeoutClient = SupplierAClient(webClientFor(server, readTimeoutMillis = 300))

            val result =
                timeoutClient.fetchAvailability(
                    externalHotelCodes = listOf("A-10023"),
                    checkIn = LocalDate.of(2026, 9, 1),
                    checkOut = LocalDate.of(2026, 9, 3),
                    adults = 2,
                    children = 0,
                )

            val failure = result.shouldBeInstanceOf<SupplierAvailabilityResult.Failure>()
            failure.reason shouldBe SupplierFailureReason.TIMEOUT
        }

        test("요청에 X-Api-Key 헤더를 포함한다") {
            server.enqueue(
                MockResponse()
                    .setBody("""{ "items": [] }""")
                    .addHeader("Content-Type", "application/json"),
            )

            client.fetchHotels()

            server.takeRequest().getHeader("X-Api-Key") shouldBe "test-api-key"
        }
    })

private fun webClientFor(
    server: MockWebServer,
    readTimeoutMillis: Long,
    apiKey: String = "test-api-key",
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
