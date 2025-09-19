package com.flightbooking.domain

import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

data class FlightCreationResponse(
    val flightId: UUID,
    val sourceAirport: String,
    val destinationAirport: String,
    val departureTime: ZonedDateTime,
    val arrivalTime: ZonedDateTime,
    val airplaneId: UUID,
    val price: BigDecimal,
    val status: String,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)