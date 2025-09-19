package com.flightbooking.data

import com.flightbooking.domain.bookings.Booking
import com.flightbooking.domain.common.BookingStatus
import java.util.*

interface BookingDao {
    fun save(booking: Booking): Booking
    fun findById(bookingId: UUID): Booking?
    fun findByUserId(userId: UUID): List<Booking>
    fun findByJourneyId(journeyId: UUID): List<Booking>
    fun updateBookingStatus(bookingId: UUID, status: BookingStatus): Boolean
    fun findByUserIdAndStatus(userId: UUID, status: BookingStatus): List<Booking>
}