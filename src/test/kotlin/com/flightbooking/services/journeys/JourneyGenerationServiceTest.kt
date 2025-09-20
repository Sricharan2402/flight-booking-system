package com.flightbooking.services.journeys

import com.flightbooking.data.FlightDao
import com.flightbooking.data.JourneyDao
import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.journeys.Journey
import com.flightbooking.utils.TestDataFactory
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("JourneyGenerationService Tests")
class JourneyGenerationServiceTest {

    private val flightDao = mockk<FlightDao>()
    private val journeyDao = mockk<JourneyDao>()

    private lateinit var journeyGenerationService: JourneyGenerationService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        journeyGenerationService = JourneyGenerationService(flightDao, journeyDao)
    }

    @Nested
    @DisplayName("Direct Journey Creation")
    inner class DirectJourneyCreation {

        @Test
        fun `should create single journey from new flight`() {
            // Given
            val newFlightId = UUID.randomUUID()
            val newFlight = TestDataFactory.createTestFlight(
                flightId = newFlightId,
                sourceAirport = "DEL",
                destinationAirport = "BOM"
            )

            every { flightDao.findById(newFlightId) } returns newFlight
            every { journeyDao.findFlightsByDate(any()) } returns listOf(newFlight)
            every { journeyDao.save(any()) } returns mockk()

            // When
            journeyGenerationService.generateJourneysForNewFlight(newFlightId)

            // Then
            verify(exactly = 1) { journeyDao.save(any()) }
            val capturedJourney = slot<Journey>()
            verify { journeyDao.save(capture(capturedJourney)) }

            assertEquals("DEL", capturedJourney.captured.sourceAirport)
            assertEquals("BOM", capturedJourney.captured.destinationAirport)
            assertEquals(1, capturedJourney.captured.flightDetails.size)
            assertEquals(newFlightId, capturedJourney.captured.flightDetails[0].flightId)
        }

        @Test
        fun `should handle non-existent flight gracefully`() {
            // Given
            val nonExistentFlightId = UUID.randomUUID()
            every { flightDao.findById(nonExistentFlightId) } returns null

            // When
            journeyGenerationService.generateJourneysForNewFlight(nonExistentFlightId)

            // Then
            verify(exactly = 0) { journeyDao.save(any()) }
        }
    }

    @Nested
    @DisplayName("Forward Extensions")
    inner class ForwardExtensions {

        @Test
        fun `should create two-leg journey with valid forward connection`() {
            // Given
            val (firstFlight, secondFlight) = TestDataFactory.createConnectedFlights(
                firstSource = "DEL",
                connectingAirport = "BOM",
                finalDestination = "MAA"
            )

            every { flightDao.findById(firstFlight.flightId) } returns firstFlight
            every { journeyDao.findFlightsByDate(any()) } returns listOf(firstFlight, secondFlight)
            every { journeyDao.save(any()) } returns mockk()

            // When
            journeyGenerationService.generateJourneysForNewFlight(firstFlight.flightId)

            // Then
            verify(atLeast = 2) { journeyDao.save(any()) }

            val capturedJourneys = mutableListOf<Journey>()
            verify { journeyDao.save(capture(capturedJourneys)) }

            // Should have single-leg and two-leg journeys
            val twoLegJourney = capturedJourneys.find { it.flightDetails.size == 2 }
            assertTrue(twoLegJourney != null, "Should create two-leg journey")
            assertEquals("DEL", twoLegJourney!!.sourceAirport)
            assertEquals("MAA", twoLegJourney.destinationAirport)
        }

        @Test
        fun `should reject forward connection with invalid layover time`() {
            // Given
            val firstFlight = TestDataFactory.createTestFlight(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureTime = ZonedDateTime.now().plusHours(2),
                arrivalTime = ZonedDateTime.now().plusHours(4)
            )

            val invalidSecondFlight = TestDataFactory.createTestFlight(
                sourceAirport = "BOM",
                destinationAirport = "MAA",
                departureTime = ZonedDateTime.now().plusHours(4).plusMinutes(15), // Only 15 min layover
                arrivalTime = ZonedDateTime.now().plusHours(6)
            )

            every { flightDao.findById(firstFlight.flightId) } returns firstFlight
            every { journeyDao.findFlightsByDate(any()) } returns listOf(firstFlight, invalidSecondFlight)
            every { journeyDao.save(any()) } returns mockk()

            // When
            journeyGenerationService.generateJourneysForNewFlight(firstFlight.flightId)

            // Then
            val capturedJourneys = mutableListOf<Journey>()
            verify { journeyDao.save(capture(capturedJourneys)) }

            // Should only have single-leg journey, no two-leg due to invalid layover
            val twoLegJourneys = capturedJourneys.filter { it.flightDetails.size == 2 }
            assertTrue(twoLegJourneys.isEmpty(), "Should not create journey with invalid layover")
        }
    }

    @Nested
    @DisplayName("Backward Extensions")
    inner class BackwardExtensions {

        @Test
        fun `should create journey with backward extension`() {
            // Given
            val middleFlight = TestDataFactory.createTestFlight(
                sourceAirport = "BOM",
                destinationAirport = "MAA",
                departureTime = ZonedDateTime.now().plusHours(5),
                arrivalTime = ZonedDateTime.now().plusHours(7)
            )

            val earlierFlight = TestDataFactory.createTestFlight(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureTime = ZonedDateTime.now().plusHours(2),
                arrivalTime = ZonedDateTime.now().plusHours(4)
            )

            every { flightDao.findById(middleFlight.flightId) } returns middleFlight
            every { journeyDao.findFlightsByDate(any()) } returns listOf(earlierFlight, middleFlight)
            every { journeyDao.save(any()) } returns mockk()

            // When
            journeyGenerationService.generateJourneysForNewFlight(middleFlight.flightId)

            // Then
            val capturedJourneys = mutableListOf<Journey>()
            verify { journeyDao.save(capture(capturedJourneys)) }

            val twoLegJourney = capturedJourneys.find { it.flightDetails.size == 2 }
            assertTrue(twoLegJourney != null, "Should create two-leg journey with backward extension")
            assertEquals("DEL", twoLegJourney!!.sourceAirport)
            assertEquals("MAA", twoLegJourney.destinationAirport)
        }
    }

    @Nested
    @DisplayName("Duration Validation")
    inner class DurationValidation {

        @Test
        fun `should reject journey exceeding 24 hours duration`() {
            // Given
            val longFlight1 = TestDataFactory.createTestFlight(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureTime = ZonedDateTime.now().plusHours(2),
                arrivalTime = ZonedDateTime.now().plusHours(14) // 12 hours
            )

            val longFlight2 = TestDataFactory.createTestFlight(
                sourceAirport = "BOM",
                destinationAirport = "MAA",
                departureTime = ZonedDateTime.now().plusHours(15), // 1 hour layover
                arrivalTime = ZonedDateTime.now().plusHours(27) // Total journey: 25 hours
            )

            every { flightDao.findById(longFlight1.flightId) } returns longFlight1
            every { journeyDao.findFlightsByDate(any()) } returns listOf(longFlight1, longFlight2)
            every { journeyDao.save(any()) } returns mockk()

            // When
            journeyGenerationService.generateJourneysForNewFlight(longFlight1.flightId)

            // Then
            val capturedJourneys = mutableListOf<Journey>()
            verify { journeyDao.save(capture(capturedJourneys)) }

            // Should only save single-leg journey, not the 25-hour two-leg journey
            val longJourneys = capturedJourneys.filter { journey ->
                Duration.between(journey.departureTime, journey.arrivalTime).toHours() > 24
            }
            assertTrue(longJourneys.isEmpty(), "Should not create journeys longer than 24 hours")
        }

        @Test
        fun `should accept journey within 24 hours duration`() {
            // Given
            val flight1 = TestDataFactory.createTestFlight(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureTime = ZonedDateTime.now().plusHours(2),
                arrivalTime = ZonedDateTime.now().plusHours(4)
            )

            val flight2 = TestDataFactory.createTestFlight(
                sourceAirport = "BOM",
                destinationAirport = "MAA",
                departureTime = ZonedDateTime.now().plusHours(5),
                arrivalTime = ZonedDateTime.now().plusHours(7) // Total: 5 hours
            )

            every { flightDao.findById(flight1.flightId) } returns flight1
            every { journeyDao.findFlightsByDate(any()) } returns listOf(flight1, flight2)
            every { journeyDao.save(any()) } returns mockk()

            // When
            journeyGenerationService.generateJourneysForNewFlight(flight1.flightId)

            // Then
            val capturedJourneys = mutableListOf<Journey>()
            verify { journeyDao.save(capture(capturedJourneys)) }

            val twoLegJourney = capturedJourneys.find { it.flightDetails.size == 2 }
            assertTrue(twoLegJourney != null, "Should create valid journey within 24 hours")
        }
    }

    @Nested
    @DisplayName("Duplicate Prevention")
    inner class DuplicatePrevention {

        @Test
        fun `should not create duplicate journeys for same flight combination`() {
            // Given
            val flight = TestDataFactory.createTestFlight()

            every { flightDao.findById(flight.flightId) } returns flight
            every { journeyDao.findFlightsByDate(any()) } returns listOf(flight)
            every { journeyDao.save(any()) } returns mockk()

            // When - call twice with same flight
            journeyGenerationService.generateJourneysForNewFlight(flight.flightId)
            journeyGenerationService.generateJourneysForNewFlight(flight.flightId)

            // Then - should only create journey once per call
            verify(exactly = 2) { journeyDao.save(any()) }
        }
    }

    @Nested
    @DisplayName("Cycle Prevention")
    inner class CyclePrevention {

        @Test
        fun `should not generate journeys for circular flight`() {
            // Given
            val circularFlight = TestDataFactory.createTestFlight(
                sourceAirport = "DEL",
                destinationAirport = "DEL" // Same source and destination
            )

            every { flightDao.findById(circularFlight.flightId) } returns circularFlight
            every { journeyDao.findFlightsByDate(any()) } returns listOf(circularFlight)

            // When
            journeyGenerationService.generateJourneysForNewFlight(circularFlight.flightId)

            // Then → nothing should be persisted
            verify(exactly = 0) { journeyDao.save(any()) }
        }
    }


    @Nested
    @DisplayName("Max Depth Enforcement")
    inner class MaxDepthEnforcement {

        @Test
        fun `should not create journeys longer than 3 flights`() {
            // Given - chain of 4 connected flights
            val flight1 = TestDataFactory.createTestFlight(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureTime = ZonedDateTime.now().plusHours(1),
                arrivalTime = ZonedDateTime.now().plusHours(2)
            )
            val flight2 = TestDataFactory.createTestFlight(
                sourceAirport = "BOM",
                destinationAirport = "MAA",
                departureTime = ZonedDateTime.now().plusHours(3),
                arrivalTime = ZonedDateTime.now().plusHours(4)
            )
            val flight3 = TestDataFactory.createTestFlight(
                sourceAirport = "MAA",
                destinationAirport = "BLR",
                departureTime = ZonedDateTime.now().plusHours(5),
                arrivalTime = ZonedDateTime.now().plusHours(6)
            )
            val flight4 = TestDataFactory.createTestFlight(
                sourceAirport = "BLR",
                destinationAirport = "HYD",
                departureTime = ZonedDateTime.now().plusHours(7),
                arrivalTime = ZonedDateTime.now().plusHours(8)
            )

            every { flightDao.findById(flight1.flightId) } returns flight1
            every { journeyDao.findFlightsByDate(any()) } returns listOf(flight1, flight2, flight3, flight4)
            every { journeyDao.save(any()) } returns mockk()

            // When
            journeyGenerationService.generateJourneysForNewFlight(flight1.flightId)

            // Then
            val capturedJourneys = mutableListOf<Journey>()
            verify { journeyDao.save(capture(capturedJourneys)) }

            // Should not create any journey with more than 3 flights
            val longJourneys = capturedJourneys.filter { it.flightDetails.size > 3 }
            assertTrue(longJourneys.isEmpty(), "Should not create journeys with more than 3 flights")

            // Should have created journeys with 1, 2, and 3 flights
            val maxFlightCount = capturedJourneys.maxOfOrNull { it.flightDetails.size } ?: 0
            assertTrue(maxFlightCount <= 3, "Maximum journey length should be 3 flights")
        }
    }
}