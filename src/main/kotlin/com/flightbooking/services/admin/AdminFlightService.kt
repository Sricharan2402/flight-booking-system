package com.flightbooking.services.admin

import com.flightbooking.common.exception.InvalidAirplaneException
import com.flightbooking.common.exception.InvalidFlightDataException
import com.flightbooking.data.AirplaneDao
import com.flightbooking.data.FlightDao
import com.flightbooking.domain.flights.Flight
import com.flightbooking.domain.flights.FlightCreationEvent
import com.flightbooking.domain.flights.FlightCreationRequest
import com.flightbooking.domain.flights.FlightCreationResponse
import com.flightbooking.producers.FlightEventProducer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.*

@Service
class AdminFlightService(
    private val flightDao: FlightDao,
    private val flightEventProducer: FlightEventProducer
) {

    private val logger = LoggerFactory.getLogger(AdminFlightService::class.java)

    @Transactional
    suspend fun createFlight(request: FlightCreationRequest): FlightCreationResponse = withContext(Dispatchers.IO) {
        logger.info("Creating flight from ${request.sourceAirport} to ${request.destinationAirport}")

        validateFlightRequest(request)

        val savedFlight = flightDao.save(request)

        publishFlightCreatedEvent(savedFlight)

        val response = FlightCreationResponse(
            flightId = savedFlight.flightId,
            sourceAirport = savedFlight.sourceAirport,
            destinationAirport = savedFlight.destinationAirport,
            departureTime = savedFlight.departureTime,
            arrivalTime = savedFlight.arrivalTime,
            airplaneId = savedFlight.airplaneId,
            price = savedFlight.price,
            status = savedFlight.status.name,
            createdAt = savedFlight.createdAt,
            updatedAt = savedFlight.updatedAt
        )

        logger.info("Successfully created flight with ID: ${savedFlight.flightId}")
        response
    }

    private suspend fun validateFlightRequest(request: FlightCreationRequest) {
        validateFlightTimes(request.departureTime, request.arrivalTime)
        validateAirports(request.sourceAirport, request.destinationAirport)
        validatePrice(request.price)
    }

    private fun validateFlightTimes(departureTime: ZonedDateTime, arrivalTime: ZonedDateTime) {
        val now = ZonedDateTime.now()

        if (departureTime.isBefore(now)) {
            throw InvalidFlightDataException("Departure time cannot be in the past")
        }

        if (arrivalTime.isBefore(departureTime)) {
            throw InvalidFlightDataException("Arrival time cannot be before departure time")
        }

        if (arrivalTime.isEqual(departureTime)) {
            throw InvalidFlightDataException("Arrival time cannot be equal to departure time")
        }
    }

    private fun validateAirports(sourceAirport: String, destinationAirport: String) {
        if (sourceAirport.isBlank() || sourceAirport.length != 3) {
            throw InvalidFlightDataException("Source airport must be a valid 3-letter airport code")
        }

        if (destinationAirport.isBlank() || destinationAirport.length != 3) {
            throw InvalidFlightDataException("Destination airport must be a valid 3-letter airport code")
        }

        if (sourceAirport.equals(destinationAirport, ignoreCase = true)) {
            throw InvalidFlightDataException("Source and destination airports cannot be the same")
        }
    }

    private fun validatePrice(price: java.math.BigDecimal) {
        if (price <= java.math.BigDecimal.ZERO) {
            throw InvalidFlightDataException("Flight price must be greater than zero")
        }
    }

    private suspend fun publishFlightCreatedEvent(flight: Flight) {
        try {
            val event = FlightCreationEvent(
                flightId = flight.flightId,
                sourceAirport = flight.sourceAirport,
                destinationAirport = flight.destinationAirport,
                departureDate = flight.departureTime,
                timestamp = ZonedDateTime.now()
            )

            flightEventProducer.publishFlightCreatedEvent(event)
            logger.debug("Published flight created event for flight ID: ${flight.flightId}")
        } catch (e: Exception) {
            logger.error("Failed to publish flight created event for flight ID: ${flight.flightId}", e)
        }
    }
}