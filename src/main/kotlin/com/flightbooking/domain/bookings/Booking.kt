package com.flightbooking.domain.bookings

import com.flightbooking.domain.common.BookingStatus
import com.flightbooking.domain.journeys.FlightReference
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

data class Booking(
    val bookingId: UUID,
    val userId: UUID,
    val journeyId: UUID,
    val numberOfSeats: Int,
    val status: BookingStatus,
    val paymentId: String,
    val bookingTime: ZonedDateTime,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)

data class BookingRequest(
    val journeyId: UUID,
    val passengerCount: Int,
    val paymentId: String,
    val userId: UUID
)

data class BookingResponse(
    val id: UUID,
    val journeyId: UUID,
    val passengerCount: Int,
    val status: String,
    val paymentId: String,
    val seatAssignments: List<SeatAssignment>,
    val journeyDetails: JourneyDetails,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)

data class SeatAssignment(
    val flightId: UUID,
    val seatNumbers: List<String>
)

data class JourneyDetails(
    val id: UUID,
    val departureTime: ZonedDateTime,
    val arrivalTime: ZonedDateTime,
    val layoverCount: Int,
    val flights: List<FlightReference>
)