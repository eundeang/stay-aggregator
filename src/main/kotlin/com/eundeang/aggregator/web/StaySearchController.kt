package com.eundeang.aggregator.web

import com.eundeang.aggregator.application.StaySearchResult
import com.eundeang.aggregator.application.StaySearchService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/stays")
class StaySearchController(
    private val staySearchService: StaySearchService,
) {
    @GetMapping("/search")
    suspend fun search(
        @RequestParam checkIn: LocalDate,
        @RequestParam checkOut: LocalDate,
        @RequestParam adults: Int,
        @RequestParam children: Int,
    ): StaySearchResult = staySearchService.search(checkIn, checkOut, adults, children)
}
