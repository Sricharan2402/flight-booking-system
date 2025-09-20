package com.flightbooking.services.search

import com.flightbooking.data.JourneyDao
import com.flightbooking.data.SeatDao
import com.flightbooking.data.FlightDao
import com.flightbooking.domain.search.SearchRequest
import com.flightbooking.domain.search.SearchResponse
import com.flightbooking.domain.journeys.Journey
import com.flightbooking.domain.flights.Flight
import com.flightbooking.services.cache.SearchCacheService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class SearchService(
    private val journeyDao: JourneyDao,
    private val seatDao: SeatDao,
    private val flightDao: FlightDao,
    private val searchCacheService: SearchCacheService
) {

    private val logger = LoggerFactory.getLogger(SearchService::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun searchJourneys(request: SearchRequest): SearchResponse {
        logger.info("Searching journeys from ${request.sourceAirport} to ${request.destinationAirport} on ${request.departureDate}")

        // Generate cache key
        val cacheKey = searchCacheService.generateCacheKey(
            request.sourceAirport,
            request.destinationAirport,
            request.departureDate
        )

        // Check cache first
        searchCacheService.getCachedSearchResults(cacheKey)?.let { cachedResponse ->
            logger.debug("Cache hit for key: $cacheKey")
            return filterAndSortCachedResults(cachedResponse, request)
        }

        logger.debug("Cache miss for key: $cacheKey")

        // Perform database search
        val searchResponse = performDatabaseSearch(request)

        // Cache the results
        searchCacheService.cacheSearchResults(cacheKey, searchResponse, 60)

        return searchResponse
    }

    fun searchJourneysWithSeats(request: SearchRequest): Pair<SearchResponse, Map<java.util.UUID, Int>> {
        logger.info("Searching journeys with seat counts from ${request.sourceAirport} to ${request.destinationAirport} on ${request.departureDate}")

        // Generate cache key
        val cacheKey = searchCacheService.generateCacheKey(
            request.sourceAirport,
            request.destinationAirport,
            request.departureDate
        )

        // Check cache first
        searchCacheService.getCachedSearchResults(cacheKey)?.let { cachedResponse ->
            logger.debug("Cache hit for key: $cacheKey")
            val filteredResponse = filterAndSortCachedResults(cachedResponse, request)
            val seatCounts = calculateSeatCountMap(filteredResponse.journeys)
            return Pair(filteredResponse, seatCounts)
        }

        logger.debug("Cache miss for key: $cacheKey")

        // Perform database search
        val searchResponse = performDatabaseSearch(request)

        // Calculate seat counts for the filtered journeys
        val seatCounts = calculateSeatCountMap(searchResponse.journeys)

        // Cache the results
        searchCacheService.cacheSearchResults(cacheKey, searchResponse, 600)

        return Pair(searchResponse, seatCounts)
    }

    private fun filterAndSortCachedResults(cachedResponse: SearchResponse, request: SearchRequest): SearchResponse {
        // Apply passenger filtering and sorting to cached results
        val filteredJourneys = cachedResponse.journeys.filter { journey ->
            calculateAvailableSeats(journey) >= request.passengers
        }

        val sortedJourneys = applySorting(filteredJourneys, request.sortBy)
        val limitedJourneys = applyLimit(sortedJourneys, request.limit)

        logger.info("Returning ${limitedJourneys.size} journeys from cache (total available: ${filteredJourneys.size})")

        return SearchResponse(
            journeys = limitedJourneys,
            totalCount = filteredJourneys.size
        )
    }

    private fun performDatabaseSearch(request: SearchRequest): SearchResponse {
        // Get all journeys for the route and date
        val journeys = journeyDao.findBySourceAndDestinationAndDate(
            request.sourceAirport,
            request.destinationAirport,
            request.departureDate
        )

        logger.debug("Found ${journeys.size} journeys before filtering")

        // Filter journeys by passenger count
        val availableJourneys = journeys.filter { journey ->
            calculateAvailableSeats(journey) >= request.passengers
        }

        logger.debug("Found ${availableJourneys.size} journeys after filtering by availability")

        // Apply sorting
        val sortedResults = applySorting(availableJourneys, request.sortBy)

        // Apply limit
        val limitedResults = applyLimit(sortedResults, request.limit)

        logger.info("Returning ${limitedResults.size} journeys (total available: ${availableJourneys.size})")

        return SearchResponse(
            journeys = limitedResults,
            totalCount = availableJourneys.size
        )
    }

    private fun calculateAvailableSeats(journey: Journey): Int {
        val flightIds = journey.flightDetails.map { it.flightId }
        val seatCounts = mutableListOf<Int>()

        for (flightId in flightIds) {
            val availableSeats = seatDao.countAvailableSeatsByFlightId(flightId)
            seatCounts.add(availableSeats)
        }

        // Return minimum available seats across all flights in the journey
        return seatCounts.minOrNull() ?: 0
    }

    private fun calculateSeatCountMap(journeys: List<Journey>): Map<java.util.UUID, Int> {
        return journeys.associate { journey ->
            journey.journeyId to calculateAvailableSeats(journey)
        }
    }

    private fun getFlightDetails(journey: Journey): List<Flight> {
        val flightIds = journey.flightDetails.map { it.flightId }
        val flights = mutableListOf<Flight>()

        for (flightId in flightIds) {
            val flight = flightDao.findById(flightId)
            if (flight != null) {
                flights.add(flight)
            }
        }

        // Sort flights by their order in the journey
        return flights.sortedBy { flight ->
            journey.flightDetails.find { it.flightId == flight.flightId }?.order ?: 0
        }
    }


    private fun applySorting(journeys: List<Journey>, sortBy: String?): List<Journey> {
        return when (sortBy?.lowercase()) {
            "price" -> journeys.sortedBy { it.totalPrice }
            "duration" -> {
                journeys.sortedBy { journey ->
                    java.time.Duration.between(journey.departureTime, journey.arrivalTime).toMinutes()
                }
            }
            else -> journeys // No sorting or invalid sort parameter
        }
    }

    private fun applyLimit(journeys: List<Journey>, limit: Int?): List<Journey> {
        return if (limit != null && limit > 0) {
            journeys.take(limit)
        } else {
            journeys
        }
    }
}