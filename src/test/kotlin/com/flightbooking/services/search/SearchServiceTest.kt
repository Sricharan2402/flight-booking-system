package com.flightbooking.services.search

import com.flightbooking.data.FlightDao
import com.flightbooking.data.JourneyDao
import com.flightbooking.data.SeatDao
import com.flightbooking.domain.search.SearchRequest
import com.flightbooking.domain.search.SearchResponse
import com.flightbooking.domain.journeys.Journey
import com.flightbooking.services.cache.SearchCacheService
import com.flightbooking.utils.TestDataFactory
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("SearchService Tests")
class SearchServiceTest {

    private val journeyDao = mockk<JourneyDao>()
    private val seatDao = mockk<SeatDao>()
    private val flightDao = mockk<FlightDao>()
    private val searchCacheService = mockk<SearchCacheService>()

    private lateinit var searchService: SearchService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        searchService = SearchService(journeyDao, seatDao, flightDao, searchCacheService)
    }

    @Nested
    @DisplayName("Cache Behavior")
    inner class CacheBehavior {

        @Test
        fun `should return cached results when cache hit`() {
            // Given
            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 2
            )

            val cachedJourneys = listOf(
                TestDataFactory.createTestJourney(
                    sourceAirport = "DEL",
                    destinationAirport = "BOM"
                )
            )
            val cachedResponse = SearchResponse(cachedJourneys, 1)

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns cachedResponse
            every { seatDao.countAvailableSeatsByFlightId(any()) } returns 5

            // When
            val result = searchService.searchJourneys(searchRequest)

            // Then
            assertEquals(1, result.journeys.size)
            assertEquals(1, result.totalCount)
            verify(exactly = 0) { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) }
            verify(exactly = 1) { searchCacheService.getCachedSearchResults("test-cache-key") }
        }

        @Test
        fun `should query database and cache results when cache miss`() {
            // Given
            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 2
            )

            val dbJourneys = listOf(
                TestDataFactory.createTestJourney(
                    sourceAirport = "DEL",
                    destinationAirport = "BOM"
                )
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns dbJourneys
            every { seatDao.countAvailableSeatsByFlightId(any()) } returns 5
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val result = searchService.searchJourneys(searchRequest)

            // Then
            assertEquals(1, result.journeys.size)
            verify(exactly = 1) { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) }
            verify(exactly = 1) { searchCacheService.cacheSearchResults("test-cache-key", any(), 600) }
        }

        @Test
        fun `searchJourneysWithSeats should return seat count map`() {
            // Given
            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 2
            )

            val journey = TestDataFactory.createTestJourney(
                sourceAirport = "DEL",
                destinationAirport = "BOM"
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns listOf(journey)
            every { seatDao.countAvailableSeatsByFlightId(any()) } returns 10
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val (response, seatCounts) = searchService.searchJourneysWithSeats(searchRequest)

            // Then
            assertEquals(1, response.journeys.size)
            assertEquals(1, seatCounts.size)
            assertEquals(10, seatCounts[journey.journeyId])
        }
    }

    @Nested
    @DisplayName("Seat Availability Calculation")
    inner class SeatAvailabilityCalculation {

        @Test
        fun `should calculate minimum available seats across all flights in journey`() {
            // Given
            val flight1Id = UUID.randomUUID()
            val flight2Id = UUID.randomUUID()

            val journey = TestDataFactory.createTestJourney(
                flightReferences = listOf(
                    com.flightbooking.domain.journeys.FlightReference(flight1Id, 1),
                    com.flightbooking.domain.journeys.FlightReference(flight2Id, 2)
                )
            )

            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 2
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns listOf(journey)
            every { seatDao.countAvailableSeatsByFlightId(flight1Id) } returns 10
            every { seatDao.countAvailableSeatsByFlightId(flight2Id) } returns 3 // Minimum
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val (_, seatCounts) = searchService.searchJourneysWithSeats(searchRequest)

            // Then
            assertEquals(3, seatCounts[journey.journeyId], "Should return minimum seats across all flights")
        }

        @Test
        fun `should filter out journeys when no seats available`() {
            // Given
            val journey = TestDataFactory.createTestJourney()
            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 2
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns listOf(journey)
            every { seatDao.countAvailableSeatsByFlightId(any()) } returns 0
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val (response, seatCounts) = searchService.searchJourneysWithSeats(searchRequest)

            // Then - Journey should be filtered out since 0 seats < 2 passengers requested
            assertEquals(0, response.journeys.size)
            assertEquals(0, response.totalCount)
            assertEquals(0, seatCounts.size)
        }
    }

    @Nested
    @DisplayName("Passenger Filtering")
    inner class PassengerFiltering {

        @Test
        fun `should filter out journeys with insufficient seats`() {
            // Given
            val journey1 = TestDataFactory.createTestJourney(journeyId = UUID.randomUUID())
            val journey2 = TestDataFactory.createTestJourney(journeyId = UUID.randomUUID())

            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 5 // Requiring 5 passengers
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns listOf(journey1, journey2)
            every { seatDao.countAvailableSeatsByFlightId(journey1.flightDetails[0].flightId) } returns 10 // Sufficient
            every { seatDao.countAvailableSeatsByFlightId(journey2.flightDetails[0].flightId) } returns 3  // Insufficient
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val result = searchService.searchJourneys(searchRequest)

            // Then
            assertEquals(1, result.journeys.size, "Should only return journey with sufficient seats")
            assertEquals(journey1.journeyId, result.journeys[0].journeyId)
        }

        @Test
        fun `should include journeys with exactly matching passenger count`() {
            // Given
            val journey = TestDataFactory.createTestJourney()
            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 5
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns listOf(journey)
            every { seatDao.countAvailableSeatsByFlightId(any()) } returns 5 // Exactly matching
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val result = searchService.searchJourneys(searchRequest)

            // Then
            assertEquals(1, result.journeys.size, "Should include journey with exactly matching seat count")
        }
    }

    @Nested
    @DisplayName("Sorting")
    inner class Sorting {

        @Test
        fun `should sort by price when sortBy is price`() {
            // Given
            val cheapJourney = TestDataFactory.createTestJourney(
                journeyId = UUID.randomUUID(),
                totalPrice = BigDecimal("1000.00")
            )
            val expensiveJourney = TestDataFactory.createTestJourney(
                journeyId = UUID.randomUUID(),
                totalPrice = BigDecimal("5000.00")
            )

            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 1,
                sortBy = "price"
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns listOf(expensiveJourney, cheapJourney)
            every { seatDao.countAvailableSeatsByFlightId(any()) } returns 10
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val result = searchService.searchJourneys(searchRequest)

            // Then
            assertEquals(2, result.journeys.size)
            assertEquals(cheapJourney.journeyId, result.journeys[0].journeyId, "Cheaper journey should be first")
            assertEquals(expensiveJourney.journeyId, result.journeys[1].journeyId, "Expensive journey should be second")
        }

        @Test
        fun `should sort by duration when sortBy is duration`() {
            // Given
            val shortJourney = TestDataFactory.createTestJourney(
                journeyId = UUID.randomUUID(),
                departureTime = ZonedDateTime.now().plusHours(1),
                arrivalTime = ZonedDateTime.now().plusHours(3) // 2 hours
            )
            val longJourney = TestDataFactory.createTestJourney(
                journeyId = UUID.randomUUID(),
                departureTime = ZonedDateTime.now().plusHours(1),
                arrivalTime = ZonedDateTime.now().plusHours(6) // 5 hours
            )

            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 1,
                sortBy = "duration"
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns listOf(longJourney, shortJourney)
            every { seatDao.countAvailableSeatsByFlightId(any()) } returns 10
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val result = searchService.searchJourneys(searchRequest)

            // Then
            assertEquals(2, result.journeys.size)
            assertEquals(shortJourney.journeyId, result.journeys[0].journeyId, "Shorter journey should be first")
            assertEquals(longJourney.journeyId, result.journeys[1].journeyId, "Longer journey should be second")
        }

        @Test
        fun `should not sort when sortBy is null or invalid`() {
            // Given
            val journey1 = TestDataFactory.createTestJourney(journeyId = UUID.randomUUID())
            val journey2 = TestDataFactory.createTestJourney(journeyId = UUID.randomUUID())

            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 1,
                sortBy = null
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns listOf(journey1, journey2)
            every { seatDao.countAvailableSeatsByFlightId(any()) } returns 10
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val result = searchService.searchJourneys(searchRequest)

            // Then
            assertEquals(2, result.journeys.size)
            assertEquals(journey1.journeyId, result.journeys[0].journeyId, "Should maintain original order")
            assertEquals(journey2.journeyId, result.journeys[1].journeyId)
        }
    }

    @Nested
    @DisplayName("Limit Application")
    inner class LimitApplication {

        @Test
        fun `should limit results when limit is specified`() {
            // Given
            val journeys = listOf(
                TestDataFactory.createTestJourney(journeyId = UUID.randomUUID()),
                TestDataFactory.createTestJourney(journeyId = UUID.randomUUID()),
                TestDataFactory.createTestJourney(journeyId = UUID.randomUUID())
            )

            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 1,
                limit = 2
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns journeys
            every { seatDao.countAvailableSeatsByFlightId(any()) } returns 10
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val result = searchService.searchJourneys(searchRequest)

            // Then
            assertEquals(2, result.journeys.size, "Should limit to specified number")
            assertEquals(3, result.totalCount, "Total count should reflect all available journeys")
        }

        @Test
        fun `should return all results when limit is null`() {
            // Given
            val journeys = listOf(
                TestDataFactory.createTestJourney(journeyId = UUID.randomUUID()),
                TestDataFactory.createTestJourney(journeyId = UUID.randomUUID()),
                TestDataFactory.createTestJourney(journeyId = UUID.randomUUID())
            )

            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 1,
                limit = null
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns journeys
            every { seatDao.countAvailableSeatsByFlightId(any()) } returns 10
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val result = searchService.searchJourneys(searchRequest)

            // Then
            assertEquals(3, result.journeys.size, "Should return all journeys when no limit")
            assertEquals(3, result.totalCount)
        }
    }

    @Nested
    @DisplayName("Empty Results Handling")
    inner class EmptyResultsHandling {

        @Test
        fun `should handle empty journey list gracefully`() {
            // Given
            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 1
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns emptyList()
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val result = searchService.searchJourneys(searchRequest)

            // Then
            assertEquals(0, result.journeys.size)
            assertEquals(0, result.totalCount)
            verify(exactly = 1) { searchCacheService.cacheSearchResults(any(), any(), any()) }
        }

        @Test
        fun `should handle all journeys filtered out by passenger count`() {
            // Given
            val journey = TestDataFactory.createTestJourney()
            val searchRequest = SearchRequest(
                sourceAirport = "DEL",
                destinationAirport = "BOM",
                departureDate = LocalDate.now().plusDays(7),
                passengers = 10 // More than available seats
            )

            every { searchCacheService.generateCacheKey(any(), any(), any()) } returns "test-cache-key"
            every { searchCacheService.getCachedSearchResults("test-cache-key") } returns null
            every { journeyDao.findBySourceAndDestinationAndDate(any(), any(), any()) } returns listOf(journey)
            every { seatDao.countAvailableSeatsByFlightId(any()) } returns 5 // Less than requested
            every { searchCacheService.cacheSearchResults(any(), any(), any()) } just Runs

            // When
            val result = searchService.searchJourneys(searchRequest)

            // Then
            assertEquals(0, result.journeys.size, "Should return no journeys when all filtered out")
            assertEquals(0, result.totalCount)
        }
    }
}