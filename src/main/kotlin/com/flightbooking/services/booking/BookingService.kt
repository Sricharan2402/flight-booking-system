package com.flightbooking.services.booking

import com.flightbooking.data.*
import com.flightbooking.domain.bookings.*
import com.flightbooking.domain.common.BookingStatus
import com.flightbooking.domain.common.SeatStatus
import com.flightbooking.services.reservation.SeatReservationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.*

@Service
class BookingService(
    private val bookingDao: BookingDao,
    private val seatDao: SeatDao,
    private val journeyDao: JourneyDao,
    private val flightDao: FlightDao,
    private val seatReservationService: SeatReservationService
) {
    private val logger = LoggerFactory.getLogger(BookingService::class.java)


    fun createBooking(request: BookingRequest): BookingResponse {
        logger.info("Creating booking for journey ${request.journeyId} with ${request.passengerCount} passengers")

        // 1. Validate journey
        val journey = journeyDao.findById(request.journeyId)
            ?: throw IllegalArgumentException("Journey not found: ${request.journeyId}")

        // 2. Get ordered flights
        val flightIds = journey.flightDetails.map { it.flightId }
        val flights = flightIds.mapNotNull { flightDao.findById(it) }
            .sortedBy { f -> journey.flightDetails.find { it.flightId == f.flightId }?.order ?: 0 }

        var reservedSeats: Map<UUID, List<UUID>>? = null

        try {
            // 3. Reserve seats in Redis
            reservedSeats = reserveSeatsWithSortedSet(flightIds, request.passengerCount)

            // 4. Save booking record
            val booking = Booking(
                bookingId = UUID.randomUUID(),
                userId = request.userId,
                journeyId = request.journeyId,
                numberOfSeats = request.passengerCount,
                status = BookingStatus.CONFIRMED,
                paymentId = request.paymentId,
                bookingTime = ZonedDateTime.now(),
                createdAt = ZonedDateTime.now(),
                updatedAt = ZonedDateTime.now()
            )

            val savedBooking = bookingDao.save(booking)

            // 5. Update DB seat records
            for ((_, seatIds) in reservedSeats) {
                seatDao.updateSeatsForBooking(seatIds, savedBooking.bookingId, SeatStatus.BOOKED)
            }

            // 6. Release Redis reservations (DB is now source of truth)
            for ((flightId, seatIds) in reservedSeats) {
                seatReservationService.releaseSeats(flightId, seatIds)
            }

            // 7. Fetch actual assigned seats from DB for response
            val seatAssignments = getSeatAssignments(savedBooking.bookingId)

            logger.info("Successfully created booking with ID: ${savedBooking.bookingId}")
            return buildBookingResponse(savedBooking, journey, flights, seatAssignments)

        } catch (e: Exception) {
            logger.error("Failed to create booking, releasing any Redis reservations", e)
            // Clean up ANY Redis reservations that were made
            reservedSeats?.let { reservations ->
                for ((flightId, seatIds) in reservations) {
                    seatReservationService.releaseSeats(flightId, seatIds)
                }
            }
            throw e
        }
    }

    fun getBooking(bookingId: UUID): BookingResponse {
        logger.info("Retrieving booking with ID: $bookingId")

        val booking = bookingDao.findById(bookingId)
            ?: throw IllegalArgumentException("Booking not found: $bookingId")

        val journey = journeyDao.findById(booking.journeyId)
            ?: throw IllegalArgumentException("Journey not found: ${booking.journeyId}")

        val flightIds = journey.flightDetails.map { it.flightId }
        val flights = flightIds.mapNotNull { flightDao.findById(it) }
            .sortedBy { f -> journey.flightDetails.find { it.flightId == f.flightId }?.order ?: 0 }

        val seatAssignments = getSeatAssignments(bookingId)

        logger.info("Successfully retrieved booking with ID: $bookingId")
        return buildBookingResponse(booking, journey, flights, seatAssignments)
    }

    // -------------------- Helpers --------------------

    private fun reserveSeatsWithSortedSet(
        flightIds: List<UUID>,
        passengerCount: Int
    ): Map<UUID, List<UUID>> {
        val seatAssignments = mutableMapOf<UUID, List<UUID>>()
        val reservedFlights = mutableListOf<UUID>()

        try {
            for (flightId in flightIds) {
                val dbAvailableSeats = seatDao.findAvailableSeatsByFlightId(flightId)
                val allAvailableSeatIds = dbAvailableSeats.map { it.seatId }

                val actuallyAvailableSeats =
                    seatReservationService.getAvailableSeats(flightId, allAvailableSeatIds)

                if (actuallyAvailableSeats.size < passengerCount) {
                    throw IllegalArgumentException(
                        "Insufficient seats on flight $flightId. Required=$passengerCount, Available=${actuallyAvailableSeats.size}"
                    )
                }

                val seatsToReserve = actuallyAvailableSeats.take(passengerCount)
                if (!seatReservationService.reserveSeats(flightId, seatsToReserve, 300)) {
                    throw IllegalStateException("Unable to reserve seats for flight $flightId. Try again.")
                }

                seatAssignments[flightId] = seatsToReserve
                reservedFlights.add(flightId)
            }

            return seatAssignments
        } catch (e: Exception) {
            logger.error("Error during seat reservation, rolling back", e)
            for (flightId in reservedFlights) {
                seatAssignments[flightId]?.let { seatIds ->
                    seatReservationService.releaseSeats(flightId, seatIds)
                }
            }
            throw e
        }
    }

    private fun getSeatAssignments(bookingId: UUID): List<SeatAssignment> {
        val bookedSeats = seatDao.findByBookingId(bookingId)
        return bookedSeats.groupBy { it.flightId }
            .map { (flightId, seats) ->
                SeatAssignment(
                    flightId = flightId,
                    seatNumbers = seats.map { it.seatNumber }
                )
            }
    }

    private fun buildBookingResponse(
        booking: Booking,
        journey: com.flightbooking.domain.journeys.Journey,
        flights: List<com.flightbooking.domain.flights.Flight>,
        seatAssignments: List<SeatAssignment>
    ): BookingResponse {
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
            status = booking.status,
            paymentId = booking.paymentId,
            seatAssignments = seatAssignments,
            journeyDetails = journeyDetails,
            createdAt = booking.createdAt,
            updatedAt = booking.updatedAt
        )
    }
}
