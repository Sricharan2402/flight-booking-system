package com.flightbooking.application

import com.flightbooking.data.*
import com.flightbooking.domain.bookings.BookingRequest
import com.flightbooking.domain.common.BookingStatus
import com.flightbooking.domain.common.SeatStatus
import com.flightbooking.domain.seats.Seat
import com.flightbooking.services.booking.BookingService
import com.flightbooking.utils.RandomFlightNetworkGenerator
import com.flightbooking.utils.RandomJourneyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
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

    //> can you run @src/test/kotlin/com/flightbooking/application/BookingFlowConcurrencyApplicationTest.kt and @test-results/booking-concurrency-performance.md and update the doc with the tests destails, run example and results follow the format like in
    //  @test-results/deterministic-flight-creation-journey-generation.md . Start logging my prompts in @prompts/userPrompts.md from now.
    //

    companion object {
        val TEST_DATE: LocalDate = LocalDate.now().plusDays(1)
    }

    @BeforeEach
    fun setup() = runBlocking {
        cleanDatabase()

        // Step 1: Generate a *tiny* flight network
        val flightGenerator = RandomFlightNetworkGenerator(flightDao)
        val flights = flightGenerator.generateRandomFlightNetwork(
            flightCount = 5,            // Keep this small
            baseDate = LocalDate.now().plusDays(1),
            ensureConnectivity = false  // No need for hub structure in tests
        )

        // Step 2: Generate just 1–2 journeys
        val journeyGenerator = RandomJourneyGenerator(journeyDao)
        val journeys = journeyGenerator.generateRandomJourneys(
            journeyCount = 1,           // Single journey for contention test
            availableFlights = flights,
            maxFlightsPerJourney = 1    // Force direct journey only
        )

        // Step 3: Create a tiny seat inventory
        flights.forEach { flight ->
            repeat(1) { seatIndex ->    // Only 1 seat available → ensures only 1 winner
                seatDao.save(
                    Seat(
                        flightId = flight.flightId,
                        seatNumber = seatIndex.toString(),
                        status = SeatStatus.AVAILABLE,
                        createdAt = ZonedDateTime.now(),
                        updatedAt = ZonedDateTime.now(),
                        seatId = UUID.randomUUID(),
                        bookingId = null
                    )
                )
            }
        }
    }


    @Test
    fun `only one booking should succeed under concurrency`() {
        val journey = journeyDao.findAll().first()

        val attempts = 10
        val executor = Executors.newFixedThreadPool(attempts)
        val startLatch = CountDownLatch(1)
        val completionLatch = CountDownLatch(attempts)

        (1..attempts).map { attemptId ->
            CompletableFuture.runAsync({
                try {
                    startLatch.await()
                    val request = BookingRequest(
                        journeyId = journey.journeyId,
                        passengerCount = 1,
                        paymentId = UUID.randomUUID().toString(),
                        userId = UUID.randomUUID()
                    )
                    bookingService.createBooking(request)
                    logger.info("Booking succeeded for attempt $attemptId")
                } catch (e: Exception) {
                    logger.info("Booking failed for attempt $attemptId: ${e.message}")
                } finally {
                    completionLatch.countDown()
                }
            }, executor)
        }

        startLatch.countDown()
        completionLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        val confirmedBookings = bookingDao.findAll().filter { it.status == BookingStatus.CONFIRMED }
        assertEquals(1, confirmedBookings.size, "Exactly one booking should succeed")
        assertTrue(confirmedBookings.first().journeyId == journey.journeyId)
    }

    private fun cleanDatabase() {
        logger.debug("Cleaning database: bookings, seats, journeys, flights")
        bookingDao.deleteAll()
        seatDao.deleteAll()
        journeyDao.deleteAll()
        flightDao.deleteAll()
    }
}
