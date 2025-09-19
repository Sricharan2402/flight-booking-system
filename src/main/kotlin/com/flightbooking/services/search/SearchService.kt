package com.flightbooking.services.search

import com.flightbooking.data.JourneyDao
import com.flightbooking.data.SeatDao
import com.flightbooking.data.FlightDao
import com.flightbooking.domain.search.SearchRequest
import com.flightbooking.domain.search.SearchResponse
import com.flightbooking.domain.search.JourneySearchResult
import com.flightbooking.domain.search.FlightSearchResult
import com.flightbooking.domain.journeys.Journey
import com.flightbooking.domain.flights.Flight
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class SearchService(
    private val journeyDao: JourneyDao,
    private val seatDao: SeatDao,
    private val flightDao: FlightDao
) {

    private val logger = LoggerFactory.getLogger(SearchService::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun searchJourneys(request: SearchRequest): SearchResponse {
        logger.info("Searching journeys from ${request.sourceAirport} to ${request.destinationAirport} on ${request.departureDate}")

        // Get all journeys for the route and date
        val journeys = journeyDao.findBySourceAndDestinationAndDate(
            request.sourceAirport,
            request.destinationAirport,
            request.departureDate
        )

        logger.debug("Found ${journeys.size} journeys before filtering")

        // Calculate availability and filter by passenger count
        val journeyResults = mutableListOf<JourneySearchResult>()

        for (journey in journeys) {
            val availableSeats = calculateAvailableSeats(journey)

            if (availableSeats >= request.passengers) {
                val flightDetails = getFlightDetails(journey)
                val journeyResult = mapToSearchResult(journey, flightDetails, availableSeats)
                journeyResults.add(journeyResult)
            }
        }

        logger.debug("Found ${journeyResults.size} journeys after filtering by availability")

        // Apply sorting
        val sortedResults = applySorting(journeyResults, request.sortBy)

        // Apply limit
        val limitedResults = applyLimit(sortedResults, request.limit)

        logger.info("Returning ${limitedResults.size} journeys (total available: ${journeyResults.size})")

        return SearchResponse(
            journeys = limitedResults,
            totalCount = journeyResults.size
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

    private fun mapToSearchResult(journey: Journey, flights: List<Flight>, availableSeats: Int): JourneySearchResult {
        val flightResults = flights.map { flight ->
            FlightSearchResult(
                id = flight.flightId.toString(),
                flightNumber = generateFlightNumber(flight.flightId),
                sourceAirport = flight.sourceAirport,
                destinationAirport = flight.destinationAirport,
                departureTime = flight.departureTime.format(dateTimeFormatter),
                arrivalTime = flight.arrivalTime.format(dateTimeFormatter),
                price = flight.price.toString()
            )
        }

        return JourneySearchResult(
            id = journey.journeyId.toString(),
            departureTime = journey.departureTime.format(dateTimeFormatter),
            arrivalTime = journey.arrivalTime.format(dateTimeFormatter),
            totalPrice = journey.totalPrice.toString(),
            layoverCount = flights.size - 1, // Number of layovers = flights - 1
            flights = flightResults,
            availableSeats = availableSeats
        )
    }

    private fun applySorting(results: List<JourneySearchResult>, sortBy: String?): List<JourneySearchResult> {
        return when (sortBy?.lowercase()) {
            "price" -> results.sortedBy { it.totalPrice.toBigDecimal() }
            "duration" -> {
                results.sortedBy { result ->
                    val departure = java.time.OffsetDateTime.parse(result.departureTime)
                    val arrival = java.time.OffsetDateTime.parse(result.arrivalTime)
                    java.time.Duration.between(departure, arrival).toMinutes()
                }
            }
            else -> results // No sorting or invalid sort parameter
        }
    }

    private fun applyLimit(results: List<JourneySearchResult>, limit: Int?): List<JourneySearchResult> {
        return if (limit != null && limit > 0) {
            results.take(limit)
        } else {
            results
        }
    }

    private fun generateFlightNumber(flightId: java.util.UUID): String {
        // Generate a flight number from UUID (using last 6 characters as flight number)
        val idString = flightId.toString().replace("-", "").uppercase()
        return "FL${idString.takeLast(6)}"
    }
}