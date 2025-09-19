package com.flightbooking.domain.journeys

import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.UUID

enum class JourneyEventType {
    JOURNEY_CREATION
}

data class JourneyCreationEvent(
    val journeyId: UUID,
    val sourceAirport: String,
    val destinationAirport: String,
    val departureTime: ZonedDateTime,
    val arrivalTime: ZonedDateTime,
    val totalPrice: BigDecimal,
    val timestamp: ZonedDateTime
) {
    val type = JourneyEventType.JOURNEY_CREATION
}