package com.eundeang.aggregator.supplier

import java.net.http.HttpTimeoutException

/** WebClient/JDK HttpClient의 타임아웃은 원인 체인 어딘가에 [HttpTimeoutException]으로 감싸여 온다. */
fun isTimeout(throwable: Throwable): Boolean {
    var cause: Throwable? = throwable
    while (cause != null) {
        if (cause is HttpTimeoutException) return true
        cause = cause.cause
    }
    return false
}
