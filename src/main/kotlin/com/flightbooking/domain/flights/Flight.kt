package com.flightbooking.domain.flights

import com.flightbooking.domain.common.FlightStatus
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

data class Flight(
    val flightId: UUID,
    val sourceAirport: String,
    val destinationAirport: String,
    val departureTime: ZonedDateTime,
    val arrivalTime: ZonedDateTime,
    val airplaneId: UUID,
    val price: BigDecimal,
    val status: FlightStatus,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)