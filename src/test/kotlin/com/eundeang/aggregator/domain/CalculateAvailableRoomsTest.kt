package com.eundeang.aggregator.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

/**
 * docs/domain-model.md "재고 / 예약 가능 객실 수": N박 전체 예약 가능한 객실 수 =
 * 기간 내 일별 remainingRooms의 최솟값.
 */
class CalculateAvailableRoomsTest :
    FunSpec({

        test("날짜마다 다른 값이고 최솟값이 중간 날짜에 있으면 그 최솟값을 반환한다") {
            val dailyRemainingRooms =
                listOf(
                    DailyInventory(LocalDate.of(2026, 9, 1), 3),
                    DailyInventory(LocalDate.of(2026, 9, 2), 1),
                    DailyInventory(LocalDate.of(2026, 9, 3), 5),
                )

            calculateAvailableRooms(dailyRemainingRooms) shouldBe 1
        }

        test("모든 날짜 재고가 0이면 0(예약 불가)을 반환한다") {
            val dailyRemainingRooms =
                listOf(
                    DailyInventory(LocalDate.of(2026, 9, 1), 0),
                    DailyInventory(LocalDate.of(2026, 9, 2), 0),
                )

            calculateAvailableRooms(dailyRemainingRooms) shouldBe 0
        }

        test("1박(원소 1개)이면 그 값을 그대로 반환한다") {
            val dailyRemainingRooms =
                listOf(
                    DailyInventory(LocalDate.of(2026, 9, 1), 7),
                )

            calculateAvailableRooms(dailyRemainingRooms) shouldBe 7
        }

        test("모든 날짜가 동일한 값이면 그 값을 반환한다") {
            val dailyRemainingRooms =
                listOf(
                    DailyInventory(LocalDate.of(2026, 9, 1), 4),
                    DailyInventory(LocalDate.of(2026, 9, 2), 4),
                    DailyInventory(LocalDate.of(2026, 9, 3), 4),
                )

            calculateAvailableRooms(dailyRemainingRooms) shouldBe 4
        }

        test("빈 리스트가 들어오면 IllegalArgumentException을 던진다") {
            shouldThrow<IllegalArgumentException> {
                calculateAvailableRooms(emptyList())
            }
        }
    })
