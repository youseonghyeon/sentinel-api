package com.seonghyeon.sentinelapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class SentinelApiApplication

fun main(args: Array<String>) {
    runApplication<SentinelApiApplication>(*args)
}
