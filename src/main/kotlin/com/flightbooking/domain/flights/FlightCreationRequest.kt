package com.flightbooking.domain

import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

data class FlightCreationRequest(
    val sourceAirport: String,
    val destinationAirport: String,
    val departureTime: ZonedDateTime,
    val arrivalTime: ZonedDateTime,
    val airplaneId: UUID,
    val price: BigDecimal
)