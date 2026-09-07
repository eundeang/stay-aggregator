package com.eundeang.aggregator.supplier.supplierb

import com.eundeang.aggregator.domain.SupplierAvailabilityResult
import com.eundeang.aggregator.supplier.FailureScenario
import com.eundeang.aggregator.supplier.supplierClientContract
import com.eundeang.aggregator.supplier.webClientFor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.time.LocalDate

/**
 * 요구사항 문서의 "Supplier B" 응답 예시를 그대로 사용한
 * MockWebServer 기반 어댑터 테스트. B는 HTTP 상태 코드가 항상 200이라, 실패
 * 판정은 오직 resultCode로만 이뤄져야 한다는 게 핵심 검증 포인트.
 *
 * 실패 판정 통일·타임아웃·X-Api-Key 같은 모든 SupplierClient 공통 계약은
 * [supplierClientContract]가 검증한다 — 여기서는 B 고유의 응답 구조 파싱만
 * 검증한다.
 */
class SupplierBClientTest :
    FunSpec({

        supplierClientContract(
            label = "Supplier B",
            newClient = { server, readTimeoutMillis -> SupplierBClient(webClientFor(server, readTimeoutMillis)) },
            enqueueFailure = { server, scenario ->
                val resultCode =
                    when (scenario) {
                        FailureScenario.INVALID_REQUEST -> "E400"
                        FailureScenario.AUTH_FAILED -> "E401"
                        FailureScenario.RATE_LIMITED -> "E429"
                        FailureScenario.SERVER_ERROR -> "E500"
                        FailureScenario.SERVICE_UNAVAILABLE -> "E503"
                    }
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200) // B는 실패해도 HTTP 상태 코드는 항상 200
                        .setBody("""{ "resultCode": "$resultCode", "resultMessage": "supplier B failure", "data": null }""")
                        .addHeader("Content-Type", "application/json"),
                )
            },
        )

        context("Supplier B 고유 응답 파싱") {
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
        }
    })
