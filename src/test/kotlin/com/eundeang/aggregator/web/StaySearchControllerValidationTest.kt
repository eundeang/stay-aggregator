package com.eundeang.aggregator.web

import com.eundeang.aggregator.application.StaySearchService
import com.eundeang.aggregator.mapping.HotelMappingRepository
import com.eundeang.aggregator.mapping.RoomTypeMappingRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

/**
 * 요청 파라미터 검증은 시스템 경계(컨트롤러)에서 수행 — 근거 없는 조합(checkOut이
 * checkIn보다 앞선 경우, 인원 0명 이하 등)이 서비스/DB 레벨까지 내려가지 않도록 한다.
 * 스펙(부록 A)에 명시된 규칙이 아니라 우리가 판단해 채운 가정 — README "가정" 참고.
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

        @Test
        fun `checkOut이 checkIn과 같으면 400을 던진다`() {
            val exception =
                assertThrows(ResponseStatusException::class.java) {
                    runBlocking { controller.search(checkIn, checkIn, 2, 0) }
                }
            assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        }

        @Test
        fun `checkOut이 checkIn보다 이전이면 400을 던진다`() {
            val exception =
                assertThrows(ResponseStatusException::class.java) {
                    runBlocking { controller.search(checkOut, checkIn, 2, 0) }
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
        fun `유효한 파라미터면 예외 없이 정상 처리된다`() {
            assertDoesNotThrow {
                runBlocking { controller.search(checkIn, checkOut, 2, 0) }
            }
        }
    }
