package com.eundeang.aggregator.web

import com.eundeang.aggregator.application.StaySearchResult
import com.eundeang.aggregator.application.StaySearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(name = "Stay Search", description = "숙박 상품 통합 검색")
@RestController
@RequestMapping("/api/v1/stays")
class StaySearchController(
    private val staySearchService: StaySearchService,
) {
    @Operation(
        summary = "숙박 상품 검색",
        description =
            "보유한 전체 숙소를 대상으로 모든 공급사를 병렬 조회해 표준 모델로 통합한 결과를 반환한다. " +
                "일부 공급사가 실패해도 나머지 결과는 포함되고, 실패는 partialFailures에 기록된다.",
    )
    @GetMapping("/search")
    suspend fun search(
        @Parameter(description = "체크인 날짜", example = "2026-09-01") @RequestParam checkIn: LocalDate,
        @Parameter(description = "체크아웃 날짜 (숙박일에 미포함)", example = "2026-09-04") @RequestParam checkOut: LocalDate,
        @Parameter(description = "성인 수", example = "2") @RequestParam adults: Int,
        @Parameter(description = "아동 수", example = "0") @RequestParam children: Int,
    ): StaySearchResult = staySearchService.search(checkIn, checkOut, adults, children)
}
