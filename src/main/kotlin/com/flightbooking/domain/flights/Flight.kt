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

data class FlightCreationRequest(
    val sourceAirport: String,
    val destinationAirport: String,
    val departureTime: ZonedDateTime,
    val arrivalTime: ZonedDateTime,
    val airplaneId: UUID,
    val price: BigDecimal,
    val totalSeats: Int
)

data class FlightCreationResponse(
    val flightId: UUID,
    val sourceAirport: String,
    val destinationAirport: String,
    val departureTime: ZonedDateTime,
    val arrivalTime: ZonedDateTime,
    val airplaneId: UUID,
    val price: BigDecimal,
    val totalSeats: Int,
    val availableSeats: Int,
    val status: String,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)