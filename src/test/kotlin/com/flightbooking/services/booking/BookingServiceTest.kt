package com.flightbooking.services.booking

import com.flightbooking.data.BookingDao
import com.flightbooking.data.FlightDao
import com.flightbooking.data.JourneyDao
import com.flightbooking.data.SeatDao
import com.flightbooking.domain.bookings.Booking
import com.flightbooking.domain.bookings.BookingRequest
import com.flightbooking.domain.common.BookingStatus
import com.flightbooking.domain.common.SeatStatus
import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.journeys.FlightReference
import com.flightbooking.domain.journeys.Journey
import com.flightbooking.domain.seats.Seat
import com.flightbooking.services.reservation.SeatReservationService
import com.flightbooking.utils.TestDataFactory
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("BookingService Tests")
class BookingServiceTest {

    private val bookingDao = mockk<BookingDao>()
    private val seatDao = mockk<SeatDao>()
    private val journeyDao = mockk<JourneyDao>()
    private val flightDao = mockk<FlightDao>()
    private val seatReservationService = mockk<SeatReservationService>()

    private lateinit var bookingService: BookingService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        bookingService = BookingService(bookingDao, seatDao, journeyDao, flightDao, seatReservationService)
    }

    @Nested
    @DisplayName("Happy Path Booking")
    inner class HappyPathBooking {

        @Test
        fun `should create booking successfully with single flight journey`() {
            // Given
            val journeyId = UUID.randomUUID()
            val flightId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            val journey = TestDataFactory.createTestJourney(
                journeyId = journeyId,
                flightReferences = listOf(FlightReference(flightId, 1))
            )

            val flight = TestDataFactory.createTestFlight(flightId = flightId)

            val availableSeats = listOf(
                TestDataFactory.createTestSeat(flightId = flightId, seatNumber = "1A"),
                TestDataFactory.createTestSeat(flightId = flightId, seatNumber = "1B")
            )

            val bookingRequest = TestDataFactory.createTestBookingRequest(
                journeyId = journeyId,
                passengerCount = 2,
                userId = userId
            )

            val savedBooking = TestDataFactory.createTestBooking(
                userId = userId,
                journeyId = journeyId,
                numberOfSeats = 2
            )

            // Setup mocks
            every { journeyDao.findById(journeyId) } returns journey
            every { flightDao.findById(flightId) } returns flight
            every { seatDao.findAvailableSeatsByFlightId(flightId) } returns availableSeats
            every { seatReservationService.getAvailableSeats(flightId, any()) } returns availableSeats.map { it.seatId }
            every { seatReservationService.reserveSeats(flightId, any(), 300) } returns true
            every { bookingDao.save(any()) } returns savedBooking
            every { seatDao.updateSeatsForBooking(any(), any(), SeatStatus.BOOKED) } returns true
            every { seatDao.findByBookingId(any()) } returns availableSeats
            every { seatReservationService.releaseSeats(flightId, any()) } returns true

            // When
            val result = bookingService.createBooking(bookingRequest)

            // Then
            assertNotNull(result)
            assertEquals(savedBooking.bookingId, result.id)
            assertEquals(BookingStatus.CONFIRMED, result.status)
            assertEquals(2, result.passengerCount)

            verify(exactly = 1) { bookingDao.save(any()) }
            verify(exactly = 1) { seatDao.updateSeatsForBooking(any(), savedBooking.bookingId, SeatStatus.BOOKED) }
            verify(exactly = 1) { seatReservationService.releaseSeats(flightId, any()) }
        }

        @Test
        fun `should create booking successfully with multi-flight journey`() {
            // Given
            val journeyId = UUID.randomUUID()
            val flight1Id = UUID.randomUUID()
            val flight2Id = UUID.randomUUID()

            val journey = TestDataFactory.createTestJourney(
                journeyId = journeyId,
                flightReferences = listOf(
                    FlightReference(flight1Id, 1),
                    FlightReference(flight2Id, 2)
                )
            )

            val flight1 = TestDataFactory.createTestFlight(flightId = flight1Id)
            val flight2 = TestDataFactory.createTestFlight(flightId = flight2Id)

            val seats1 = listOf(TestDataFactory.createTestSeat(flightId = flight1Id, seatNumber = "1A"))
            val seats2 = listOf(TestDataFactory.createTestSeat(flightId = flight2Id, seatNumber = "2A"))

            val bookingRequest = TestDataFactory.createTestBookingRequest(
                journeyId = journeyId,
                passengerCount = 1
            )

            val savedBooking = TestDataFactory.createTestBooking(journeyId = journeyId, numberOfSeats = 1)

            // Setup mocks
            every { journeyDao.findById(journeyId) } returns journey
            every { flightDao.findById(flight1Id) } returns flight1
            every { flightDao.findById(flight2Id) } returns flight2
            every { seatDao.findAvailableSeatsByFlightId(flight1Id) } returns seats1
            every { seatDao.findAvailableSeatsByFlightId(flight2Id) } returns seats2
            every { seatReservationService.getAvailableSeats(any(), any()) } returns listOf(UUID.randomUUID())
            every { seatReservationService.reserveSeats(any(), any(), 300) } returns true
            every { bookingDao.save(any()) } returns savedBooking
            every { seatDao.updateSeatsForBooking(any(), any(), SeatStatus.BOOKED) } returns true
            every { seatDao.findByBookingId(any()) } returns seats1 + seats2
            every { seatReservationService.releaseSeats(any(), any()) } returns true

            // When
            val result = bookingService.createBooking(bookingRequest)

            // Then
            assertNotNull(result)
            assertEquals(savedBooking.bookingId, result.id)

            // Should have processed both flights
            verify(exactly = 2) { seatReservationService.reserveSeats(any(), any(), 300) }
            verify(exactly = 2) { seatReservationService.releaseSeats(any(), any()) }
        }
    }

    @Nested
    @DisplayName("Journey Validation")
    inner class JourneyValidation {

        @Test
        fun `should throw exception when journey not found`() {
            // Given
            val nonExistentJourneyId = UUID.randomUUID()
            val bookingRequest = TestDataFactory.createTestBookingRequest(journeyId = nonExistentJourneyId)

            every { journeyDao.findById(nonExistentJourneyId) } returns null

            // When & Then
            assertThrows<IllegalArgumentException> {
                bookingService.createBooking(bookingRequest)
            }

            verify(exactly = 0) { bookingDao.save(any()) }
        }

        @Test
        fun `should throw exception when flight in journey not found`() {
            // Given
            val journeyId = UUID.randomUUID()
            val nonExistentFlightId = UUID.randomUUID()

            val journey = TestDataFactory.createTestJourney(
                journeyId = journeyId,
                flightReferences = listOf(FlightReference(nonExistentFlightId, 1))
            )

            val bookingRequest = TestDataFactory.createTestBookingRequest(journeyId = journeyId)

            every { journeyDao.findById(journeyId) } returns journey
            every { flightDao.findById(nonExistentFlightId) } returns null

            // When & Then
            assertThrows<Exception> {
                bookingService.createBooking(bookingRequest)
            }
        }
    }

    @Nested
    @DisplayName("Seat Reservation")
    inner class SeatReservation {

        @Test
        fun `should throw exception when insufficient seats available`() {
            // Given
            val journeyId = UUID.randomUUID()
            val flightId = UUID.randomUUID()

            val journey = TestDataFactory.createTestJourney(
                journeyId = journeyId,
                flightReferences = listOf(FlightReference(flightId, 1))
            )

            val flight = TestDataFactory.createTestFlight(flightId = flightId)

            val availableSeats = listOf(
                TestDataFactory.createTestSeat(flightId = flightId, seatNumber = "1A")
            ) // Only 1 seat available

            val bookingRequest = TestDataFactory.createTestBookingRequest(
                journeyId = journeyId,
                passengerCount = 2 // Requesting 2 seats
            )

            every { journeyDao.findById(journeyId) } returns journey
            every { flightDao.findById(flightId) } returns flight
            every { seatDao.findAvailableSeatsByFlightId(flightId) } returns availableSeats
            every { seatReservationService.getAvailableSeats(flightId, any()) } returns availableSeats.map { it.seatId }

            // When & Then
            assertThrows<IllegalArgumentException> {
                bookingService.createBooking(bookingRequest)
            }

            verify(exactly = 0) { seatReservationService.reserveSeats(any(), any(), any()) }
            verify(exactly = 0) { bookingDao.save(any()) }
        }

        @Test
        fun `should throw exception when seat reservation fails`() {
            // Given
            val journeyId = UUID.randomUUID()
            val flightId = UUID.randomUUID()

            val journey = TestDataFactory.createTestJourney(
                journeyId = journeyId,
                flightReferences = listOf(FlightReference(flightId, 1))
            )

            val flight = TestDataFactory.createTestFlight(flightId = flightId)

            val availableSeats = listOf(
                TestDataFactory.createTestSeat(flightId = flightId, seatNumber = "1A")
            )

            val bookingRequest = TestDataFactory.createTestBookingRequest(
                journeyId = journeyId,
                passengerCount = 1
            )

            every { journeyDao.findById(journeyId) } returns journey
            every { flightDao.findById(flightId) } returns flight
            every { seatDao.findAvailableSeatsByFlightId(flightId) } returns availableSeats
            every { seatReservationService.getAvailableSeats(flightId, any()) } returns availableSeats.map { it.seatId }
            every { seatReservationService.reserveSeats(flightId, any(), 300) } returns false // Reservation fails

            // When & Then
            assertThrows<IllegalStateException> {
                bookingService.createBooking(bookingRequest)
            }

            verify(exactly = 0) { bookingDao.save(any()) }
        }
    }

    @Nested
    @DisplayName("Rollback and Exception Handling")
    inner class RollbackAndExceptionHandling {

        @Test
        fun `should rollback reservations when booking fails in middle of multi-flight journey`() {
            // Given
            val journeyId = UUID.randomUUID()
            val flight1Id = UUID.randomUUID()
            val flight2Id = UUID.randomUUID()

            val journey = TestDataFactory.createTestJourney(
                journeyId = journeyId,
                flightReferences = listOf(
                    FlightReference(flight1Id, 1),
                    FlightReference(flight2Id, 2)
                )
            )

            val flight1 = TestDataFactory.createTestFlight(flightId = flight1Id)
            val flight2 = TestDataFactory.createTestFlight(flightId = flight2Id)

            val seats1 = listOf(TestDataFactory.createTestSeat(flightId = flight1Id))
            val seats2 = listOf(TestDataFactory.createTestSeat(flightId = flight2Id))

            val bookingRequest = TestDataFactory.createTestBookingRequest(
                journeyId = journeyId,
                passengerCount = 1
            )

            every { journeyDao.findById(journeyId) } returns journey
            every { flightDao.findById(flight1Id) } returns flight1
            every { flightDao.findById(flight2Id) } returns flight2
            every { seatDao.findAvailableSeatsByFlightId(flight1Id) } returns seats1
            every { seatDao.findAvailableSeatsByFlightId(flight2Id) } returns seats2
            every { seatReservationService.getAvailableSeats(any(), any()) } returns listOf(UUID.randomUUID())
            every { seatReservationService.reserveSeats(flight1Id, any(), 300) } returns true  // First succeeds
            every { seatReservationService.reserveSeats(flight2Id, any(), 300) } returns false // Second fails
            every { seatReservationService.releaseSeats(flight1Id, any()) } returns true

            // When & Then
            assertThrows<Exception> {
                bookingService.createBooking(bookingRequest)
            }

            // Should rollback the first reservation
            verify(exactly = 1) { seatReservationService.releaseSeats(flight1Id, any()) }
            verify(exactly = 0) { bookingDao.save(any()) }
        }

        @Test
        fun `should release reservations when database save fails`() {
            // Given
            val journeyId = UUID.randomUUID()
            val flightId = UUID.randomUUID()

            val journey = TestDataFactory.createTestJourney(
                journeyId = journeyId,
                flightReferences = listOf(FlightReference(flightId, 1))
            )

            val flight = TestDataFactory.createTestFlight(flightId = flightId)
            val availableSeats = listOf(TestDataFactory.createTestSeat(flightId = flightId))
            val bookingRequest = TestDataFactory.createTestBookingRequest(journeyId = journeyId, passengerCount = 1)

            every { journeyDao.findById(journeyId) } returns journey
            every { flightDao.findById(flightId) } returns flight
            every { seatDao.findAvailableSeatsByFlightId(flightId) } returns availableSeats
            every { seatReservationService.getAvailableSeats(flightId, any()) } returns availableSeats.map { it.seatId }
            every { seatReservationService.reserveSeats(flightId, any(), 300) } returns true
            every { bookingDao.save(any()) } throws RuntimeException("Database error")
            every { seatReservationService.releaseSeats(flightId, any()) } returns true

            // When & Then
            assertThrows<RuntimeException> {
                bookingService.createBooking(bookingRequest)
            }

            // Should release reservations since they were made before the DB save failure
            verify(exactly = 1) { seatReservationService.releaseSeats(flightId, any()) }
        }

        @Test
        fun `should release reservations when seat update fails`() {
            // Given
            val journeyId = UUID.randomUUID()
            val flightId = UUID.randomUUID()

            val journey = TestDataFactory.createTestJourney(
                journeyId = journeyId,
                flightReferences = listOf(FlightReference(flightId, 1))
            )

            val flight = TestDataFactory.createTestFlight(flightId = flightId)
            val availableSeats = listOf(TestDataFactory.createTestSeat(flightId = flightId))
            val bookingRequest = TestDataFactory.createTestBookingRequest(journeyId = journeyId, passengerCount = 1)
            val savedBooking = TestDataFactory.createTestBooking()

            every { journeyDao.findById(journeyId) } returns journey
            every { flightDao.findById(flightId) } returns flight
            every { seatDao.findAvailableSeatsByFlightId(flightId) } returns availableSeats
            every { seatReservationService.getAvailableSeats(flightId, any()) } returns availableSeats.map { it.seatId }
            every { seatReservationService.reserveSeats(flightId, any(), 300) } returns true
            every { bookingDao.save(any()) } returns savedBooking
            every { seatDao.updateSeatsForBooking(any(), any(), SeatStatus.BOOKED) } throws RuntimeException("Seat update error")
            every { seatReservationService.releaseSeats(flightId, any()) } returns true

            // When & Then
            assertThrows<RuntimeException> {
                bookingService.createBooking(bookingRequest)
            }

            verify(exactly = 1) { seatReservationService.releaseSeats(flightId, any()) }
        }
    }

    @Nested
    @DisplayName("Get Booking")
    inner class GetBooking {

        @Test
        fun `should retrieve booking successfully`() {
            // Given
            val bookingId = UUID.randomUUID()
            val booking = TestDataFactory.createTestBooking(bookingId = bookingId)

            val journey = TestDataFactory.createTestJourney(journeyId = booking.journeyId)
            val flights = listOf(TestDataFactory.createTestFlight())
            val seats = listOf(TestDataFactory.createTestSeat())

            every { bookingDao.findById(bookingId) } returns booking
            every { journeyDao.findById(booking.journeyId) } returns journey
            every { flightDao.findById(any()) } returns flights[0]
            every { seatDao.findByBookingId(bookingId) } returns seats

            // When
            val result = bookingService.getBooking(bookingId)

            // Then
            assertNotNull(result)
            assertEquals(bookingId, result.id)
            assertEquals(booking.status, result.status)
        }

        @Test
        fun `should throw exception when booking not found`() {
            // Given
            val nonExistentBookingId = UUID.randomUUID()

            every { bookingDao.findById(nonExistentBookingId) } returns null

            // When & Then
            assertThrows<IllegalArgumentException> {
                bookingService.getBooking(nonExistentBookingId)
            }
        }
    }

    @Nested
    @DisplayName("Payment Integration")
    inner class PaymentIntegration {

        @Test
        fun `should include payment ID in booking response`() {
            // Given
            val paymentId = "payment_test_123"
            val journeyId = UUID.randomUUID()
            val flightId = UUID.randomUUID()

            val journey = TestDataFactory.createTestJourney(
                journeyId = journeyId,
                flightReferences = listOf(FlightReference(flightId, 1))
            )

            val flight = TestDataFactory.createTestFlight(flightId = flightId)
            val availableSeats = listOf(TestDataFactory.createTestSeat(flightId = flightId))

            val bookingRequest = TestDataFactory.createTestBookingRequest(
                journeyId = journeyId,
                passengerCount = 1,
                paymentId = paymentId
            )

            val savedBooking = TestDataFactory.createTestBooking(paymentId = paymentId)

            // Setup mocks
            every { journeyDao.findById(journeyId) } returns journey
            every { flightDao.findById(flightId) } returns flight
            every { seatDao.findAvailableSeatsByFlightId(flightId) } returns availableSeats
            every { seatReservationService.getAvailableSeats(flightId, any()) } returns availableSeats.map { it.seatId }
            every { seatReservationService.reserveSeats(flightId, any(), 300) } returns true
            every { bookingDao.save(any()) } returns savedBooking
            every { seatDao.updateSeatsForBooking(any(), any(), SeatStatus.BOOKED) } returns true
            every { seatDao.findByBookingId(any()) } returns availableSeats
            every { seatReservationService.releaseSeats(flightId, any()) } returns true

            // When
            val result = bookingService.createBooking(bookingRequest)

            // Then
            assertEquals(paymentId, result.paymentId)
        }
    }
}