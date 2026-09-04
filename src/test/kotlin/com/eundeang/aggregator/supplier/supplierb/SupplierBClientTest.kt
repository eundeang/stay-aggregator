package com.eundeang.aggregator.supplier.supplierb

import com.eundeang.aggregator.domain.SupplierAvailabilityResult
import com.eundeang.aggregator.domain.SupplierFailureReason
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
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
 * docs/supplier-api-spec.md "Supplier B" 섹션의 응답 예시를 그대로 사용한
 * MockWebServer 기반 어댑터 테스트. B는 HTTP 상태 코드가 항상 200이라, 실패
 * 판정은 오직 resultCode로만 이뤄져야 한다는 게 핵심 검증 포인트.
 */
class SupplierBClientTest :
    FunSpec({

        lateinit var server: MockWebServer
        lateinit var client: SupplierBClient

        beforeEach {
            server = MockWebServer()
            server.start()
            client = SupplierBClient(webClientFor(server, readTimeoutMillis = 5_000))
        }

        afterEach {
            server.shutdown()
        }

        suspend fun assertFailureReason(
            resultCode: String,
            expected: SupplierFailureReason,
        ) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200) // B는 실패해도 HTTP 상태 코드는 항상 200
                    .setBody("""{ "resultCode": "$resultCode", "resultMessage": "supplier B failure", "data": null }""")
                    .addHeader("Content-Type", "application/json"),
            )

            val result =
                client.fetchAvailability(
                    externalHotelCodes = listOf("B77120"),
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
                          "resultCode": "0000",
                          "resultMessage": "SUCCESS",
                          "data": {
                            "items": [
                              {
                                "propertyId": "B77120",
                                "propertyName": "Riverside Hotel Seoul",
                                "rooms": [
                                  { "roomId": "R-401", "roomName": "Deluxe Twin Room", "maxOccupancy": 2 }
                                ]
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json"),
            )

            val hotels = client.fetchHotels()

            hotels.size shouldBe 1
            hotels[0].externalHotelCode shouldBe "B77120"
            hotels[0].hotelName shouldBe "Riverside Hotel Seoul"
            hotels[0].roomTypes[0].externalRoomTypeCode shouldBe "R-401"
            hotels[0].roomTypes[0].maxOccupancy shouldBe 2
        }

        test("재고 요금 정상 응답을 SupplierOffer로 변환한다") {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """
                        {
                          "resultCode": "0000",
                          "resultMessage": "SUCCESS",
                          "data": {
                            "items": [
                              {
                                "propertyId": "B77120",
                                "propertyName": "Riverside Hotel Seoul",
                                "roomId": "R-401",
                                "roomName": "Deluxe Twin Room",
                                "maxOccupancy": 2,
                                "breakfastIncluded": true,
                                "currency": "KRW",
                                "totalPrice": 452000,
                                "taxIncluded": true,
                                "inventory": [
                                  { "date": "2026-09-01", "remainingRooms": 3 },
                                  { "date": "2026-09-02", "remainingRooms": 1 }
                                ]
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json"),
            )

            val result =
                client.fetchAvailability(
                    externalHotelCodes = listOf("B77120"),
                    checkIn = LocalDate.of(2026, 9, 1),
                    checkOut = LocalDate.of(2026, 9, 3),
                    adults = 2,
                    children = 0,
                )

            val success = result.shouldBeInstanceOf<SupplierAvailabilityResult.Success>()
            val offer = success.offers.single()
            offer.externalHotelCode shouldBe "B77120"
            offer.externalRoomTypeCode shouldBe "R-401"
            offer.breakfastIncluded shouldBe true
            offer.currency shouldBe "KRW"
            offer.totalAmount shouldBe 452_000L // totalPrice 그대로 (이미 세금 포함)
            offer.nightlyNetAmounts.shouldBeNull() // B는 일자별 단가를 안 줌
            offer.dailyRemainingRooms.map { it.remainingRooms } shouldBe listOf(3, 1)
        }

        test("resultCode E400은 INVALID_REQUEST로 변환된다") {
            assertFailureReason("E400", SupplierFailureReason.INVALID_REQUEST)
        }

        test("resultCode E401은 AUTH_FAILED로 변환된다") {
            assertFailureReason("E401", SupplierFailureReason.AUTH_FAILED)
        }

        test("resultCode E429는 RATE_LIMITED로 변환된다") {
            assertFailureReason("E429", SupplierFailureReason.RATE_LIMITED)
        }

        test("resultCode E500은 SUPPLIER_ERROR로 변환된다") {
            assertFailureReason("E500", SupplierFailureReason.SUPPLIER_ERROR)
        }

        test("resultCode E503은 SUPPLIER_ERROR로 변환된다") {
            assertFailureReason("E503", SupplierFailureReason.SUPPLIER_ERROR)
        }

        test("무응답이면 TIMEOUT으로 변환된다") {
            server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.NO_RESPONSE })
            val timeoutClient = SupplierBClient(webClientFor(server, readTimeoutMillis = 300))

            val result =
                timeoutClient.fetchAvailability(
                    externalHotelCodes = listOf("B77120"),
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
                    .setBody("""{ "resultCode": "0000", "resultMessage": "SUCCESS", "data": { "items": [] } }""")
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
