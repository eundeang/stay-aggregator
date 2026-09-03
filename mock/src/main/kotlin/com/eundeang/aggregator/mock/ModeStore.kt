package com.eundeang.aggregator.mock

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

enum class MockMode { NORMAL, ERROR, NO_RESPONSE }

const val NO_RESPONSE_DELAY_MS = 30_000L

@Component
class ModeStore {
    private val modes = mapOf(
        "a" to AtomicReference(MockMode.NORMAL),
        "b" to AtomicReference(MockMode.NORMAL),
    )

    fun get(supplier: String): MockMode = modeRef(supplier).get()

    fun set(supplier: String, mode: MockMode) {
        modeRef(supplier).set(mode)
    }

    private fun modeRef(supplier: String): AtomicReference<MockMode> =
        modes[supplier.lowercase()] ?: throw IllegalArgumentException("unknown supplier: $supplier")
}
