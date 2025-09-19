package com.flightbooking.producers

import com.flightbooking.domain.flights.FlightCreationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class FlightEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    private val logger = LoggerFactory.getLogger(FlightEventProducer::class.java)

    companion object {
        private const val FLIGHT_EVENTS_TOPIC = "flight-events"
    }

    suspend fun publishFlightCreatedEvent(event: FlightCreationEvent) = withContext(Dispatchers.IO) {
        try {
            val key = event.flightId.toString()
            logger.debug("Publishing flight created event for flight ID: ${event.flightId}")

            kafkaTemplate.send(FLIGHT_EVENTS_TOPIC, key, event).get()

            logger.info("Successfully published flight created event for flight ID: ${event.flightId}")
        } catch (e: Exception) {
            logger.error("Failed to publish flight created event for flight ID: ${event.flightId}", e)
            throw e
        }
    }
}