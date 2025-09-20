package com.flightbooking.data

import com.flightbooking.domain.seats.Seat
import com.flightbooking.domain.common.SeatStatus
import java.util.*

interface SeatDao {
    fun save(seat: Seat): Seat
    fun saveAll(seats: List<Seat>): List<Seat>
    fun findById(seatId: UUID): Seat?
    fun findByFlightId(flightId: UUID): List<Seat>
    fun findAvailableSeatsByFlightId(flightId: UUID): List<Seat>
    fun countAvailableSeatsByFlightId(flightId: UUID): Int
    fun updateSeatStatus(seatId: UUID, status: SeatStatus, bookingId: UUID?): Boolean
    fun findByBookingId(bookingId: UUID): List<Seat>
    fun createSeatsForFlight(flightId: UUID, totalSeats: Int): List<Seat>
    fun updateSeatsForBooking(seatIds: List<UUID>, bookingId: UUID, status: SeatStatus): Boolean
}