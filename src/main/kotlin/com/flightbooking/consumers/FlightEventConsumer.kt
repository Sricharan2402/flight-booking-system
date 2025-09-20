package com.flightbooking.consumers

import com.flightbooking.domain.flights.FlightCreationEvent
import com.flightbooking.services.journeys.JourneyGenerationService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

@Component
class FlightEventConsumer(
    private val journeyGenerationService: JourneyGenerationService
) {

    private val logger = LoggerFactory.getLogger(FlightEventConsumer::class.java)

    // Metrics for tracking consumption performance
    private val eventsConsumed = AtomicLong(0)
    private val eventsProcessedSuccessfully = AtomicLong(0)
    private val eventsFailedToProcess = AtomicLong(0)
    private val totalProcessingTimeMs = AtomicLong(0)

    @KafkaListener(topics = ["flight-events-load"])
    fun handleFlightCreatedEvent(event: FlightCreationEvent) {
        val correlationId = "event_${event.flightId}_${System.currentTimeMillis()}"
        val consumeStartTime = System.currentTimeMillis()

        runBlocking {
            try {
                eventsConsumed.incrementAndGet()

                logger.info("EVENT_CONSUME_START correlation_id={} flight_id={} total_consumed={}",
                    correlationId, event.flightId, eventsConsumed.get())

                val processingTimeMs = measureTimeMillis {
                    journeyGenerationService.generateJourneysForNewFlight(event.flightId)
                }

                // Update success metrics
                eventsProcessedSuccessfully.incrementAndGet()
                totalProcessingTimeMs.addAndGet(processingTimeMs)

                logger.info("EVENT_CONSUME_SUCCESS correlation_id={} flight_id={} processing_time_ms={} total_successful={} total_consumed={}",
                    correlationId, event.flightId, processingTimeMs, eventsProcessedSuccessfully.get(), eventsConsumed.get())

            } catch (e: Exception) {
                eventsFailedToProcess.incrementAndGet()
                val processingTimeMs = System.currentTimeMillis() - consumeStartTime

                logger.error("EVENT_CONSUME_FAILED correlation_id={} flight_id={} processing_time_ms={} total_failed={} total_consumed={} error_message=\"{}\"",
                    correlationId, event.flightId, processingTimeMs, eventsFailedToProcess.get(), eventsConsumed.get(), e.message, e)

                // In a production system, you might want to implement retry logic or dead letter queue
                throw e
            }
        }
    }

    fun getConsumerMetrics(): ConsumerMetrics {
        val consumed = eventsConsumed.get()
        val successful = eventsProcessedSuccessfully.get()
        val failed = eventsFailedToProcess.get()
        val totalTime = totalProcessingTimeMs.get()

        return ConsumerMetrics(
            eventsConsumed = consumed,
            eventsProcessedSuccessfully = successful,
            eventsFailedToProcess = failed,
            successRate = if (consumed > 0) (successful.toDouble() / consumed) * 100 else 0.0,
            averageProcessingTimeMs = if (successful > 0) totalTime.toDouble() / successful else 0.0,
            totalProcessingTimeMs = totalTime
        )
    }

    fun resetMetrics() {
        eventsConsumed.set(0)
        eventsProcessedSuccessfully.set(0)
        eventsFailedToProcess.set(0)
        totalProcessingTimeMs.set(0)
        logger.info("CONSUMER_METRICS_RESET")
    }
}

data class ConsumerMetrics(
    val eventsConsumed: Long,
    val eventsProcessedSuccessfully: Long,
    val eventsFailedToProcess: Long,
    val successRate: Double,
    val averageProcessingTimeMs: Double,
    val totalProcessingTimeMs: Long
)