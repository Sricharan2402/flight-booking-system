package com.flightbooking.utils

import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.flights.FlightCreationRequest
import com.flightbooking.data.FlightDao
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.random.Random

class RandomFlightNetworkGenerator(
    private val flightDao: FlightDao
) {

    private val logger = LoggerFactory.getLogger(RandomFlightNetworkGenerator::class.java)

    companion object {
        // Major airport codes for realistic flight network
        val MAJOR_AIRPORTS = listOf(
            "DEL", "BOM", "BLR", "MAA", "CCU", "HYD", "AMD", "COK", "GOI", "PNQ",
            "JAI", "LKO", "IXC", "GAU", "IXR", "IXS", "IXA", "IXB", "IXM", "IXZ"
        )

        // Price ranges for different route types
        val DOMESTIC_PRICE_RANGE = 3000..15000
        val POPULAR_ROUTE_PRICE_RANGE = 4000..8000
        val REGIONAL_PRICE_RANGE = 2500..6000

        // Timing constraints
        val FLIGHT_DURATION_HOURS_RANGE = 1..4
        val MIN_LAYOVER_MINUTES = 30
        val MAX_LAYOVER_HOURS = 4
    }

    fun generateRandomFlightNetwork(
        flightCount: Int,
        baseDate: LocalDate = LocalDate.now().plusDays(1),
        ensureConnectivity: Boolean = true
    ): List<Flight> {
        logger.info("Generating random flight network: {} flights, baseDate={}, ensureConnectivity={}",
            flightCount, baseDate, ensureConnectivity)

        val flights = mutableListOf<Flight>()
        val airportPairTracker = mutableSetOf<Pair<String, String>>()

        // Generate backbone connectivity first (if requested)
        if (ensureConnectivity && flightCount >= 20) {
            val backboneFlights = generateBackboneConnectivity(baseDate, airportPairTracker)
            flights.addAll(backboneFlights)
            logger.info("Generated {} backbone flights for connectivity", backboneFlights.size)
        }

        // Fill remaining slots with random flights
        val remainingFlights = flightCount - flights.size
        val randomFlights = generateRandomFlights(remainingFlights, baseDate, airportPairTracker)
        flights.addAll(randomFlights)

        // Shuffle to avoid predictable patterns
        flights.shuffle()

        logger.info("Generated {} total flights across {} unique routes",
            flights.size, airportPairTracker.size)

        return persistFlights(flights)
    }

    private fun generateBackboneConnectivity(
        baseDate: LocalDate,
        airportPairTracker: MutableSet<Pair<String, String>>
    ): List<Flight> {
        val backboneFlights = mutableListOf<Flight>()
        val majorHubs = MAJOR_AIRPORTS.take(8) // Use top 8 airports as hubs

        // Create hub-to-hub connections (bidirectional)
        for (i in majorHubs.indices) {
            for (j in i + 1 until majorHubs.size) {
                val source = majorHubs[i]
                val dest = majorHubs[j]

                // Forward direction
                if (airportPairTracker.add(Pair(source, dest))) {
                    backboneFlights.add(createRandomFlight(source, dest, baseDate))
                }

                // Reverse direction (50% chance to avoid too many routes)
                if (Random.nextBoolean() && airportPairTracker.add(Pair(dest, source))) {
                    backboneFlights.add(createRandomFlight(dest, source, baseDate))
                }
            }
        }

        // Connect smaller airports to hubs
        val smallerAirports = MAJOR_AIRPORTS.drop(8)
        smallerAirports.forEach { airport ->
            val randomHub = majorHubs.random()

            // Connection to hub
            if (airportPairTracker.add(Pair(airport, randomHub))) {
                backboneFlights.add(createRandomFlight(airport, randomHub, baseDate))
            }

            // Connection from hub (70% chance)
            if (Random.nextDouble() < 0.7 && airportPairTracker.add(Pair(randomHub, airport))) {
                backboneFlights.add(createRandomFlight(randomHub, airport, baseDate))
            }
        }

        return backboneFlights
    }

    private fun generateRandomFlights(
        count: Int,
        baseDate: LocalDate,
        airportPairTracker: MutableSet<Pair<String, String>>
    ): List<Flight> {
        val randomFlights = mutableListOf<Flight>()
        var attempts = 0
        val maxAttempts = count * 3 // Prevent infinite loops

        while (randomFlights.size < count && attempts < maxAttempts) {
            attempts++

            val source = MAJOR_AIRPORTS.random()
            val dest = MAJOR_AIRPORTS.filter { it != source }.random()
            val routePair = Pair(source, dest)

            // Avoid duplicate routes but allow some overlap
            if (airportPairTracker.contains(routePair)) {
                if (Random.nextDouble() < 0.1) { // 10% chance to allow duplicate routes
                    randomFlights.add(createRandomFlight(source, dest, baseDate, allowDuplicateRoute = true))
                }
                continue
            }

            airportPairTracker.add(routePair)
            randomFlights.add(createRandomFlight(source, dest, baseDate))
        }

        if (randomFlights.size < count) {
            logger.warn("Could only generate {} out of {} requested random flights", randomFlights.size, count)
        }

        return randomFlights
    }

    private fun createRandomFlight(
        source: String,
        dest: String,
        baseDate: LocalDate,
        allowDuplicateRoute: Boolean = false
    ): Flight {
        // Random time within the day (6 AM to 10 PM)
        val departureHour = Random.nextInt(6, 23)
        val departureMinute = Random.nextInt(0, 60)

        val departureTime = baseDate.atTime(departureHour, departureMinute)
            .atZone(ZoneId.systemDefault())

        // Flight duration based on route type
        val flightDurationMinutes = when {
            isPopularRoute(source, dest) -> Random.nextInt(60, 180) // 1-3 hours
            else -> Random.nextInt(45, 240) // 45 minutes - 4 hours
        }

        val arrivalTime = departureTime.plusMinutes(flightDurationMinutes.toLong())

        // Price based on route popularity and flight duration
        val basePrice = when {
            isPopularRoute(source, dest) -> POPULAR_ROUTE_PRICE_RANGE.random()
            flightDurationMinutes > 120 -> DOMESTIC_PRICE_RANGE.random()
            else -> REGIONAL_PRICE_RANGE.random()
        }

        // Add some price variation
        val priceVariation = Random.nextInt(-500, 1000)
        val finalPrice = (basePrice + priceVariation).coerceAtLeast(1500)

        val request = FlightCreationRequest(
            sourceAirport = source,
            destinationAirport = dest,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            airplaneId = UUID.randomUUID(),
            price = BigDecimal(finalPrice),
            totalSeats = Random.nextInt(120, 300)
        )

        return flightDao.save(request)
    }

    private fun isPopularRoute(source: String, dest: String): Boolean {
        val popularRoutes = setOf(
            Pair("DEL", "BOM"), Pair("BOM", "DEL"),
            Pair("DEL", "BLR"), Pair("BLR", "DEL"),
            Pair("BOM", "BLR"), Pair("BLR", "BOM"),
            Pair("DEL", "MAA"), Pair("MAA", "DEL"),
            Pair("BOM", "MAA"), Pair("MAA", "BOM")
        )
        return popularRoutes.contains(Pair(source, dest))
    }

    private fun persistFlights(flights: List<Flight>): List<Flight> {
        logger.info("Persisting {} flights to database", flights.size)
        // Flights are already persisted via flightDao.save() in createRandomFlight()
        return flights
    }

    fun generateTestFlights(
        count: Int,
        existingFlights: List<Flight>,
        baseDate: LocalDate = LocalDate.now().plusDays(1)
    ): List<FlightCreationRequest> {
        logger.info("Generating {} test flights to complement existing {} flights", count, existingFlights.size)

        val testFlights = mutableListOf<FlightCreationRequest>()
        val existingRoutes = existingFlights.map { Pair(it.sourceAirport, it.destinationAirport) }.toSet()
        val usedAirports = existingFlights.flatMap { listOf(it.sourceAirport, it.destinationAirport) }.toSet()

        repeat(count) {
            // Prefer airports that are already in the network to create journey opportunities
            val source = if (usedAirports.isNotEmpty() && Random.nextDouble() < 0.8) {
                usedAirports.random()
            } else {
                MAJOR_AIRPORTS.random()
            }

            val dest = if (usedAirports.isNotEmpty() && Random.nextDouble() < 0.8) {
                usedAirports.filter { it != source }.randomOrNull() ?: MAJOR_AIRPORTS.filter { it != source }.random()
            } else {
                MAJOR_AIRPORTS.filter { it != source }.random()
            }

            // Generate flight with slight time variation to avoid conflicts
            val departureHour = Random.nextInt(6, 23)
            val departureMinute = Random.nextInt(0, 60)
            val departureTime = baseDate.atTime(departureHour, departureMinute)
                .atZone(ZoneId.systemDefault())

            val flightDurationMinutes = Random.nextInt(60, 180)
            val arrivalTime = departureTime.plusMinutes(flightDurationMinutes.toLong())

            val price = if (isPopularRoute(source, dest)) {
                Random.nextInt(4000, 8000)
            } else {
                Random.nextInt(3000, 12000)
            }

            testFlights.add(
                FlightCreationRequest(
                    sourceAirport = source,
                    destinationAirport = dest,
                    departureTime = departureTime,
                    arrivalTime = arrivalTime,
                    airplaneId = UUID.randomUUID(),
                    price = BigDecimal(price),
                    totalSeats = Random.nextInt(150, 250)
                )
            )
        }

        return testFlights
    }
}