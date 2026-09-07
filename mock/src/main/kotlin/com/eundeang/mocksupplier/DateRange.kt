package com.eundeang.mocksupplier

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** checkIn(포함) ~ checkOut(미포함) 사이의 숙박일 목록. */
fun datesBetween(
    checkIn: LocalDate,
    checkOut: LocalDate,
): List<LocalDate> {
    val nights = ChronoUnit.DAYS.between(checkIn, checkOut)
    return (0 until nights).map { checkIn.plusDays(it) }
}
