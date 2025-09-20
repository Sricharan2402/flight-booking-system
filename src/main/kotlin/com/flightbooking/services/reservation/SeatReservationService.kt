package com.flightbooking.services.reservation

import java.util.*

interface SeatReservationService {
    fun reserveSeats(flightId: UUID, seatIds: List<UUID>, reservationTtlSeconds: Long): Boolean
    fun releaseSeats(flightId: UUID, seatIds: List<UUID>): Boolean
    fun getAvailableSeats(flightId: UUID, allSeats: List<UUID>): List<UUID>
    fun cleanupExpiredReservations(flightId: UUID): Int
    fun generateReservationKey(flightId: UUID): String
}