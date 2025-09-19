package com.flightbooking.services.journeys

import com.flightbooking.data.FlightDao
import com.flightbooking.data.JourneyDao
import com.flightbooking.domain.common.JourneyStatus
import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.journeys.FlightReference
import com.flightbooking.domain.journeys.Journey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        private const val MAX_DEPTH = 3
    }

    @Transactional
    suspend fun generateJourneysForNewFlight(flightId: UUID) = withContext(Dispatchers.IO) {
        logger.info("Starting BFS journey generation for new flight ID: $flightId")

        val newFlight = flightDao.findById(flightId)
        if (newFlight == null) {
            logger.warn("Flight with ID $flightId not found, skipping journey generation")
            return@withContext
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

            // Convert current path into a Journey object and collect it
            journeysToSave.add(createJourneyFromFlights(currentPath))

            // Stop expanding if reached max depth
            if (currentPath.size >= MAX_DEPTH) continue

            val lastFlight = currentPath.last()
            val forwardConnections = findForwardConnections(lastFlight, sameDayFlights)

            for (nextFlight in forwardConnections) {
                if (!currentPath.any { it.flightId == nextFlight.flightId }) { // prevent cycles
                    queue.add(currentPath + nextFlight)
                }
            }

            val firstFlight = currentPath.first()
            val backwardConnections = findBackwardConnections(firstFlight, sameDayFlights)

            for (prevFlight in backwardConnections) {
                if (!currentPath.any { it.flightId == prevFlight.flightId }) { // prevent cycles
                    queue.add(listOf(prevFlight) + currentPath)
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
