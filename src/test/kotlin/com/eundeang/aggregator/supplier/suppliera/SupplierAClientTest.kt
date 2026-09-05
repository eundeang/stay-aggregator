package com.eundeang.aggregator.supplier.suppliera

import com.eundeang.aggregator.domain.SupplierAvailabilityResult
import com.eundeang.aggregator.supplier.FailureScenario
import com.eundeang.aggregator.supplier.supplierClientContract
import com.eundeang.aggregator.supplier.webClientFor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.time.LocalDate

/**
 * docs/supplier-api-spec.md "Supplier A" 섹션의 응답 예시를 그대로 사용한
 * MockWebServer 기반 어댑터 테스트. 9090 Mock Supplier는 띄우지 않는다.
 *
 * 실패 판정 통일·타임아웃·X-Api-Key 같은 모든 SupplierClient 공통 계약은
 * [supplierClientContract]가 검증한다 — 여기서는 A 고유의 응답 구조 파싱만
 * 검증한다.
 */
class SupplierAClientTest :
    FunSpec({

        supplierClientContract(
            label = "Supplier A",
            newClient = { server, readTimeoutMillis -> SupplierAClient(webClientFor(server, readTimeoutMillis)) },
            enqueueFailure = { server, scenario ->
                val httpStatus =
                    when (scenario) {
                        FailureScenario.INVALID_REQUEST -> 400
                        FailureScenario.AUTH_FAILED -> 401
                        FailureScenario.RATE_LIMITED -> 429
                        FailureScenario.SERVER_ERROR -> 500
                        FailureScenario.SERVICE_UNAVAILABLE -> 503
                    }
                server.enqueue(
                    MockResponse()
                        .setResponseCode(httpStatus)
                        .setBody("""{ "error": "SOME_ERROR", "message": "supplier A failure" }""")
                        .addHeader("Content-Type", "application/json"),
                )
            },
        )

        context("Supplier A 고유 응답 파싱") {
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
        }
    })
