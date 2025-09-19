package com.flightbooking.services.booking

import com.flightbooking.data.BookingDao
import com.flightbooking.data.SeatDao
import com.flightbooking.data.JourneyDao
import com.flightbooking.data.FlightDao
import com.flightbooking.domain.bookings.*
import com.flightbooking.domain.common.BookingStatus
import com.flightbooking.domain.common.SeatStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

@Service
class BookingService(
    private val bookingDao: BookingDao,
    private val seatDao: SeatDao,
    private val journeyDao: JourneyDao,
    private val flightDao: FlightDao
) {

    private val logger = LoggerFactory.getLogger(BookingService::class.java)

    @Transactional
    fun createBooking(request: BookingRequest): BookingResponse {
        logger.info("Creating booking for journey ${request.journeyId} with ${request.passengerCount} passengers")

        // Validate journey exists and is active
        val journey = journeyDao.findById(request.journeyId)
            ?: throw IllegalArgumentException("Journey not found: ${request.journeyId}")

        // Get flight details for the journey
        val flightIds = journey.flightDetails.map { it.flightId }
        val flights = flightIds.mapNotNull { flightDao.findById(it) }
            .sortedBy { flight -> journey.flightDetails.find { it.flightId == flight.flightId }?.order ?: 0 }

        // Check seat availability across all flights
        validateSeatAvailability(flightIds, request.passengerCount)

        // Reserve seats for each flight in the journey
        val seatAssignments = reserveSeatsForJourney(flightIds, request.passengerCount)

        // Calculate total price
        val totalPrice = journey.totalPrice * BigDecimal(request.passengerCount)

        // Create booking record
        val booking = Booking(
            bookingId = UUID.randomUUID(),
            userId = request.userId ?: UUID.randomUUID(),
            journeyId = request.journeyId,
            numberOfSeats = request.passengerCount,
            status = BookingStatus.CONFIRMED,
            paymentId = request.paymentId,
            bookingTime = ZonedDateTime.now(),
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now()
        )

        val savedBooking = bookingDao.save(booking)

        // Update seats with booking ID
        for ((flightId, seatIds) in seatAssignments) {
            seatDao.updateSeatsForBooking(seatIds, savedBooking.bookingId, SeatStatus.BOOKED)
        }

        logger.info("Successfully created booking with ID: ${savedBooking.bookingId}")

        // Build response
        return buildBookingResponse(savedBooking, seatAssignments, journey, flights)
    }

    fun getBooking(bookingId: UUID): BookingResponse {
        logger.info("Retrieving booking with ID: $bookingId")

        val booking = bookingDao.findById(bookingId)
            ?: throw IllegalArgumentException("Booking not found: $bookingId")

        val journey = journeyDao.findById(booking.journeyId)
            ?: throw IllegalArgumentException("Journey not found: ${booking.journeyId}")

        val flightIds = journey.flightDetails.map { it.flightId }
        val flights = flightIds.mapNotNull { flightDao.findById(it) }
            .sortedBy { flight -> journey.flightDetails.find { it.flightId == flight.flightId }?.order ?: 0 }

        // Get seat assignments for this booking
        val bookedSeats = seatDao.findByBookingId(bookingId)
        val seatAssignments = bookedSeats.groupBy { it.flightId }
            .map { (flightId, seats) ->
                SeatAssignment(
                    flightId = flightId,
                    seatNumbers = seats.map { it.seatNumber }
                )
            }

        logger.info("Successfully retrieved booking with ID: $bookingId")

        return buildBookingResponse(booking, emptyMap(), journey, flights, seatAssignments)
    }

    private fun validateSeatAvailability(flightIds: List<UUID>, passengerCount: Int) {
        for (flightId in flightIds) {
            val availableSeats = seatDao.countAvailableSeatsByFlightId(flightId)
            if (availableSeats < passengerCount) {
                throw IllegalArgumentException("Insufficient seats available on flight $flightId. Required: $passengerCount, Available: $availableSeats")
            }
        }
    }

    private fun reserveSeatsForJourney(flightIds: List<UUID>, passengerCount: Int): Map<UUID, List<UUID>> {
        val seatAssignments = mutableMapOf<UUID, List<UUID>>()

        for (flightId in flightIds) {
            val availableSeats = seatDao.findAvailableSeatsByFlightId(flightId)
            if (availableSeats.size < passengerCount) {
                throw IllegalArgumentException("Insufficient seats available on flight $flightId")
            }

            val seatsToReserve = availableSeats.take(passengerCount)
            val seatIds = seatsToReserve.map { it.seatId }

            // Reserve seats (update status to RESERVED)
            for (seatId in seatIds) {
                seatDao.updateSeatStatus(seatId, SeatStatus.RESERVED, null)
            }

            seatAssignments[flightId] = seatIds
        }

        return seatAssignments
    }

    private fun buildBookingResponse(
        booking: Booking,
        seatAssignmentMap: Map<UUID, List<UUID>>,
        journey: com.flightbooking.domain.journeys.Journey,
        flights: List<com.flightbooking.domain.flights.Flight>,
        existingSeatAssignments: List<SeatAssignment> = emptyList()
    ): BookingResponse {

        val seatAssignments = existingSeatAssignments.ifEmpty {
            // Build seat assignments from the reservation map
            // This would need to be enhanced to get actual seat numbers
            seatAssignmentMap.map { (flightId, seatIds) ->
                SeatAssignment(
                    flightId = flightId,
                    seatNumbers = seatIds.map { "TBD" } // TODO: Get actual seat numbers
                )
            }
        }

        val journeyDetails = JourneyDetails(
            id = journey.journeyId,
            departureTime = journey.departureTime,
            arrivalTime = journey.arrivalTime,
            layoverCount = flights.size - 1,
            flights = journey.flightDetails
        )

        return BookingResponse(
            id = booking.bookingId,
            journeyId = booking.journeyId,
            passengerCount = booking.numberOfSeats,
            status = booking.status.name,
            paymentId = booking.paymentId,
            seatAssignments = seatAssignments,
            journeyDetails = journeyDetails,
            createdAt = booking.createdAt,
            updatedAt = booking.updatedAt
        )
    }
}