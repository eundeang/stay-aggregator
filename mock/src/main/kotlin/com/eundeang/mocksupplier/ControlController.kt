package com.eundeang.mocksupplier

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

    /** delay 모드일 때만 seconds를 함께 지정 — 예: ?value=delay&seconds=6 */
    @PostMapping("/{supplier}/mode")
    fun setMode(
        @PathVariable supplier: String,
        @RequestParam value: String,
        @RequestParam(required = false) seconds: Long?,
    ): Map<String, String> {
        if (supplier.lowercase() !in setOf("a", "b")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown supplier: $supplier")
        }
        val mode = when (value.lowercase()) {
            "normal" -> MockMode.NORMAL
            "error" -> MockMode.ERROR
            "no-response" -> MockMode.NO_RESPONSE
            "delay" -> MockMode.DELAY
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown mode: $value")
        }
        modeStore.set(supplier, mode)
        if (mode == MockMode.DELAY) {
            modeStore.setDelaySeconds(supplier, seconds ?: DEFAULT_DELAY_SECONDS)
        }

        val result = mutableMapOf("supplier" to supplier.uppercase(), "mode" to mode.name)
        if (mode == MockMode.DELAY) {
            result["seconds"] = modeStore.getDelaySeconds(supplier).toString()
        }
        return result
    }
}
