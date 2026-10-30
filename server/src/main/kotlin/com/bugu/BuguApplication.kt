package com.bugu

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class BuguApplication

fun main(args: Array<String>) {
    runApplication<BuguApplication>(*args)
}
