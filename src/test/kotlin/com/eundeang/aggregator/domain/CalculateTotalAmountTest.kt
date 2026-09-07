package com.eundeang.aggregator.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * 요구사항 문서 "요금 규약": 숙박 전체 금액 = 각 날짜의
 * (nightlyRate+taxAmount) 합산.
 */
class CalculateTotalAmountTest :
    FunSpec({

        test("여러 날짜의 (nightlyRate+taxAmount)를 합산한다") {
            val dailyRates =
                listOf(
                    120_000L to 12_000L,
                    150_000L to 15_000L,
                    120_000L to 12_000L,
                )

            calculateTotalAmount(dailyRates) shouldBe 429_000L
        }

        test("1박(원소 1개)이면 그 날짜의 합만 반환한다") {
            val dailyRates = listOf(120_000L to 12_000L)

            calculateTotalAmount(dailyRates) shouldBe 132_000L
        }

        test("taxAmount가 0이어도 nightlyRate 합만 정상 반환한다") {
            val dailyRates =
                listOf(
                    100_000L to 0L,
                    100_000L to 0L,
                )

            calculateTotalAmount(dailyRates) shouldBe 200_000L
        }
    })
