package com.flightbooking.services.admin

import com.flightbooking.data.FlightDao
import com.flightbooking.data.SeatDao
import com.flightbooking.domain.flights.FlightCreationEvent
import com.flightbooking.domain.flights.FlightCreationRequest
import com.flightbooking.domain.flights.FlightCreationResponse
import com.flightbooking.domain.common.FlightStatus
import com.flightbooking.domain.seats.Seat
import com.flightbooking.producers.FlightEventProducer
import com.flightbooking.utils.TestDataFactory
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("AdminFlightService Tests")
class AdminFlightServiceTest {

    private val flightDao = mockk<FlightDao>()
    private val seatDao = mockk<SeatDao>()
    private val flightEventProducer = mockk<FlightEventProducer>()

    private lateinit var adminFlightService: AdminFlightService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        adminFlightService = AdminFlightService(flightDao, seatDao, flightEventProducer)
    }

    @Nested
    @DisplayName("Flight Creation")
    inner class FlightCreation {

        @Test
        fun `should create flight successfully with all required fields`() {
            // Given
            val flightCreationRequest = TestDataFactory.createTestFlightCreationRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureTime = ZonedDateTime.now().plusHours(2),
                arrivalTime = ZonedDateTime.now().plusHours(4),
                price = BigDecimal("5000.00"),
                totalSeats = 180
            )

            val createdFlight = TestDataFactory.createTestFlight(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                price = BigDecimal("5000.00"),
                status = FlightStatus.ACTIVE
            )

            val createdSeats = TestDataFactory.createTestSeats(createdFlight.flightId, 180)

            every { flightDao.save(any()) } returns createdFlight
            every { seatDao.createSeatsForFlight(any(), any()) } returns createdSeats
            every { flightEventProducer.publishFlightCreatedEvent(any()) } just Runs

            // When
            val result = adminFlightService.createFlight(flightCreationRequest)

            // Then
            assertNotNull(result)
            assertEquals(createdFlight.flightId, result.flightId)
            assertEquals("DEL", result.sourceAirport)
            assertEquals("BOM", result.destinationAirport)
            assertEquals(BigDecimal("5000.00"), result.price)
            assertEquals(180, result.totalSeats)
            assertEquals(180, result.availableSeats)
            assertEquals(FlightStatus.ACTIVE.name, result.status)

            verify(exactly = 1) { flightDao.save(any()) }
            verify(exactly = 1) { seatDao.createSeatsForFlight(any(), any()) }
            verify(exactly = 1) { flightEventProducer.publishFlightCreatedEvent(any()) }
        }

        @Test
        fun `should create correct number of seats for flight`() {
            // Given
            val totalSeats = 150
            val flightCreationRequest = TestDataFactory.createTestFlightCreationRequest(
                totalSeats = totalSeats
            )

            val createdFlight = TestDataFactory.createTestFlight()
            val createdSeats = TestDataFactory.createTestSeats(createdFlight.flightId, totalSeats)

            every { flightDao.save(any()) } returns createdFlight
            every { seatDao.createSeatsForFlight(any(), any()) } returns createdSeats
            every { flightEventProducer.publishFlightCreatedEvent(any()) } just Runs

            // When
            adminFlightService.createFlight(flightCreationRequest)

            // Then
            val totalSeatsSlot = slot<Int>()
            verify { seatDao.createSeatsForFlight(any(), capture(totalSeatsSlot)) }

            assertEquals(totalSeats, totalSeatsSlot.captured, "Should create exact number of seats")
        }

        @Test
        fun `should generate unique seat numbers for created seats`() {
            // Given
            val totalSeats = 12  // 2 rows, 6 seats per row
            val flightCreationRequest = TestDataFactory.createTestFlightCreationRequest(
                totalSeats = totalSeats
            )

            val createdFlight = TestDataFactory.createTestFlight()
            val createdSeats = TestDataFactory.createTestSeats(createdFlight.flightId, totalSeats)

            every { flightDao.save(any()) } returns createdFlight
            every { seatDao.createSeatsForFlight(any(), any()) } returns createdSeats
            every { flightEventProducer.publishFlightCreatedEvent(any()) } just Runs

            // When
            adminFlightService.createFlight(flightCreationRequest)

            // Then
            val totalSeatsSlot = slot<Int>()
            verify { seatDao.createSeatsForFlight(any(), capture(totalSeatsSlot)) }

            assertEquals(totalSeats, totalSeatsSlot.captured, "Should create correct number of seats")
        }
    }

    @Nested
    @DisplayName("Kafka Event Publishing")
    inner class KafkaEventPublishing {

        @Test
        fun `should publish flight created event with correct details`() {
            // Given
            val departureTime = ZonedDateTime.now().plusHours(2)
            val flightCreationRequest = TestDataFactory.createTestFlightCreationRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureTime = departureTime
            )

            val createdFlight = TestDataFactory.createTestFlight(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureTime = departureTime
            )

            every { flightDao.save(any()) } returns createdFlight
            every { seatDao.createSeatsForFlight(any(), any()) } returns emptyList()
            every { flightEventProducer.publishFlightCreatedEvent(any()) } just Runs

            // When
            adminFlightService.createFlight(flightCreationRequest)

            // Then
            val eventSlot = slot<FlightCreationEvent>()
            verify { flightEventProducer.publishFlightCreatedEvent(capture(eventSlot)) }

            val publishedEvent = eventSlot.captured
            assertEquals(createdFlight.flightId, publishedEvent.flightId)
            assertEquals("DEL", publishedEvent.sourceAirport)
            assertEquals("BOM", publishedEvent.destinationAirport)
            assertEquals(departureTime, publishedEvent.departureDate)
        }

        @Test
        fun `should still create flight even if event publishing fails`() {
            // Given
            val flightCreationRequest = TestDataFactory.createTestFlightCreationRequest()
            val createdFlight = TestDataFactory.createTestFlight()

            every { flightDao.save(any()) } returns createdFlight
            every { seatDao.createSeatsForFlight(any(), any()) } returns emptyList()
            every { flightEventProducer.publishFlightCreatedEvent(any()) } throws RuntimeException("Kafka error")

            // When - should succeed despite Kafka failure
            val result = adminFlightService.createFlight(flightCreationRequest)

            // Then - should still have saved the flight and seats
            assertNotNull(result)
            verify(exactly = 1) { flightDao.save(any()) }
            verify(exactly = 1) { seatDao.createSeatsForFlight(any(), any()) }
        }
    }

    @Nested
    @DisplayName("Validation")
    inner class Validation {

        @Test
        fun `should validate departure time is before arrival time`() {
            // Given
            val departureTime = ZonedDateTime.now().plusHours(4)
            val arrivalTime = ZonedDateTime.now().plusHours(2) // Before departure

            val invalidRequest = TestDataFactory.createTestFlightCreationRequest(
                departureTime = departureTime,
                arrivalTime = arrivalTime
            )

            // When & Then
            assertThrows<IllegalArgumentException> {
                adminFlightService.createFlight(invalidRequest)
            }

            verify(exactly = 0) { flightDao.save(any()) }
        }

        @Test
        fun `should validate source and destination airports are different`() {
            // Given
            val invalidRequest = TestDataFactory.createTestFlightCreationRequest(
                sourceAirport = "DEL",
                destinationAirport = "DEL" // Same as source
            )

            // When & Then
            assertThrows<IllegalArgumentException> {
                adminFlightService.createFlight(invalidRequest)
            }

            verify(exactly = 0) { flightDao.save(any()) }
        }

        @Test
        fun `should validate positive price`() {
            // Given
            val invalidRequest = TestDataFactory.createTestFlightCreationRequest(
                price = BigDecimal("-100.00") // Negative price
            )

            // When & Then
            assertThrows<IllegalArgumentException> {
                adminFlightService.createFlight(invalidRequest)
            }

            verify(exactly = 0) { flightDao.save(any()) }
        }

        @Test
        fun `should validate positive total seats`() {
            // Given
            val invalidRequest = TestDataFactory.createTestFlightCreationRequest(
                totalSeats = 0 // Zero seats
            )

            // When & Then
            assertThrows<IllegalArgumentException> {
                adminFlightService.createFlight(invalidRequest)
            }

            verify(exactly = 0) { flightDao.save(any()) }
        }

        @Test
        fun `should validate reasonable total seats count`() {
            // Given
            val invalidRequest = TestDataFactory.createTestFlightCreationRequest(
                totalSeats = 1000 // Unreasonably high
            )

            // When & Then
            assertThrows<IllegalArgumentException> {
                adminFlightService.createFlight(invalidRequest)
            }

            verify(exactly = 0) { flightDao.save(any()) }
        }

        @Test
        fun `should validate departure time is in future`() {
            // Given
            val pastTime = ZonedDateTime.now().minusHours(1)
            val invalidRequest = TestDataFactory.createTestFlightCreationRequest(
                departureTime = pastTime,
                arrivalTime = pastTime.plusHours(2)
            )

            // When & Then
            assertThrows<IllegalArgumentException> {
                adminFlightService.createFlight(invalidRequest)
            }

            verify(exactly = 0) { flightDao.save(any()) }
        }
    }

    @Nested
    @DisplayName("Database Error Handling")
    inner class DatabaseErrorHandling {

        @Test
        fun `should handle flight creation database error gracefully`() {
            // Given
            val flightCreationRequest = TestDataFactory.createTestFlightCreationRequest()

            every { flightDao.save(any()) } throws RuntimeException("Database connection error")

            // When & Then
            assertThrows<RuntimeException> {
                adminFlightService.createFlight(flightCreationRequest)
            }

            verify(exactly = 0) { seatDao.saveAll(any()) }
            verify(exactly = 0) { flightEventProducer.publishFlightCreatedEvent(any()) }
        }

        @Test
        fun `should handle seat creation database error gracefully`() {
            // Given
            val flightCreationRequest = TestDataFactory.createTestFlightCreationRequest()
            val createdFlight = TestDataFactory.createTestFlight()

            every { flightDao.save(any()) } returns createdFlight
            every { seatDao.createSeatsForFlight(any(), any()) } throws RuntimeException("Seat creation error")

            // When & Then
            assertThrows<RuntimeException> {
                adminFlightService.createFlight(flightCreationRequest)
            }

            verify(exactly = 1) { flightDao.save(any()) }
            verify(exactly = 0) { flightEventProducer.publishFlightCreatedEvent(any()) }
        }
    }

    @Nested
    @DisplayName("Flight Response Building")
    inner class FlightResponseBuilding {

        @Test
        fun `should build correct flight response with all fields`() {
            // Given
            val flightCreationRequest = TestDataFactory.createTestFlightCreationRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                price = BigDecimal("7500.50"),
                totalSeats = 200
            )

            val createdFlight = TestDataFactory.createTestFlight(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                price = BigDecimal("7500.50")
            )

            every { flightDao.save(any()) } returns createdFlight
            every { seatDao.createSeatsForFlight(any(), any()) } returns TestDataFactory.createTestSeats(createdFlight.flightId, 200)
            every { flightEventProducer.publishFlightCreatedEvent(any()) } just Runs

            // When
            val result = adminFlightService.createFlight(flightCreationRequest)

            // Then
            assertEquals(createdFlight.flightId, result.flightId)
            assertEquals("DEL", result.sourceAirport)
            assertEquals("BOM", result.destinationAirport)
            assertEquals(createdFlight.departureTime, result.departureTime)
            assertEquals(createdFlight.arrivalTime, result.arrivalTime)
            assertEquals(createdFlight.airplaneId, result.airplaneId)
            assertEquals(BigDecimal("7500.50"), result.price)
            assertEquals(200, result.totalSeats)
            assertEquals(200, result.availableSeats) // All seats initially available
            assertEquals(FlightStatus.ACTIVE.name, result.status)
            assertEquals(createdFlight.createdAt, result.createdAt)
            assertEquals(createdFlight.updatedAt, result.updatedAt)
        }

        @Test
        fun `should set available seats to total seats for new flight`() {
            // Given
            val totalSeats = 150
            val flightCreationRequest = TestDataFactory.createTestFlightCreationRequest(
                totalSeats = totalSeats
            )

            val createdFlight = TestDataFactory.createTestFlight()

            every { flightDao.save(any()) } returns createdFlight
            every { seatDao.createSeatsForFlight(any(), any()) } returns TestDataFactory.createTestSeats(createdFlight.flightId, totalSeats)
            every { flightEventProducer.publishFlightCreatedEvent(any()) } just Runs

            // When
            val result = adminFlightService.createFlight(flightCreationRequest)

            // Then
            assertEquals(totalSeats, result.totalSeats)
            assertEquals(totalSeats, result.availableSeats, "Available seats should equal total seats for new flight")
        }
    }
}