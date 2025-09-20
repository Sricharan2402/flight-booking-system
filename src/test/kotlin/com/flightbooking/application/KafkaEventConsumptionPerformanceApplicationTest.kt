package com.flightbooking.application

import com.flightbooking.data.*
import com.flightbooking.domain.flights.FlightCreationEvent
import com.flightbooking.services.admin.AdminFlightService
import com.flightbooking.services.metrics.EventMetricsService
import com.flightbooking.services.metrics.PerformanceThresholds
import com.flightbooking.utils.RandomFlightNetworkGenerator
import com.flightbooking.utils.RandomJourneyGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("kafka-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KafkaEventConsumptionPerformanceApplicationTest {

    private val logger = LoggerFactory.getLogger(KafkaEventConsumptionPerformanceApplicationTest::class.java)

    @Autowired
    private lateinit var adminFlightService: AdminFlightService

    @Autowired
    private lateinit var eventMetricsService: EventMetricsService

    @Autowired
    private lateinit var journeyDao: JourneyDao

    @Autowired
    private lateinit var flightDao: FlightDao

    @Value("\${test.performance.baseline-flights:500}")
    private var baselineFlightCount: Int = 3

    @Value("\${test.performance.baseline-journeys:300}")
    private var baselineJourneyCount: Int = 2

    @Value("\${test.performance.test-flights:100}")
    private var testFlightCount: Int = 2

    @Value("\${test.performance.flight-creation-delay-ms:25}")
    private var flightCreationDelayMs: Long = 25

    @Value("\${test.performance.min-success-rate:99.0}")
    private var minSuccessRate: Double = 99.0

    @Value("\${test.performance.max-consumer-time-ms:500}")
    private var maxConsumerTimeMs: Double = 500.0

    companion object {
        val TEST_DATE: LocalDate = LocalDate.now().plusDays(1)
        val TEST_AIRPORTS = listOf("DEL", "BOM", "BLR", "MAA", "CCU", "HYD", "AMD", "COK", "GOI", "PNQ")
    }

    @BeforeEach
    fun setupLargeDataset() = runBlocking {
        logger.info("🔧 SETUP_START: Setting up large dataset for Kafka performance test")
        logger.info("Target: {} baseline flights, {} baseline journeys, {} test flights",
            baselineFlightCount, baselineJourneyCount, testFlightCount)

        cleanDatabase()
        eventMetricsService.resetAllMetrics()

        val setupStartTime = System.currentTimeMillis()

        // Generate large flight network
        logger.info("📊 FLIGHT_GENERATION_START: Generating {} baseline flights", baselineFlightCount)
        val flightGenerator = RandomFlightNetworkGenerator(flightDao)
        val baselineFlights = flightGenerator.generateRandomFlightNetwork(
            flightCount = baselineFlightCount,
            baseDate = TEST_DATE,
            ensureConnectivity = true
        )
        logger.info("✅ FLIGHT_GENERATION_COMPLETE: Generated {} flights", baselineFlights.size)

        // Generate journey combinations
        logger.info("🗺️ JOURNEY_GENERATION_START: Generating {} baseline journeys", baselineJourneyCount)
        val journeyGenerator = RandomJourneyGenerator(journeyDao)
        val baselineJourneys = journeyGenerator.generateRandomJourneys(
            journeyCount = baselineJourneyCount,
            availableFlights = baselineFlights,
            maxFlightsPerJourney = 3
        )
        logger.info("✅ JOURNEY_GENERATION_COMPLETE: Generated {} journeys", baselineJourneys.size)

        val setupTimeMs = System.currentTimeMillis() - setupStartTime
        logger.info("🎯 SETUP_COMPLETE: {} flights, {} journeys in {}ms",
            baselineFlights.size, baselineJourneys.size, setupTimeMs)
    }

    @AfterEach
    fun cleanupLargeDataset() {
        logger.info("🧹 CLEANUP_START: Cleaning up large dataset")
        val cleanupStartTime = System.currentTimeMillis()

        cleanDatabase()

        val cleanupTimeMs = System.currentTimeMillis() - cleanupStartTime
        logger.info("✅ CLEANUP_COMPLETE: Cleaned database in {}ms", cleanupTimeMs)
    }

    @Test
    @Order(1)
    fun `high-volume Kafka event processing with performance validation`() = runBlocking {
        logger.info("🚀 TEST_START: High-volume Kafka event processing performance test")
        logger.info("Processing {} flight creation events with {}ms delay between events",
            testFlightCount, flightCreationDelayMs)

        val testStartTime = System.currentTimeMillis()

        // Get baseline metrics
        val initialJourneyCount = journeyDao.count()
        val initialFlights = flightDao.findAll()

        logger.info("📊 BASELINE: {} flights, {} journeys before test execution",
            initialFlights.size, initialJourneyCount)

        // Generate test flights for event creation
        val flightGenerator = RandomFlightNetworkGenerator(flightDao)
        val testFlightRequests = flightGenerator.generateTestFlights(
            count = testFlightCount,
            existingFlights = initialFlights,
            baseDate = TEST_DATE
        )

        logger.info("🎯 EVENT_PROCESSING_START: Creating {} flights with Kafka events", testFlightRequests.size)

        // Create flights and trigger Kafka events with minimal delay
        val eventCreationStartTime = System.currentTimeMillis()
        val createdFlightIds = mutableListOf<java.util.UUID>()

        for ((index, flightRequest) in testFlightRequests.withIndex()) {
            try {
                // Create flight (triggers Kafka event via AdminFlightService)
                val createdFlight = adminFlightService.createFlight(flightRequest)
                createdFlightIds.add(createdFlight.flightId)

                if ((index + 1) % 10 == 0) {
                    logger.info("📈 PROGRESS: Created {}/{} flights", index + 1, testFlightRequests.size)
                }

                // Small delay to avoid overwhelming the system
                if (flightCreationDelayMs > 0) {
                    delay(flightCreationDelayMs)
                }

            } catch (e: Exception) {
                logger.error("❌ FLIGHT_CREATION_FAILED: Failed to create flight {}: {}", index + 1, e.message)
                // Continue with remaining flights
            }
        }

        val eventCreationTimeMs = System.currentTimeMillis() - eventCreationStartTime
        logger.info("⚡ EVENT_CREATION_COMPLETE: Created {} flights in {}ms (avg {}ms per flight)",
            createdFlightIds.size, eventCreationTimeMs, eventCreationTimeMs / createdFlightIds.size)

        // Wait for all events to be processed (with timeout)
        logger.info("⏳ WAITING_FOR_CONSUMPTION: Allowing time for Kafka events to be processed...")
        val maxWaitTimeMs = 120000L // 2 minutes max wait
        val waitStartTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - waitStartTime < maxWaitTimeMs) {
            val metrics = eventMetricsService.getAggregatedMetrics()

            logger.info("📊 CONSUMPTION_PROGRESS: Produced={}, Consumed={}, Success={}, Avg={}ms",
                metrics.producer.eventsProduced,
                metrics.consumer.eventsConsumed,
                metrics.consumer.eventsProcessedSuccessfully,
                String.format("%.1f", metrics.consumer.averageProcessingTimeMs))

            // Check if all events have been processed successfully
            if (metrics.consumer.eventsProcessedSuccessfully >= createdFlightIds.size.toLong()) {
                logger.info("✅ ALL_EVENTS_PROCESSED: All {} events processed successfully", createdFlightIds.size)
                break
            }

            delay(2000) // Check every 2 seconds
        }

        val totalTestTimeMs = System.currentTimeMillis() - testStartTime

        // Final metrics validation
        logger.info("📊 FINAL_METRICS_COLLECTION: Gathering final performance metrics")
        val finalMetrics = eventMetricsService.getAggregatedMetrics()
        eventMetricsService.logMetricsSummary()

        val finalJourneyCount = journeyDao.count()
        val journeyDelta = finalJourneyCount - initialJourneyCount

        logger.info("🎯 TEST_RESULTS: " +
                "Total time: {}ms, " +
                "Journeys created: {}, " +
                "Events produced: {}, " +
                "Events consumed: {}, " +
                "Success rate: {}%",
            totalTestTimeMs,
            journeyDelta,
            finalMetrics.producer.eventsProduced,
            finalMetrics.consumer.eventsConsumed,
            String.format("%.2f", finalMetrics.consumer.successRate))

        // Performance validation with configurable thresholds
        val thresholds = PerformanceThresholds(
            minProducerSuccessRate = minSuccessRate,
            maxProducerTimeMs = 1000.0,
            minConsumerSuccessRate = minSuccessRate,
            maxConsumerTimeMs = maxConsumerTimeMs,
            minCorrelationRate = minSuccessRate,
            maxEventLag = 5
        )

        val validation = eventMetricsService.validatePerformanceThresholds(thresholds)

        // Log validation results
        if (validation.allThresholdsPassed) {
            logger.info("✅ PERFORMANCE_VALIDATION_PASSED: All performance thresholds met")
        } else {
            logger.error("❌ PERFORMANCE_VALIDATION_FAILED: Some thresholds not met:")
            validation.validationMessages.forEach { message ->
                logger.error("   ❌ {}", message)
            }
        }

        // Assert performance requirements
        assertTrue(validation.allThresholdsPassed,
            "Performance validation failed: ${validation.validationMessages.joinToString("; ")}")

        // Assert basic functionality
        assertTrue(finalMetrics.producer.eventsProduced >= createdFlightIds.size,
            "Should have produced at least ${createdFlightIds.size} events, but produced ${finalMetrics.producer.eventsProduced}")

        assertTrue(finalMetrics.consumer.eventsProcessedSuccessfully >= createdFlightIds.size * 0.99,
            "Should have successfully processed at least 99% of events")

        assertTrue(journeyDelta > 0,
            "Should have generated some new journeys from the created flights")

        logger.info("🎉 TEST_COMPLETE: Kafka event consumption performance test passed successfully!")
    }

    private fun cleanDatabase() {
        logger.debug("Cleaning database: journeys, then flights")
        journeyDao.deleteAll()
        flightDao.deleteAll()
    }
}