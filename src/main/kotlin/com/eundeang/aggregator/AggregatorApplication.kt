package com.eundeang.aggregator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AggregatorApplication

fun main(args: Array<String>) {
    runApplication<AggregatorApplication>(*args)
}
