package com.flightbooking.application

import com.flightbooking.data.*
import com.flightbooking.domain.search.SearchRequest
import com.flightbooking.services.search.SearchService
import com.flightbooking.services.cache.SearchCacheService
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
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SearchFlowWithCachePerformanceApplicationTest {

    private val logger = LoggerFactory.getLogger(SearchFlowWithCachePerformanceApplicationTest::class.java)

    @Autowired
    private lateinit var searchService: SearchService

    @Autowired
    private lateinit var searchCacheService: SearchCacheService

    @Autowired
    private lateinit var journeyDao: JourneyDao

    @Autowired
    private lateinit var flightDao: FlightDao

    @Value("\${test.performance.search.concurrent-requests:150}")
    private var concurrentRequests: Int = 150

    @Value("\${test.performance.search.target-p90-latency-ms:100}")
    private var targetP90LatencyMs: Long = 100

    @Value("\${test.performance.search.cache-hit-ratio-threshold:95.0}")
    private var cacheHitRatioThreshold: Double = 95.0

    companion object {
        val TEST_DATE: LocalDate = LocalDate.now().plusDays(1)
        const val SOURCE_AIRPORT = "DEL"
        const val DESTINATION_AIRPORT = "BOM"
        const val PASSENGERS = 2
    }

    @BeforeEach
    fun setupTestData() = runBlocking {
        logger.info("🔧 SETUP_START: Setting up cache performance test data")

        cleanDatabase()
        clearCache()

        // Generate realistic flight network
        val flightGenerator = RandomFlightNetworkGenerator(flightDao)
        val baselineFlights = flightGenerator.generateRandomFlightNetwork(
            flightCount = 30,
            baseDate = TEST_DATE,
            ensureConnectivity = true
        )
        logger.info("✅ Generated {} flights", baselineFlights.size)

        // Generate journeys including DEL → BOM route
        val journeyGenerator = RandomJourneyGenerator(journeyDao)
        val baselineJourneys = journeyGenerator.generateRandomJourneys(
            journeyCount = 20,
            availableFlights = baselineFlights,
            maxFlightsPerJourney = 3
        )
        logger.info("✅ Generated {} journeys", baselineJourneys.size)

        logger.info("🎯 SETUP_COMPLETE: {} flights, {} journeys for cache testing",
            baselineFlights.size, baselineJourneys.size)
    }

    @AfterEach
    fun cleanup() {
        logger.info("🧹 CLEANUP_START: Cleaning up cache test data")
        cleanDatabase()
        clearCache()
        logger.info("✅ CLEANUP_COMPLETE")
    }

    @Test
    @Order(1)
    fun `cache warming and hit ratio validation under concurrent load`() = runBlocking {
        logger.info("🚀 TEST_START: Cache warming and concurrent load testing")

        // Phase 1: Cache Warming
        logger.info("🔥 CACHE_WARMING_START: Initial search to populate cache")
        val warmupRequest = SearchRequest(
            sourceAirport = SOURCE_AIRPORT,
            destinationAirport = DESTINATION_AIRPORT,
            departureDate = TEST_DATE,
            passengers = PASSENGERS,
            sortBy = "price"
        )

        val warmupStartTime = System.nanoTime()
        val warmupResponse = searchService.searchJourneys(warmupRequest)
        val warmupTimeMs = (System.nanoTime() - warmupStartTime) / 1_000_000

        logger.info("✅ CACHE_WARMING_COMPLETE: Found {} journeys in {}ms (cache miss expected)",
            warmupResponse.journeys.size, warmupTimeMs)

        // Verify cache key generation
        val cacheKey = searchCacheService.generateCacheKey(
            SOURCE_AIRPORT, DESTINATION_AIRPORT, TEST_DATE
        )
        logger.info("📊 Cache key generated: {}", cacheKey)

        // Phase 2: Concurrent Load Testing
        logger.info("⚡ CONCURRENT_LOAD_START: Executing {} concurrent requests", concurrentRequests)

        val executor = Executors.newFixedThreadPool(20)
        val startLatch = CountDownLatch(1)
        val completionLatch = CountDownLatch(concurrentRequests)
        val responseTimes = mutableListOf<Long>()
        val errors = mutableListOf<Exception>()

        val futures = (1..concurrentRequests).map { requestId ->
            CompletableFuture.supplyAsync({
                try {
                    // Wait for all threads to be ready
                    startLatch.await()

                    val requestStartTime = System.nanoTime()
                    val response = searchService.searchJourneys(warmupRequest)
                    val responseTimeMs = (System.nanoTime() - requestStartTime) / 1_000_000

                    synchronized(responseTimes) {
                        responseTimes.add(responseTimeMs)
                    }

                    logger.debug("Request {} completed in {}ms with {} results",
                        requestId, responseTimeMs, response.journeys.size)

                    Triple(requestId, responseTimeMs, response.journeys.size)
                } catch (e: Exception) {
                    logger.error("Request {} failed: {}", requestId, e.message)
                    synchronized(errors) {
                        errors.add(e)
                    }
                    throw e
                } finally {
                    completionLatch.countDown()
                }
            }, executor)
        }

        // Start all requests simultaneously
        val testStartTime = System.currentTimeMillis()
        startLatch.countDown()

        // Wait for completion with timeout
        val allCompleted = completionLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)
        val totalTestTimeMs = System.currentTimeMillis() - testStartTime

        // Collect results
        val successfulResults = mutableListOf<Triple<Int, Long, Int>>()
        futures.forEach { future ->
            try {
                if (future.isDone && !future.isCompletedExceptionally) {
                    successfulResults.add(future.get())
                }
            } catch (e: Exception) {
                logger.warn("Failed to get result from future: {}", e.message)
            }
        }

        executor.shutdown()

        logger.info("⚡ CONCURRENT_LOAD_COMPLETE: {}/{} requests completed in {}ms",
            successfulResults.size, concurrentRequests, totalTestTimeMs)

        // Phase 3: Performance Analysis
        logger.info("📊 PERFORMANCE_ANALYSIS_START")

        // Calculate latency percentiles
        val sortedTimes = responseTimes.sorted()
        val p50 = calculatePercentile(sortedTimes, 50.0)
        val p90 = calculatePercentile(sortedTimes, 90.0)
        val p95 = calculatePercentile(sortedTimes, 95.0)
        val p99 = calculatePercentile(sortedTimes, 99.0)
        val avgTime = if (sortedTimes.isNotEmpty()) sortedTimes.average() else 0.0

        logger.info("📈 LATENCY_METRICS: Avg={}ms, P50={}ms, P90={}ms, P95={}ms, P99={}ms",
            String.format("%.1f", avgTime),
            String.format("%.1f", p50),
            String.format("%.1f", p90),
            String.format("%.1f", p95),
            String.format("%.1f", p99))

        // Calculate cache hit ratio (after warmup, all should be cache hits)
        val cacheHitRatio = if (responseTimes.size > 1) {
            // Assuming cache hits are significantly faster than DB hits
            val fastResponses = responseTimes.count { it < warmupTimeMs / 2 }
            (fastResponses.toDouble() / responseTimes.size) * 100
        } else {
            0.0
        }

        logger.info("🎯 CACHE_PERFORMANCE: Hit ratio = {}%, Target = {}%",
            String.format("%.1f", cacheHitRatio), cacheHitRatioThreshold)

        // Performance Validations
        assertTrue(allCompleted, "All requests should complete within timeout")
        assertTrue(errors.isEmpty(), "No errors should occur: ${errors.map { it.message }}")
        assertTrue(successfulResults.size >= concurrentRequests * 0.95,
            "At least 95% of requests should succeed: ${successfulResults.size}/$concurrentRequests")
        assertTrue(p90 <= targetP90LatencyMs,
            "P90 latency should be ≤${targetP90LatencyMs}ms, but was ${String.format("%.1f", p90)}ms")
        assertTrue(cacheHitRatio >= cacheHitRatioThreshold,
            "Cache hit ratio should be ≥${cacheHitRatioThreshold}%, but was ${String.format("%.1f", cacheHitRatio)}%")

        logger.info("✅ PERFORMANCE_VALIDATION_PASSED: All cache performance thresholds met")
    }

    @Test
    @Order(2)
    fun `cache expiry and regeneration behavior`() = runBlocking {
        logger.info("🚀 TEST_START: Cache expiry and regeneration testing")

        val searchRequest = SearchRequest(
            sourceAirport = SOURCE_AIRPORT,
            destinationAirport = DESTINATION_AIRPORT,
            departureDate = TEST_DATE,
            passengers = PASSENGERS
        )

        // Phase 1: Initial cache population
        logger.info("🔥 CACHE_POPULATION: Initial search to populate cache")
        val initialStartTime = System.nanoTime()
        val initialResponse = searchService.searchJourneys(searchRequest)
        val initialTimeMs = (System.nanoTime() - initialStartTime) / 1_000_000

        logger.info("✅ Initial search completed in {}ms with {} results (cache miss)",
            initialTimeMs, initialResponse.journeys.size)

        // Phase 2: Immediate cache hit verification
        logger.info("⚡ CACHE_HIT_VERIFICATION: Immediate subsequent search")
        val cachedStartTime = System.nanoTime()
        val cachedResponse = searchService.searchJourneys(searchRequest)
        val cachedTimeMs = (System.nanoTime() - cachedStartTime) / 1_000_000

        logger.info("✅ Cached search completed in {}ms with {} results (cache hit expected)",
            cachedTimeMs, cachedResponse.journeys.size)

        // Verify cache hit was significantly faster
        assertTrue(cachedTimeMs < initialTimeMs / 2,
            "Cache hit should be significantly faster: ${cachedTimeMs}ms vs ${initialTimeMs}ms")

        // Phase 3: Force cache invalidation and regeneration
        logger.info("🗑️ CACHE_INVALIDATION: Clearing cache to test regeneration")
        val cacheKey = searchCacheService.generateCacheKey(
            SOURCE_AIRPORT, DESTINATION_AIRPORT, TEST_DATE
        )
        searchCacheService.invalidateCache("search:*")

        // Phase 4: Cache regeneration verification
        logger.info("🔄 CACHE_REGENERATION: Search after cache invalidation")
        val regenerationStartTime = System.nanoTime()
        val regeneratedResponse = searchService.searchJourneys(searchRequest)
        val regenerationTimeMs = (System.nanoTime() - regenerationStartTime) / 1_000_000

        logger.info("✅ Cache regeneration completed in {}ms with {} results",
            regenerationTimeMs, regeneratedResponse.journeys.size)

        // Validations
        assertTrue(initialResponse.journeys.size == cachedResponse.journeys.size,
            "Cache hit should return same number of results")
        assertTrue(regeneratedResponse.journeys.size == initialResponse.journeys.size,
            "Cache regeneration should return same results")
        assertTrue(regenerationTimeMs > cachedTimeMs,
            "Cache regeneration should be slower than cache hit: ${regenerationTimeMs}ms vs ${cachedTimeMs}ms")

        logger.info("✅ CACHE_BEHAVIOR_VALIDATED: Cache expiry and regeneration working correctly")
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

    private fun cleanDatabase() {
        logger.debug("Cleaning database: journeys, then flights")
        journeyDao.deleteAll()
        flightDao.deleteAll()
    }

    private fun clearCache() {
        logger.debug("Clearing search cache")
        searchCacheService.invalidateCache("search:*")
    }
}