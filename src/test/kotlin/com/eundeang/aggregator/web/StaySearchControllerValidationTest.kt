package com.eundeang.aggregator.web

import com.eundeang.aggregator.application.StaySearchService
import com.eundeang.aggregator.mapping.HotelMappingRepository
import com.eundeang.aggregator.mapping.RoomTypeMappingRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.stream.Stream

/**
 * 요청 파라미터 검증은 시스템 경계(컨트롤러)에서 수행 — 근거 없는 조합(checkOut이
 * checkIn보다 앞선 경우, 인원 0명 이하 등)이 서비스/DB 레벨까지 내려가지 않도록 한다.
 * 요구사항 문서에 명시된 규칙이 아니라 우리가 판단해 채운 가정 — README "가정" 참고.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StaySearchControllerValidationTest
    @Autowired
    constructor(
        hotelMappingRepository: HotelMappingRepository,
        roomTypeMappingRepository: RoomTypeMappingRepository,
    ) {
        private val controller =
            StaySearchController(StaySearchService(emptyList(), hotelMappingRepository, roomTypeMappingRepository))
        private val checkIn = LocalDate.of(2026, 9, 1)
        private val checkOut = LocalDate.of(2026, 9, 4)

        @ParameterizedTest
        @MethodSource("invalidCheckInOutPairs")
        fun `checkOut이 checkIn 이후가 아니면 400을 던진다`(
            invalidCheckIn: LocalDate,
            invalidCheckOut: LocalDate,
        ) {
            val exception =
                assertThrows(ResponseStatusException::class.java) {
                    runBlocking { controller.search(invalidCheckIn, invalidCheckOut, 2, 0) }
                }
            assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        }

        @Test
        fun `adults가 1 미만이면 400을 던진다`() {
            val exception =
                assertThrows(ResponseStatusException::class.java) {
                    runBlocking { controller.search(checkIn, checkOut, 0, 0) }
                }
            assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        }

        @Test
        fun `children이 음수면 400을 던진다`() {
            val exception =
                assertThrows(ResponseStatusException::class.java) {
                    runBlocking { controller.search(checkIn, checkOut, 2, -1) }
                }
            assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        }

        @Test
        fun `유효한 파라미터면 예외 없이 매핑 없는 빈 결과를 반환한다`() {
            val result = runBlocking { controller.search(checkIn, checkOut, 2, 0) }

            assertEquals(emptyList<Any>(), result.results)
            assertEquals(emptyList<Any>(), result.partialFailures)
        }

        companion object {
            @JvmStatic
            fun invalidCheckInOutPairs(): Stream<Arguments> =
                Stream.of(
                    Arguments.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)),
                    Arguments.of(LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 1)),
                )
        }
    }
