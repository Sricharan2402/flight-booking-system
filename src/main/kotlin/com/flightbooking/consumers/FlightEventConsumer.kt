package com.flightbooking.consumers

import com.flightbooking.domain.flights.FlightCreationEvent
import com.flightbooking.services.journeys.JourneyGenerationService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class FlightEventConsumer(
    private val journeyGenerationService: JourneyGenerationService
) {

    private val logger = LoggerFactory.getLogger(FlightEventConsumer::class.java)

    @KafkaListener(topics = ["flight-events"])
    fun handleFlightCreatedEvent(event: FlightCreationEvent) {
        runBlocking {
            try {
                logger.info("Received flight created event for flight ID: ${event.flightId}")

                journeyGenerationService.generateJourneysForNewFlight(event.flightId)

                logger.info("Successfully processed flight created event for flight ID: ${event.flightId}")
            } catch (e: Exception) {
                logger.error("Failed to process flight created event for flight ID: ${event.flightId}", e)
                // In a production system, you might want to implement retry logic or dead letter queue
                throw e
            }
        }
    }
}