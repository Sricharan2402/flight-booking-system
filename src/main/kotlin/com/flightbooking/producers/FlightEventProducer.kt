package com.flightbooking.producers

import com.flightbooking.domain.flights.FlightCreationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

@Component
class FlightEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    private val logger = LoggerFactory.getLogger(FlightEventProducer::class.java)

    @org.springframework.beans.factory.annotation.Value("\${flight.events.producer.enabled:true}")
    private var producerEnabled: Boolean = true

    companion object {
        private const val FLIGHT_EVENTS_TOPIC = "flight-events-load"
    }

    // Metrics for tracking production performance
    private val eventsProduced = AtomicLong(0)
    private val eventsFailedToProduce = AtomicLong(0)
    private val totalProduceTimeMs = AtomicLong(0)

    fun publishFlightCreatedEvent(event: FlightCreationEvent) {
        if (!producerEnabled) {
            logger.info("EVENT_PRODUCER_DISABLED flight_id={} reason=producer_disabled", event.flightId)
            return
        }

        val correlationId = "event_${event.flightId}_${System.currentTimeMillis()}"
        val publishStartTime = System.currentTimeMillis()

        try {
            val key = event.flightId.toString()
            logger.info("EVENT_PUBLISH_START correlation_id={} flight_id={} topic={}",
                correlationId, event.flightId, FLIGHT_EVENTS_TOPIC)

            val publishTimeMs = measureTimeMillis {
                kafkaTemplate.send(FLIGHT_EVENTS_TOPIC, key, event).get()
            }

            // Update metrics
            eventsProduced.incrementAndGet()
            totalProduceTimeMs.addAndGet(publishTimeMs)

            logger.info("EVENT_PUBLISH_SUCCESS correlation_id={} flight_id={} publish_time_ms={} total_produced={}",
                correlationId, event.flightId, publishTimeMs, eventsProduced.get())

        } catch (e: Exception) {
            eventsFailedToProduce.incrementAndGet()
            val publishTimeMs = System.currentTimeMillis() - publishStartTime

            logger.error("EVENT_PUBLISH_FAILED correlation_id={} flight_id={} publish_time_ms={} total_failed={} error_message=\"{}\"",
                correlationId, event.flightId, publishTimeMs, eventsFailedToProduce.get(), e.message, e)
            throw e
        }
    }

    fun getProducerMetrics(): ProducerMetrics {
        val produced = eventsProduced.get()
        val failed = eventsFailedToProduce.get()
        val totalTime = totalProduceTimeMs.get()

        return ProducerMetrics(
            eventsProduced = produced,
            eventsFailed = failed,
            totalEvents = produced + failed,
            successRate = if (produced + failed > 0) (produced.toDouble() / (produced + failed)) * 100 else 0.0,
            averagePublishTimeMs = if (produced > 0) totalTime.toDouble() / produced else 0.0,
            totalPublishTimeMs = totalTime
        )
    }

    fun resetMetrics() {
        eventsProduced.set(0)
        eventsFailedToProduce.set(0)
        totalProduceTimeMs.set(0)
        logger.info("PRODUCER_METRICS_RESET")
    }
}

data class ProducerMetrics(
    val eventsProduced: Long,
    val eventsFailed: Long,
    val totalEvents: Long,
    val successRate: Double,
    val averagePublishTimeMs: Double,
    val totalPublishTimeMs: Long
)