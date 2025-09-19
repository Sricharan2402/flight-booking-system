package com.flightbooking

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FlightBookingApplication

fun main(args: Array<String>) {
    runApplication<FlightBookingApplication>(*args)
}