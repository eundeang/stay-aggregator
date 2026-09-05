package com.eundeang.mocksupplier

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class MockMode { NORMAL, ERROR, NO_RESPONSE, DELAY }

const val NO_RESPONSE_DELAY_MS = 30_000L
const val DEFAULT_DELAY_SECONDS = 5L

@Component
class ModeStore {
    private val modes = mapOf(
        "a" to AtomicReference(MockMode.NORMAL),
        "b" to AtomicReference(MockMode.NORMAL),
    )
    private val delaySeconds = mapOf(
        "a" to AtomicLong(DEFAULT_DELAY_SECONDS),
        "b" to AtomicLong(DEFAULT_DELAY_SECONDS),
    )

    fun get(supplier: String): MockMode = modeRef(supplier).get()

    fun set(supplier: String, mode: MockMode) {
        modeRef(supplier).set(mode)
    }

    /** DELAY 모드에서 정상 응답을 반환하기 전 대기할 시간(초). */
    fun getDelaySeconds(supplier: String): Long = delayRef(supplier).get()

    fun setDelaySeconds(supplier: String, seconds: Long) {
        delayRef(supplier).set(seconds)
    }

    private fun modeRef(supplier: String): AtomicReference<MockMode> =
        modes[supplier.lowercase()] ?: throw IllegalArgumentException("unknown supplier: $supplier")

    private fun delayRef(supplier: String): AtomicLong =
        delaySeconds[supplier.lowercase()] ?: throw IllegalArgumentException("unknown supplier: $supplier")
}
