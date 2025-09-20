package com.flightbooking.utils

import com.flightbooking.data.JourneyDao
import com.flightbooking.domain.common.FlightStatus
import com.flightbooking.domain.common.JourneyStatus
import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.journeys.FlightReference
import com.flightbooking.domain.journeys.Journey
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.util.*
import kotlin.random.Random

class RandomJourneyGenerator(
    private val journeyDao: JourneyDao
) {

    private val logger = LoggerFactory.getLogger(RandomJourneyGenerator::class.java)

    companion object {
        const val MIN_LAYOVER_MINUTES = 30
        const val MAX_LAYOVER_HOURS = 4
        const val MAX_JOURNEY_FLIGHTS = 3
    }

    fun generateRandomJourneys(
        journeyCount: Int,
        availableFlights: List<Flight>,
        maxFlightsPerJourney: Int = MAX_JOURNEY_FLIGHTS
    ): List<Journey> {
        logger.info(
            "Generating {} random journeys from {} available flights, max {} flights per journey",
            journeyCount, availableFlights.size, maxFlightsPerJourney
        )

        if (availableFlights.isEmpty()) {
            logger.warn("No flights available for journey generation")
            return emptyList()
        }

        val journeys = mutableListOf<Journey>()
        val flightsByAirport = groupFlightsBySourceAirport(availableFlights)
        val existingJourneySignatures = mutableSetOf<String>()

        var attempts = 0
        val maxAttempts = journeyCount * 5 // Prevent infinite loops

        while (journeys.size < journeyCount && attempts < maxAttempts) {
            attempts++

            val journeyLength = Random.nextInt(1, maxFlightsPerJourney + 1)
            val journey = when (journeyLength) {
                1 -> generateSingleFlightJourney(availableFlights, existingJourneySignatures)
                2 -> generateTwoFlightJourney(flightsByAirport, existingJourneySignatures)
                3 -> generateThreeFlightJourney(flightsByAirport, existingJourneySignatures)
                else -> null
            }

            journey?.let {
                if (isValidJourney(it) && addUniqueJourney(it, journeys, existingJourneySignatures)) {
                    logger.debug(
                        "Generated {}-flight journey: {} -> {}",
                        it.flightDetails.size, it.sourceAirport, it.destinationAirport
                    )
                }
            }
        }

        if (journeys.size < journeyCount) {
            logger.warn(
                "Could only generate {} out of {} requested journeys after {} attempts",
                journeys.size, journeyCount, attempts
            )
        }

        return persistJourneys(journeys)
    }

    private fun groupFlightsBySourceAirport(flights: List<Flight>): Map<String, List<Flight>> {
        return flights.groupBy { it.sourceAirport }
    }

    private fun generateSingleFlightJourney(
        availableFlights: List<Flight>,
        existingSignatures: Set<String>
    ): Journey? {
        val maxAttempts = 10
        repeat(maxAttempts) {
            val flight = availableFlights.random()
            val signature = flight.flightId.toString()

            if (!existingSignatures.contains(signature)) {
                return createJourneyFromFlights(listOf(flight))
            }
        }
        return null
    }

    private fun generateTwoFlightJourney(
        flightsByAirport: Map<String, List<Flight>>,
        existingSignatures: Set<String>
    ): Journey? {
        val maxAttempts = 20

        repeat(maxAttempts) {
            val firstFlight = flightsByAirport.values.flatten().randomOrNull() ?: return null
            val connectingFlights = flightsByAirport[firstFlight.destinationAirport] ?: emptyList()

            val validConnections = connectingFlights.filter { secondFlight ->
                isValidLayover(firstFlight.arrivalTime, secondFlight.departureTime) &&
                        secondFlight.destinationAirport != firstFlight.sourceAirport
            }

            if (validConnections.isNotEmpty()) {
                val secondFlight = validConnections.random()
                val flights = listOf(firstFlight, secondFlight)
                val signature = createJourneySignature(flights)

                if (!existingSignatures.contains(signature)) {
                    return createJourneyFromFlights(flights)
                }
            }
        }
        return null
    }

    private fun generateThreeFlightJourney(
        flightsByAirport: Map<String, List<Flight>>,
        existingSignatures: Set<String>
    ): Journey? {
        val maxAttempts = 30

        repeat(maxAttempts) {
            val firstFlight = flightsByAirport.values.flatten().randomOrNull() ?: return null
            val secondFlights = flightsByAirport[firstFlight.destinationAirport] ?: emptyList()

            val validSecondFlights = secondFlights.filter { secondFlight ->
                isValidLayover(firstFlight.arrivalTime, secondFlight.departureTime) &&
                        secondFlight.destinationAirport != firstFlight.sourceAirport
            }

            if (validSecondFlights.isEmpty()) return@repeat

            val secondFlight = validSecondFlights.random()
            val thirdFlights = flightsByAirport[secondFlight.destinationAirport] ?: emptyList()

            val validThirdFlights = thirdFlights.filter { thirdFlight ->
                isValidLayover(secondFlight.arrivalTime, thirdFlight.departureTime) &&
                        thirdFlight.destinationAirport != firstFlight.sourceAirport &&
                        thirdFlight.destinationAirport != secondFlight.sourceAirport
            }

            if (validThirdFlights.isNotEmpty()) {
                val thirdFlight = validThirdFlights.random()
                val flights = listOf(firstFlight, secondFlight, thirdFlight)
                val signature = createJourneySignature(flights)

                if (!existingSignatures.contains(signature)) {
                    return createJourneyFromFlights(flights)
                }
            }
        }
        return null
    }

    private fun createJourneyFromFlights(flights: List<Flight>): Journey {
        val flightDetails = flights.mapIndexed { index, flight ->
            FlightReference(flight.flightId, index + 1)
        }

        val totalPrice = flights.sumOf { it.price }

        return Journey(
            journeyId = UUID.randomUUID(),
            flightDetails = flightDetails,
            sourceAirport = flights.first().sourceAirport,
            destinationAirport = flights.last().destinationAirport,
            departureTime = flights.first().departureTime,
            arrivalTime = flights.last().arrivalTime,
            totalPrice = totalPrice,
            status = JourneyStatus.ACTIVE,
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now()
        )
    }

    private fun isValidLayover(arrivalTime: ZonedDateTime, departureTime: ZonedDateTime): Boolean {
        val layoverMinutes = java.time.Duration.between(arrivalTime, departureTime).toMinutes()
        return layoverMinutes in MIN_LAYOVER_MINUTES..(MAX_LAYOVER_HOURS * 60)
    }

    private fun isValidJourney(journey: Journey): Boolean {
        if (journey.sourceAirport == journey.destinationAirport) return false
        if (journey.flightDetails.isEmpty() || journey.flightDetails.size > MAX_JOURNEY_FLIGHTS) return false
        if (journey.totalPrice.signum() <= 0) return false
        if (journey.departureTime.isAfter(journey.arrivalTime)) return false
        return true
    }

    private fun createJourneySignature(flights: List<Flight>): String {
        // NOTE: If you want uniqueness by "set of flights", keep sorted()
        // If you want uniqueness by "path order", remove sorted() and just join in given order
        return flights.map { it.flightId }.joinToString(",")
    }

    private fun addUniqueJourney(
        journey: Journey,
        journeys: MutableList<Journey>,
        existingSignatures: MutableSet<String>
    ): Boolean {
        val signature = createJourneySignature(journey.flightDetails.map {
            Flight(
                it.flightId,
                "",
                "",
                ZonedDateTime.now(),
                ZonedDateTime.now(),
                UUID.randomUUID(),
                it.order.toBigDecimal(),
                FlightStatus.ACTIVE,
                createdAt = ZonedDateTime.now(),
                updatedAt = ZonedDateTime.now()
            )
        })
        return if (existingSignatures.add(signature)) {
            journeys.add(journey)
            true
        } else {
            false
        }
    }

    private fun persistJourneys(journeys: List<Journey>): List<Journey> {
        logger.info("Persisting {} journeys to database", journeys.size)
        val persistedJourneys = mutableListOf<Journey>()

        journeys.forEach { journey ->
            try {
                val saved = journeyDao.save(journey)
                persistedJourneys.add(saved)
            } catch (e: Exception) {
                logger.warn(
                    "Failed to persist journey {} -> {}: {}",
                    journey.sourceAirport, journey.destinationAirport, e.message
                )
            }
        }

        logger.info("Successfully persisted {} out of {} journeys", persistedJourneys.size, journeys.size)
        return persistedJourneys
    }

    fun generateJourneysDistribution(
        totalJourneys: Int,
        singleFlightRatio: Double = 0.6,
        twoFlightRatio: Double = 0.3,
        threeFlightRatio: Double = 0.1
    ): JourneyDistribution {
        val singleFlightCount = (totalJourneys * singleFlightRatio).toInt()
        val twoFlightCount = (totalJourneys * twoFlightRatio).toInt()
        val threeFlightCount = totalJourneys - singleFlightCount - twoFlightCount

        return JourneyDistribution(
            singleFlightJourneys = singleFlightCount,
            twoFlightJourneys = twoFlightCount,
            threeFlightJourneys = threeFlightCount,
            totalJourneys = totalJourneys
        )
    }

    fun generateJourneysWithDistribution(
        distribution: JourneyDistribution,
        availableFlights: List<Flight>
    ): List<Journey> {
        logger.info(
            "Generating journeys with distribution: {} single, {} two-flight, {} three-flight",
            distribution.singleFlightJourneys, distribution.twoFlightJourneys, distribution.threeFlightJourneys
        )

        val journeys = mutableListOf<Journey>()
        val flightsByAirport = groupFlightsBySourceAirport(availableFlights)
        val existingSignatures = mutableSetOf<String>()

        repeat(distribution.singleFlightJourneys) {
            val journey = generateSingleFlightJourney(availableFlights, existingSignatures)
            journey?.let {
                if (isValidJourney(it) && addUniqueJourney(it, journeys, existingSignatures)) {
                    // Added
                }
            }
        }

        repeat(distribution.twoFlightJourneys) {
            val journey = generateTwoFlightJourney(flightsByAirport, existingSignatures)
            journey?.let {
                if (isValidJourney(it) && addUniqueJourney(it, journeys, existingSignatures)) {
                    // Added
                }
            }
        }

        repeat(distribution.threeFlightJourneys) {
            val journey = generateThreeFlightJourney(flightsByAirport, existingSignatures)
            journey?.let {
                if (isValidJourney(it) && addUniqueJourney(it, journeys, existingSignatures)) {
                    // Added
                }
            }
        }

        return persistJourneys(journeys)
    }
}

data class JourneyDistribution(
    val singleFlightJourneys: Int,
    val twoFlightJourneys: Int,
    val threeFlightJourneys: Int,
    val totalJourneys: Int
)
