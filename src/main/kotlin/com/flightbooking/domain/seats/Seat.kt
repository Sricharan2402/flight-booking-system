package com.flightbooking.domain.seats

import com.flightbooking.domain.common.SeatStatus
import java.time.ZonedDateTime
import java.util.*

data class Seat(
    val seatId: UUID,
    val flightId: UUID,
    val seatNumber: String,
    val status: SeatStatus,
    val bookingId: UUID?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)