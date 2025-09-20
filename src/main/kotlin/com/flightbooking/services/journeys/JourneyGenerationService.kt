package com.flightbooking.services.journeys

import com.flightbooking.data.FlightDao
import com.flightbooking.data.JourneyDao
import com.flightbooking.domain.common.JourneyStatus
import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.journeys.FlightReference
import com.flightbooking.domain.journeys.Journey
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.time.Duration
import java.time.ZonedDateTime

@Service
class JourneyGenerationService(
    private val flightDao: FlightDao,
    private val journeyDao: JourneyDao
) {

    private val logger = LoggerFactory.getLogger(JourneyGenerationService::class.java)

    companion object {
        private val MIN_LAYOVER_DURATION = Duration.ofMinutes(30)
        private val MAX_LAYOVER_DURATION = Duration.ofHours(4)
        private val MAX_JOURNEY_DURATION = Duration.ofHours(24)
        private const val MAX_DEPTH = 3
    }

    fun generateJourneysForNewFlight(flightId: UUID) {
        logger.info("Starting BFS journey generation for new flight ID: $flightId")

        val newFlight = flightDao.findById(flightId)
        if (newFlight == null) {
            logger.warn("Flight with ID $flightId not found, skipping journey generation")
            return
        }

        val flightDate = newFlight.departureTime.toLocalDate()
        val sameDayFlights = journeyDao.findFlightsByDate(flightDate)

        val generatedJourneys = mutableSetOf<String>()
        val journeysToSave = mutableListOf<Journey>()

        // BFS queue: each element is a path of flights forming a partial journey
        val queue: ArrayDeque<List<Flight>> = ArrayDeque()

        // Start BFS with the new flight as the initial path
        queue.add(listOf(newFlight))

        while (queue.isNotEmpty()) {
            val currentPath = queue.removeFirst()
            val signature = createJourneySignature(currentPath.map { it.flightId })

            // Skip duplicates
            if (!generatedJourneys.add(signature)) continue

            // Validate journey duration before proceeding
            if (!isValidJourneyDuration(currentPath)) {
                logger.debug("Skipping journey with excessive duration: ${Duration.between(currentPath.first().departureTime, currentPath.last().arrivalTime)}")
                continue
            }

            // Validate and convert current path into a Journey object
            val journey = createJourneyFromFlights(currentPath)
            if (isValidJourney(journey)) {
                journeysToSave.add(journey)
            } else {
                logger.debug("Skipping invalid journey (same source/destination): ${journey.sourceAirport} → ${journey.destinationAirport}")
            }

            // Stop expanding if reached max depth
            if (currentPath.size >= MAX_DEPTH) continue

            val lastFlight = currentPath.last()
            val forwardConnections = findForwardConnections(lastFlight, sameDayFlights)

            for (nextFlight in forwardConnections) {
                if (!currentPath.any { it.flightId == nextFlight.flightId }) { // prevent cycles
                    val newPath = currentPath + nextFlight
                    // Check duration before adding to queue to avoid processing invalid paths
                    if (isValidJourneyDuration(newPath)) {
                        queue.add(newPath)
                    } else {
                        logger.debug("Rejecting forward connection due to duration limit: ${Duration.between(newPath.first().departureTime, newPath.last().arrivalTime)}")
                    }
                }
            }

            val firstFlight = currentPath.first()
            val backwardConnections = findBackwardConnections(firstFlight, sameDayFlights)

            for (prevFlight in backwardConnections) {
                if (!currentPath.any { it.flightId == prevFlight.flightId }) { // prevent cycles
                    val newPath = listOf(prevFlight) + currentPath
                    // Check duration before adding to queue to avoid processing invalid paths
                    if (isValidJourneyDuration(newPath)) {
                        queue.add(newPath)
                    } else {
                        logger.debug("Rejecting backward connection due to duration limit: ${Duration.between(newPath.first().departureTime, newPath.last().arrivalTime)}")
                    }
                }
            }
        }

        // Persist journeys
        for (journey in journeysToSave) {
            try {
                journeyDao.save(journey)
            } catch (e: Exception) {
                logger.error("Failed to save journey: ${journey.journeyId}", e)
            }
        }

        logger.info("Completed BFS journey generation for flight $flightId. Generated ${journeysToSave.size} journeys")
    }

    private fun findForwardConnections(flight: Flight, allFlights: List<Flight>): List<Flight> {
        return allFlights.filter { candidateFlight ->
            candidateFlight.sourceAirport == flight.destinationAirport &&
                    isValidLayover(flight.arrivalTime, candidateFlight.departureTime)
        }
    }

    private fun findBackwardConnections(flight: Flight, allFlights: List<Flight>): List<Flight> {
        return allFlights.filter { candidateFlight ->
            candidateFlight.destinationAirport == flight.sourceAirport &&
                    isValidLayover(candidateFlight.arrivalTime, flight.departureTime)
        }
    }

    private fun isValidLayover(arrivalTime: ZonedDateTime, departureTime: ZonedDateTime): Boolean {
        val layoverDuration = Duration.between(arrivalTime, departureTime)
        return layoverDuration >= MIN_LAYOVER_DURATION && layoverDuration <= MAX_LAYOVER_DURATION
    }

    private fun isValidJourneyDuration(flights: List<Flight>): Boolean {
        if (flights.isEmpty()) return true

        val journeyDuration = Duration.between(
            flights.first().departureTime,
            flights.last().arrivalTime
        )

        return journeyDuration <= MAX_JOURNEY_DURATION
    }

    /**
     * Validates that a journey meets business rules:
     * - Source and destination airports must be different
     * - Additional validations can be added here
     */
    private fun isValidJourney(journey: Journey): Boolean {
        // Ensure source and destination airports are different
        if (journey.sourceAirport == journey.destinationAirport) {
            return false
        }

        // Additional journey validations can be added here
        // e.g., minimum flight count, price validation, etc.

        return true
    }

    private fun createJourneyFromFlights(flights: List<Flight>): Journey {
        return Journey(
            journeyId = UUID.randomUUID(),
            flightDetails = flights.mapIndexed { idx, f -> FlightReference(f.flightId, idx + 1) },
            sourceAirport = flights.first().sourceAirport,
            destinationAirport = flights.last().destinationAirport,
            departureTime = flights.first().departureTime,
            arrivalTime = flights.last().arrivalTime,
            totalPrice = flights.sumOf { it.price },
            status = JourneyStatus.ACTIVE,
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now()
        )
    }

    private fun createJourneySignature(flightIds: List<UUID>): String {
        // Ordered concatenation ensures uniqueness for paths
        return flightIds.joinToString(",")
    }
}
