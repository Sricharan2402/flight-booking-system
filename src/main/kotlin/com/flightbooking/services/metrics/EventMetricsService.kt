package com.flightbooking.services.metrics

import com.flightbooking.consumers.ConsumerMetrics
import com.flightbooking.consumers.FlightEventConsumer
import com.flightbooking.producers.FlightEventProducer
import com.flightbooking.producers.ProducerMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EventMetricsService(
    private val flightEventProducer: FlightEventProducer,
    private val flightEventConsumer: FlightEventConsumer
) {

    private val logger = LoggerFactory.getLogger(EventMetricsService::class.java)

    fun getAggregatedMetrics(): AggregatedEventMetrics {
        val producerMetrics = flightEventProducer.getProducerMetrics()
        val consumerMetrics = flightEventConsumer.getConsumerMetrics()

        return AggregatedEventMetrics(
            producer = producerMetrics,
            consumer = consumerMetrics,
            eventCorrelation = calculateEventCorrelation(producerMetrics, consumerMetrics)
        )
    }

    fun logMetricsSummary() {
        val metrics = getAggregatedMetrics()

        logger.info("METRICS_SUMMARY " +
                "producer_events={} producer_success_rate={}% producer_avg_time_ms={} " +
                "consumer_events={} consumer_success_rate={}% consumer_avg_time_ms={} " +
                "correlation_match={}% events_processed_vs_produced={}",
            metrics.producer.totalEvents,
            String.format("%.2f", metrics.producer.successRate),
            String.format("%.2f", metrics.producer.averagePublishTimeMs),
            metrics.consumer.eventsConsumed,
            String.format("%.2f", metrics.consumer.successRate),
            String.format("%.2f", metrics.consumer.averageProcessingTimeMs),
            String.format("%.2f", metrics.eventCorrelation.correlationRate),
            "${metrics.consumer.eventsConsumed}/${metrics.producer.eventsProduced}"
        )
    }

    fun resetAllMetrics() {
        flightEventProducer.resetMetrics()
        flightEventConsumer.resetMetrics()
        logger.info("ALL_METRICS_RESET")
    }

    private fun calculateEventCorrelation(
        producerMetrics: ProducerMetrics,
        consumerMetrics: ConsumerMetrics
    ): EventCorrelation {
        val correlationRate = if (producerMetrics.eventsProduced > 0) {
            (consumerMetrics.eventsConsumed.toDouble() / producerMetrics.eventsProduced) * 100
        } else {
            0.0
        }

        val isCorrelationPerfect = producerMetrics.eventsProduced == consumerMetrics.eventsConsumed
        val eventLag = producerMetrics.eventsProduced - consumerMetrics.eventsConsumed

        return EventCorrelation(
            eventsProduced = producerMetrics.eventsProduced,
            eventsConsumed = consumerMetrics.eventsConsumed,
            correlationRate = correlationRate,
            isPerfectCorrelation = isCorrelationPerfect,
            eventLag = eventLag
        )
    }

    fun validatePerformanceThresholds(thresholds: PerformanceThresholds): PerformanceValidation {
        val metrics = getAggregatedMetrics()

        val validations = mutableListOf<String>()
        var allPassed = true

        // Producer validations
        if (metrics.producer.successRate < thresholds.minProducerSuccessRate) {
            validations.add("Producer success rate ${String.format("%.2f", metrics.producer.successRate)}% below threshold ${thresholds.minProducerSuccessRate}%")
            allPassed = false
        }

        if (metrics.producer.averagePublishTimeMs > thresholds.maxProducerTimeMs) {
            validations.add("Producer average time ${String.format("%.2f", metrics.producer.averagePublishTimeMs)}ms above threshold ${thresholds.maxProducerTimeMs}ms")
            allPassed = false
        }

        // Consumer validations
        if (metrics.consumer.successRate < thresholds.minConsumerSuccessRate) {
            validations.add("Consumer success rate ${String.format("%.2f", metrics.consumer.successRate)}% below threshold ${thresholds.minConsumerSuccessRate}%")
            allPassed = false
        }

        if (metrics.consumer.averageProcessingTimeMs > thresholds.maxConsumerTimeMs) {
            validations.add("Consumer average time ${String.format("%.2f", metrics.consumer.averageProcessingTimeMs)}ms above threshold ${thresholds.maxConsumerTimeMs}ms")
            allPassed = false
        }

        // Correlation validations
        if (metrics.eventCorrelation.correlationRate < thresholds.minCorrelationRate) {
            validations.add("Event correlation rate ${String.format("%.2f", metrics.eventCorrelation.correlationRate)}% below threshold ${thresholds.minCorrelationRate}%")
            allPassed = false
        }

        if (metrics.eventCorrelation.eventLag > thresholds.maxEventLag) {
            validations.add("Event lag ${metrics.eventCorrelation.eventLag} above threshold ${thresholds.maxEventLag}")
            allPassed = false
        }

        return PerformanceValidation(
            allThresholdsPassed = allPassed,
            validationMessages = validations,
            metrics = metrics
        )
    }
}

data class AggregatedEventMetrics(
    val producer: ProducerMetrics,
    val consumer: ConsumerMetrics,
    val eventCorrelation: EventCorrelation
)

data class EventCorrelation(
    val eventsProduced: Long,
    val eventsConsumed: Long,
    val correlationRate: Double,
    val isPerfectCorrelation: Boolean,
    val eventLag: Long
)

data class PerformanceThresholds(
    val minProducerSuccessRate: Double = 99.0,
    val maxProducerTimeMs: Double = 1000.0,
    val minConsumerSuccessRate: Double = 99.0,
    val maxConsumerTimeMs: Double = 500.0,
    val minCorrelationRate: Double = 99.0,
    val maxEventLag: Long = 5
)

data class PerformanceValidation(
    val allThresholdsPassed: Boolean,
    val validationMessages: List<String>,
    val metrics: AggregatedEventMetrics
)