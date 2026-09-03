package com.eundeang.aggregator.mock

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/control")
class ControlController(private val modeStore: ModeStore) {

    @PostMapping("/{supplier}/mode")
    fun setMode(
        @PathVariable supplier: String,
        @RequestParam value: String,
    ): Map<String, String> {
        if (supplier.lowercase() !in setOf("a", "b")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown supplier: $supplier")
        }
        val mode = when (value.lowercase()) {
            "normal" -> MockMode.NORMAL
            "error" -> MockMode.ERROR
            "no-response" -> MockMode.NO_RESPONSE
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown mode: $value")
        }
        modeStore.set(supplier, mode)
        return mapOf("supplier" to supplier.uppercase(), "mode" to mode.name)
    }
}
