package com.eundeang.mocksupplier

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Tag(name = "Mock Control", description = "공급사별 모드 전환 (정상/장애/무응답/지연) — docs/mock-supplier.md 참고")
@RestController
@RequestMapping("/control")
class ControlController(private val modeStore: ModeStore) {

    @Operation(
        summary = "공급사 모드 전환",
        description = "value=delay일 때만 seconds로 지연 시간(초, 기본 5)을 지정한다. 예: ?value=delay&seconds=6",
    )
    @PostMapping("/{supplier}/mode")
    fun setMode(
        @Parameter(description = "a 또는 b") @PathVariable supplier: String,
        @Parameter(description = "normal | error | no-response | delay") @RequestParam value: String,
        @Parameter(description = "delay 모드의 지연 시간(초)") @RequestParam(required = false) seconds: Long?,
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
