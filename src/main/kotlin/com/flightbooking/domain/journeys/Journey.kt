package com.flightbooking.domain.journeys

import com.flightbooking.domain.common.JourneyStatus
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

data class FlightReference(
    val flightId: UUID,
    val order: Int
)

data class Journey(
    val journeyId: UUID,
    val flightDetails: List<FlightReference>,
    val sourceAirport: String,
    val destinationAirport: String,
    val departureTime: ZonedDateTime,
    val arrivalTime: ZonedDateTime,
    val totalPrice: BigDecimal,
    val status: JourneyStatus,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)