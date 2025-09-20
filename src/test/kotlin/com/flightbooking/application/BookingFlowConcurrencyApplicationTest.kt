package com.flightbooking.application

import com.flightbooking.data.*
import com.flightbooking.domain.bookings.BookingRequest
import com.flightbooking.domain.common.BookingStatus
import com.flightbooking.services.booking.BookingService
import com.flightbooking.utils.RandomFlightNetworkGenerator
import com.flightbooking.utils.RandomJourneyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BookingFlowConcurrencyApplicationTest {

    private val logger = LoggerFactory.getLogger(BookingFlowConcurrencyApplicationTest::class.java)

    @Autowired
    private lateinit var bookingService: BookingService

    @Autowired
    private lateinit var journeyDao: JourneyDao

    @Autowired
    private lateinit var flightDao: FlightDao

    @Autowired
    private lateinit var seatDao: SeatDao

    @Autowired
    private lateinit var bookingDao: BookingDao

    @Value("\${test.performance.booking.concurrent-attempts:50}")
    private var concurrentAttempts: Int = 50

    @Value("\${test.performance.booking.target-success-rate:2.0}")
    private var targetSuccessRatePercent: Double = 2.0

    @Value("\${test.performance.booking.max-booking-time-ms:2000}")
    private var maxBookingTimeMs: Long = 2000

    companion object {
        val TEST_DATE: LocalDate = LocalDate.now().plusDays(1)
        const val SOURCE_AIRPORT = "DEL"
        const val DESTINATION_AIRPORT = "BOM"
        const val PASSENGERS = 2
    }

    @BeforeEach
    fun setupTestData() = runBlocking {
        logger.info("🔧 SETUP_START: Setting up booking concurrency test data")

        cleanDatabase()

        // Generate flight network with limited capacity for contention
        val flightGenerator = RandomFlightNetworkGenerator(flightDao)
        val testFlights = flightGenerator.generateRandomFlightNetwork(
            flightCount = 15,
            baseDate = TEST_DATE,
            ensureConnectivity = true
        )
        logger.info("✅ Generated {} flights", testFlights.size)

        // Generate journeys for booking
        val journeyGenerator = RandomJourneyGenerator(journeyDao)
        val testJourneys = journeyGenerator.generateRandomJourneys(
            journeyCount = 10,
            availableFlights = testFlights,
            maxFlightsPerJourney = 2
        )
        logger.info("✅ Generated {} journeys", testJourneys.size)

        // Create limited seat inventory for high contention
        testFlights.forEach { flight ->
            repeat(5) { seatNumber ->  // Only 5 seats per flight
                seatDao.create(
                    flightId = flight.id,
                    seatNumber = "${('A' + (seatNumber % 3))}${seatNumber + 1}",
                    seatClass = "ECONOMY",
                    price = flight.price,
                    isAvailable = true
                )
            }
        }
        logger.info("✅ Created seat inventory: {} seats per flight", 5)

        logger.info("🎯 SETUP_COMPLETE: {} flights, {} journeys, limited seat inventory for contention testing",
            testFlights.size, testJourneys.size)
    }

    @AfterEach
    fun cleanup() {
        logger.info("🧹 CLEANUP_START: Cleaning up booking test data")
        cleanDatabase()
        logger.info("✅ CLEANUP_COMPLETE")
    }

    @Test
    @Order(1)
    fun `high concurrency booking with seat contention and lock validation`() = runBlocking {
        logger.info("🚀 TEST_START: High concurrency booking with Redis lock validation")

        // Phase 1: Find available journey for testing
        val availableJourneys = journeyDao.findAll()
        assertTrue(availableJourneys.isNotEmpty(), "Should have test journeys available")

        val targetJourney = availableJourneys.first()
        logger.info("🎯 TARGET_JOURNEY: Using journey {} with {} flights",
            targetJourney.id, targetJourney.flightDetails.flights.size)

        // Phase 2: Verify initial seat availability
        val initialSeats = targetJourney.flightDetails.flights.flatMap { flightDetail ->
            seatDao.findAvailableSeatsByFlight(flightDetail.id)
        }
        logger.info("📊 INITIAL_CAPACITY: {} total available seats across journey flights",
            initialSeats.size)

        val expectedMaxSuccessfulBookings = minOf(initialSeats.size / PASSENGERS, 2) // Max 2 successful bookings
        logger.info("🎯 EXPECTED_SUCCESS: Maximum {} successful bookings possible",
            expectedMaxSuccessfulBookings)

        // Phase 3: Concurrent booking attempts
        logger.info("⚡ CONCURRENT_BOOKING_START: {} concurrent attempts for same journey",
            concurrentAttempts)

        val executor = Executors.newFixedThreadPool(20)
        val startLatch = CountDownLatch(1)
        val completionLatch = CountDownLatch(concurrentAttempts)

        val successfulBookings = AtomicInteger(0)
        val failedBookings = AtomicInteger(0)
        val lockContentionErrors = AtomicInteger(0)
        val seatUnavailableErrors = AtomicInteger(0)
        val timeoutErrors = AtomicInteger(0)
        val responseTimes = mutableListOf<Long>()
        val errors = mutableListOf<String>()

        val futures = (1..concurrentAttempts).map { attemptId ->
            CompletableFuture.supplyAsync({
                try {
                    startLatch.await()

                    val requestStartTime = System.nanoTime()
                    val bookingRequest = createBookingRequest(targetJourney.id, attemptId)

                    val result = try {
                        val booking = bookingService.createBooking(bookingRequest)
                        val responseTimeMs = (System.nanoTime() - requestStartTime) / 1_000_000

                        synchronized(responseTimes) {
                            responseTimes.add(responseTimeMs)
                        }

                        successfulBookings.incrementAndGet()
                        logger.debug("BOOKING_SUCCESS attempt_id={} booking_id={} response_time_ms={}",
                            attemptId, booking.id, responseTimeMs)

                        BookingResult.Success(booking.id, responseTimeMs)
                    } catch (e: Exception) {
                        val responseTimeMs = (System.nanoTime() - requestStartTime) / 1_000_000

                        synchronized(responseTimes) {
                            responseTimes.add(responseTimeMs)
                        }

                        failedBookings.incrementAndGet()

                        // Categorize error types
                        when {
                            e.message?.contains("lock", ignoreCase = true) == true ||
                            e.message?.contains("contention", ignoreCase = true) == true -> {
                                lockContentionErrors.incrementAndGet()
                                logger.debug("BOOKING_LOCK_CONTENTION attempt_id={} response_time_ms={} error={}",
                                    attemptId, responseTimeMs, e.message)
                            }
                            e.message?.contains("seat", ignoreCase = true) == true ||
                            e.message?.contains("available", ignoreCase = true) == true -> {
                                seatUnavailableErrors.incrementAndGet()
                                logger.debug("BOOKING_SEAT_UNAVAILABLE attempt_id={} response_time_ms={} error={}",
                                    attemptId, responseTimeMs, e.message)
                            }
                            e.message?.contains("timeout", ignoreCase = true) == true -> {
                                timeoutErrors.incrementAndGet()
                                logger.debug("BOOKING_TIMEOUT attempt_id={} response_time_ms={} error={}",
                                    attemptId, responseTimeMs, e.message)
                            }
                            else -> {
                                synchronized(errors) {
                                    errors.add("Attempt $attemptId: ${e.message}")
                                }
                                logger.debug("BOOKING_OTHER_ERROR attempt_id={} response_time_ms={} error={}",
                                    attemptId, responseTimeMs, e.message)
                            }
                        }

                        BookingResult.Failure(e.message ?: "Unknown error", responseTimeMs)
                    }

                    result
                } finally {
                    completionLatch.countDown()
                }
            }, executor)
        }

        // Start all booking attempts simultaneously
        val testStartTime = System.currentTimeMillis()
        startLatch.countDown()

        // Wait for completion
        val allCompleted = completionLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)
        val totalTestTimeMs = System.currentTimeMillis() - testStartTime

        executor.shutdown()

        logger.info("⚡ CONCURRENT_BOOKING_COMPLETE: Total time {}ms", totalTestTimeMs)

        // Phase 4: Results Analysis
        logger.info("📊 BOOKING_RESULTS_ANALYSIS_START")

        val successful = successfulBookings.get()
        val failed = failedBookings.get()
        val totalAttempts = successful + failed

        // Calculate timing metrics
        val sortedTimes = responseTimes.sorted()
        val avgTime = if (sortedTimes.isNotEmpty()) sortedTimes.average() else 0.0
        val p90 = calculatePercentile(sortedTimes, 90.0)
        val p95 = calculatePercentile(sortedTimes, 95.0)
        val maxTime = if (sortedTimes.isNotEmpty()) sortedTimes.maxOrNull() ?: 0L else 0L

        logger.info("📈 TIMING_METRICS: Avg={}ms, P90={}ms, P95={}ms, Max={}ms",
            String.format("%.1f", avgTime),
            String.format("%.1f", p90),
            String.format("%.1f", p95),
            maxTime)

        logger.info("🎯 BOOKING_SUMMARY: Success={}/{} ({}%), Failed={}, Lock_Contention={}, Seat_Unavailable={}, Timeouts={}",
            successful, totalAttempts, String.format("%.1f", (successful.toDouble() / totalAttempts) * 100),
            failed, lockContentionErrors.get(), seatUnavailableErrors.get(), timeoutErrors.get())

        // Phase 5: Database Consistency Validation
        logger.info("🔍 CONSISTENCY_VALIDATION_START")

        val finalBookings = bookingDao.findAll()
        val actualSuccessfulBookings = finalBookings.count { it.status == BookingStatus.CONFIRMED }

        logger.info("📊 DB_CONSISTENCY: Expected_Max={}, Reported_Success={}, DB_Confirmed={}",
            expectedMaxSuccessfulBookings, successful, actualSuccessfulBookings)

        // Verify no double-booking occurred
        val bookedSeats = finalBookings.filter { it.status == BookingStatus.CONFIRMED }
            .flatMap { booking ->
                val journey = journeyDao.findById(booking.journeyId)!!
                journey.flightDetails.flights.flatMap { flightDetail ->
                    seatDao.findBookedSeatsByFlight(flightDetail.id)
                }
            }

        val uniqueBookedSeats = bookedSeats.toSet()
        val hasDoubleBooking = bookedSeats.size != uniqueBookedSeats.size

        logger.info("🔐 DOUBLE_BOOKING_CHECK: Total_Booked_Seats={}, Unique_Booked_Seats={}, Double_Booking={}",
            bookedSeats.size, uniqueBookedSeats.size, hasDoubleBooking)

        // Performance Validations
        assertTrue(allCompleted, "All booking attempts should complete within timeout")
        assertTrue(!hasDoubleBooking, "No double-booking should occur: ${bookedSeats.size} vs ${uniqueBookedSeats.size}")
        assertEquals(successful, actualSuccessfulBookings,
            "Successful bookings count should match database: $successful vs $actualSuccessfulBookings")
        assertTrue(successful <= expectedMaxSuccessfulBookings,
            "Successful bookings should not exceed seat capacity: $successful vs $expectedMaxSuccessfulBookings")
        assertTrue(p90 <= maxBookingTimeMs,
            "P90 booking time should be ≤${maxBookingTimeMs}ms, but was ${String.format("%.1f", p90)}ms")

        val actualSuccessRate = (successful.toDouble() / totalAttempts) * 100
        assertTrue(actualSuccessRate >= targetSuccessRatePercent,
            "Success rate should be ≥${targetSuccessRatePercent}%, but was ${String.format("%.1f", actualSuccessRate)}%")
        assertTrue(lockContentionErrors.get() > 0 || seatUnavailableErrors.get() > 0,
            "Should have contention errors demonstrating lock effectiveness")

        logger.info("✅ CONCURRENCY_VALIDATION_PASSED: Redis locks prevented double-booking under high concurrency")
    }

    @Test
    @Order(2)
    fun `seat reservation consistency across multiple flights in journey`() = runBlocking {
        logger.info("🚀 TEST_START: Multi-flight journey booking consistency")

        // Find a journey with multiple flights
        val multiFlightJourneys = journeyDao.findAll().filter {
            it.flightDetails.flights.size > 1
        }
        assertTrue(multiFlightJourneys.isNotEmpty(), "Should have multi-flight journeys")

        val targetJourney = multiFlightJourneys.first()
        logger.info("🎯 TARGET_JOURNEY: Multi-flight journey {} with {} flights",
            targetJourney.id, targetJourney.flightDetails.flights.size)

        // Phase 1: Concurrent attempts on multi-flight journey
        val concurrentMultiFlightAttempts = 20
        logger.info("⚡ MULTI_FLIGHT_BOOKING_START: {} concurrent attempts", concurrentMultiFlightAttempts)

        val executor = Executors.newFixedThreadPool(10)
        val startLatch = CountDownLatch(1)
        val completionLatch = CountDownLatch(concurrentMultiFlightAttempts)

        val successes = AtomicInteger(0)
        val failures = AtomicInteger(0)

        val futures = (1..concurrentMultiFlightAttempts).map { attemptId ->
            CompletableFuture.supplyAsync({
                try {
                    startLatch.await()

                    val bookingRequest = createBookingRequest(targetJourney.id, attemptId)
                    val booking = bookingService.createBooking(bookingRequest)

                    successes.incrementAndGet()
                    logger.debug("MULTI_FLIGHT_SUCCESS attempt_id={} booking_id={}", attemptId, booking.id)

                } catch (e: Exception) {
                    failures.incrementAndGet()
                    logger.debug("MULTI_FLIGHT_FAILURE attempt_id={} error={}", attemptId, e.message)
                } finally {
                    completionLatch.countDown()
                }
            }, executor)
        }

        startLatch.countDown()
        completionLatch.await(20, java.util.concurrent.TimeUnit.SECONDS)
        executor.shutdown()

        logger.info("📊 MULTI_FLIGHT_RESULTS: Success={}, Failed={}", successes.get(), failures.get())

        // Phase 2: Validate consistency across all flights
        val confirmedBookings = bookingDao.findAll().filter { it.status == BookingStatus.CONFIRMED }

        confirmedBookings.forEach { booking ->
            val journey = journeyDao.findById(booking.journeyId)!!
            val allFlightsBookedCorrectly = journey.flightDetails.flights.all { flightDetail ->
                val bookedSeats = seatDao.findBookedSeatsByFlight(flightDetail.id)
                bookedSeats.size == PASSENGERS // Each booking should reserve correct number of seats
            }

            assertTrue(allFlightsBookedCorrectly,
                "All flights in journey ${journey.id} should have consistent seat reservations")
        }

        logger.info("✅ MULTI_FLIGHT_CONSISTENCY_VALIDATED: All multi-flight journeys maintain seat consistency")
    }

    private fun calculatePercentile(sortedValues: List<Long>, percentile: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        val index = (percentile / 100.0) * (sortedValues.size - 1)
        val lower = kotlin.math.floor(index).toInt()
        val upper = kotlin.math.ceil(index).toInt()

        return if (lower == upper) {
            sortedValues[lower].toDouble()
        } else {
            val weight = index - lower
            sortedValues[lower] * (1 - weight) + sortedValues[upper] * weight
        }
    }

    private fun createBookingRequest(journeyId: String,): BookingRequest {
        return BookingRequest(
            journeyId = journeyId,
            passengerCount = 2,
            paymentId = UUID.randomUUID().toString(),
            userId = ""
        )
    }

    private fun cleanDatabase() {
        logger.debug("Cleaning database: bookings, seats, journeys, flights")
        bookingDao.deleteAll()
        seatDao.deleteAll()
        journeyDao.deleteAll()
        flightDao.deleteAll()
    }

    sealed class BookingResult {
        data class Success(val bookingId: String, val responseTimeMs: Long) : BookingResult()
        data class Failure(val error: String, val responseTimeMs: Long) : BookingResult()
    }
}