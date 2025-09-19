package com.flightbooking.domain.flights

import java.time.ZonedDateTime
import java.util.UUID


enum class EventType {
    FLIGHT_CREATION
}

data class FlightCreationEvent(
    val flightId: UUID,
    val sourceAirport: String,
    val destinationAirport: String,
    val departureDate: ZonedDateTime,
    val timestamp: ZonedDateTime
) {
    val type = EventType.FLIGHT_CREATION
}