package com.flightbooking.utils

import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

object PerformanceTestUtils {

    private val logger = LoggerFactory.getLogger(PerformanceTestUtils::class.java)

    fun calculatePercentile(sortedValues: List<Long>, percentile: Double): Double {
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

    data class ConcurrentTestResult<T>(
        val successfulResults: List<T>,
        val failures: List<Exception>,
        val responseTimes: List<Long>,
        val totalTimeMs: Long,
        val allCompleted: Boolean
    )

    fun <T> executeConcurrentTest(
        concurrentRequests: Int,
        timeoutSeconds: Long = 30,
        threadPoolSize: Int = 20,
        operation: (requestId: Int) -> T
    ): ConcurrentTestResult<T> {
        val executor = Executors.newFixedThreadPool(threadPoolSize)
        val startLatch = CountDownLatch(1)
        val completionLatch = CountDownLatch(concurrentRequests)

        val successfulResults = mutableListOf<T>()
        val failures = mutableListOf<Exception>()
        val responseTimes = mutableListOf<Long>()

        val futures = (1..concurrentRequests).map { requestId ->
            CompletableFuture.supplyAsync({
                try {
                    startLatch.await()

                    val result: T
                    val responseTimeMs = measureTimeMillis {
                        result = operation(requestId)
                    }

                    synchronized(successfulResults) {
                        successfulResults.add(result)
                    }
                    synchronized(responseTimes) {
                        responseTimes.add(responseTimeMs)
                    }

                    result
                } catch (e: Exception) {
                    logger.debug("Request {} failed: {}", requestId, e.message)
                    synchronized(failures) {
                        failures.add(e)
                    }
                    throw e
                } finally {
                    completionLatch.countDown()
                }
            }, executor)
        }

        val testStartTime = System.currentTimeMillis()
        startLatch.countDown()

        val allCompleted = completionLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        val totalTimeMs = System.currentTimeMillis() - testStartTime

        executor.shutdown()

        return ConcurrentTestResult(
            successfulResults = successfulResults,
            failures = failures,
            responseTimes = responseTimes,
            totalTimeMs = totalTimeMs,
            allCompleted = allCompleted
        )
    }

    data class LatencyMetrics(
        val count: Int,
        val min: Long,
        val max: Long,
        val average: Double,
        val p50: Double,
        val p90: Double,
        val p95: Double,
        val p99: Double
    ) {
        fun logSummary() {
            logger.info("📈 LATENCY_METRICS: " +
                    "count={} avg={}ms min={}ms max={}ms " +
                    "p50={}ms p90={}ms p95={}ms p99={}ms",
                count,
                String.format("%.1f", average),
                min, max,
                String.format("%.1f", p50),
                String.format("%.1f", p90),
                String.format("%.1f", p95),
                String.format("%.1f", p99))
        }
    }

    fun calculateLatencyMetrics(responseTimes: List<Long>): LatencyMetrics {
        if (responseTimes.isEmpty()) {
            return LatencyMetrics(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val sorted = responseTimes.sorted()

        return LatencyMetrics(
            count = sorted.size,
            min = sorted.first(),
            max = sorted.last(),
            average = sorted.average(),
            p50 = calculatePercentile(sorted, 50.0),
            p90 = calculatePercentile(sorted, 90.0),
            p95 = calculatePercentile(sorted, 95.0),
            p99 = calculatePercentile(sorted, 99.0)
        )
    }

    data class ThroughputMetrics(
        val totalRequests: Int,
        val successfulRequests: Int,
        val failedRequests: Int,
        val successRate: Double,
        val requestsPerSecond: Double,
        val totalTimeMs: Long
    ) {
        fun logSummary() {
            logger.info("🎯 THROUGHPUT_METRICS: " +
                    "total={} success={} failed={} " +
                    "success_rate={}% rps={} total_time={}ms",
                totalRequests, successfulRequests, failedRequests,
                String.format("%.1f", successRate),
                String.format("%.1f", requestsPerSecond),
                totalTimeMs)
        }
    }

    fun calculateThroughputMetrics(
        totalRequests: Int,
        successfulRequests: Int,
        totalTimeMs: Long
    ): ThroughputMetrics {
        val failedRequests = totalRequests - successfulRequests
        val successRate = if (totalRequests > 0) {
            (successfulRequests.toDouble() / totalRequests) * 100
        } else 0.0
        val requestsPerSecond = if (totalTimeMs > 0) {
            (totalRequests.toDouble() / totalTimeMs) * 1000
        } else 0.0

        return ThroughputMetrics(
            totalRequests = totalRequests,
            successfulRequests = successfulRequests,
            failedRequests = failedRequests,
            successRate = successRate,
            requestsPerSecond = requestsPerSecond,
            totalTimeMs = totalTimeMs
        )
    }

    class TestTimer {
        private var startTime: Long = 0
        private var endTime: Long = 0

        fun start(): TestTimer {
            startTime = System.currentTimeMillis()
            return this
        }

        fun stop(): TestTimer {
            endTime = System.currentTimeMillis()
            return this
        }

        fun durationMs(): Long = endTime - startTime

        fun logDuration(operation: String) {
            logger.info("⏱️ TIMER: {} completed in {}ms", operation, durationMs())
        }
    }

    inline fun <T> timed(operation: String, block: () -> T): T {
        val timer = TestTimer().start()
        val result = block()
        timer.stop().logDuration(operation)
        return result
    }
}