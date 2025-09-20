# Search Flow Cache Performance Test

## Overview

This document describes the **Search flow cache performance test** for the flight booking system. This test validates Redis cache behavior, hit ratios, and P90 latency under concurrent load conditions, ensuring the system meets sub-100ms search performance requirements.

## Key Design Principles

### 1. **Cache-First Search Architecture**
- **Redis cache layer**: Primary data source for search queries
- **PostgreSQL fallback**: Read replica fallback when cache misses occur
- **Cache warming**: Initial population of cache for realistic testing
- **Hit ratio validation**: Ensures cache effectiveness under load

### 2. **Concurrent Load Testing**
- **Realistic load**: 150 concurrent search requests (configurable)
- **Performance baselines**: P90 latency < 100ms requirement
- **Cache effectiveness**: >95% hit ratio threshold
- **Stress testing**: Validates system behavior under peak load

### 3. **Cache Lifecycle Testing**
- **Cache warming**: Initial population with database queries
- **Cache hits**: Subsequent requests served from Redis
- **Cache expiry**: Validation of TTL and regeneration behavior
- **Performance comparison**: Cache vs database response times

## Test Architecture

### Search Performance Flow
```
SearchRequest → SearchService.searchJourneys()
                      ↓
              SearchCacheService.get()
                      ↓ (cache hit)
              Redis Cache Response [FAST]
                      ↓ (cache miss)
              PostgreSQL Query [SLOWER]
                      ↓
              Cache population & Response
```

### Infrastructure Requirements
- **Database**: PostgreSQL `flight_booking` (read replicas for search)
- **Cache**: Redis with search cache TTL configuration
- **Test Data**: Realistic flight network with journey combinations
- **Configuration**: `application-test.yml` with performance thresholds

## Test Data Design

### Flight Network (30 flights generated)
```
Airport Network: DEL, BOM, BLR, MAA, CCU, HYD, AMD, COK, GOI, PNQ
Route Examples:
- DEL → BOM (direct flights)
- DEL → BLR → CCU (connecting flights)
- BOM → MAA → HYD (multi-leg journeys)
```

### Journey Combinations (20 journeys)
- Generated from baseline flight network
- Mix of 1-3 flight combinations per journey
- Valid layover constraints (30min-4hr between connections)
- Realistic pricing and timing data

### Cache Test Scenario
- **Source**: DEL (Delhi)
- **Destination**: BOM (Mumbai)
- **Date**: Tomorrow (LocalDate.now().plusDays(1))
- **Passengers**: 2
- **Sort**: By price

## Test Scenarios

### Scenario 1: Cache Warming and Concurrent Load Testing
**Objective**: Validate cache population, hit ratios, and P90 latency under load

**Test Flow**:
1. **Setup**: Generate 30 flights + 20 journeys baseline dataset
2. **Cache Warming**: Execute initial search to populate Redis cache
3. **Concurrent Load**: Execute 150 simultaneous identical search requests
4. **Validation**: Verify P90 latency, cache hit ratio, and error rates

**Expected Outcomes**:
- **Cache Hit Ratio**: >95% (nearly all requests served from cache)
- **P90 Latency**: <100ms (sub-100ms performance requirement)
- **Success Rate**: 100% (no errors or timeouts)
- **Performance Improvement**: Cache hits significantly faster than initial query

**Success Criteria**:
```kotlin
assertTrue(allCompleted, "All requests should complete within timeout")
assertTrue(errors.isEmpty(), "No errors should occur")
assertTrue(p90 <= targetP90LatencyMs, "P90 latency should be ≤100ms")
assertTrue(cacheHitRatio >= 95.0, "Cache hit ratio should be ≥95%")
```

### Scenario 2: Cache Expiry and Regeneration Behavior
**Objective**: Validate cache TTL behavior and regeneration performance

**Test Flow**:
1. **Initial Population**: Execute search to populate cache
2. **Cache Hit Verification**: Immediate subsequent search (should be fast)
3. **Cache Invalidation**: Manually clear cache to simulate expiry
4. **Regeneration Testing**: Search after invalidation (should be slower)

**Expected Outcomes**:
- **Cache Hit Speed**: Significantly faster than initial database query
- **Consistent Results**: Same journey count across cache/database responses
- **Regeneration Behavior**: Post-invalidation queries populate cache again
- **Performance Consistency**: Regenerated cache performs as well as initial cache

## Implementation Details

### Test Class Structure
```kotlin
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SearchFlowWithCachePerformanceApplicationTest {

    @Autowired private lateinit var searchService: SearchService
    @Autowired private lateinit var searchCacheService: SearchCacheService
    @Autowired private lateinit var journeyDao: JourneyDao
    @Autowired private lateinit var flightDao: FlightDao

    // Configurable performance thresholds
    @Value("\${test.performance.search.concurrent-requests:150}")
    private var concurrentRequests: Int = 150

    @Value("\${test.performance.search.target-p90-latency-ms:100}")
    private var targetP90LatencyMs: Long = 100
}
```

### Performance Metrics Collection
```kotlin
// Latency measurement
val requestStartTime = System.nanoTime()
val response = searchService.searchJourneys(searchRequest)
val responseTimeMs = (System.nanoTime() - requestStartTime) / 1_000_000

// Cache hit ratio calculation
val cacheHitRatio = if (responseTimes.size > 1) {
    val fastResponses = responseTimes.count { it < warmupTimeMs / 2 }
    (fastResponses.toDouble() / responseTimes.size) * 100
} else 0.0

// Percentile calculations
val sortedTimes = responseTimes.sorted()
val p90 = calculatePercentile(sortedTimes, 90.0)
val p95 = calculatePercentile(sortedTimes, 95.0)
```

### Concurrent Load Testing
```kotlin
val executor = Executors.newFixedThreadPool(20)
val startLatch = CountDownLatch(1)  // Synchronize all threads
val completionLatch = CountDownLatch(concurrentRequests)

val futures = (1..concurrentRequests).map { requestId ->
    CompletableFuture.supplyAsync({
        startLatch.await()  // Wait for all threads to be ready
        val response = searchService.searchJourneys(searchRequest)
        // Collect timing and response data
    }, executor)
}

startLatch.countDown()  // Start all requests simultaneously
completionLatch.await(30, TimeUnit.SECONDS)  // Wait for completion
```

## Configuration (application-test.yml)
```yaml
test:
  performance:
    search:
      concurrent-requests: 150
      target-p90-latency-ms: 100
      cache-hit-ratio-threshold: 95.0

redis:
  search:
    cache-ttl: 60  # 1 minute for tests

spring:
  data:
    redis:
      timeout: 1000ms
      jedis:
        pool:
          max-active: 10
```

## Expected Performance Results

### Baseline Performance
- **Cache Warming Time**: 50-200ms (database query + cache population)
- **Cache Hit Time**: 5-20ms (Redis response)
- **Cache Miss Time**: 50-150ms (database query)
- **P90 Latency**: <100ms under concurrent load

### Cache Effectiveness
- **Hit Ratio**: >95% after cache warming
- **Performance Improvement**: 5-10x faster for cache hits
- **Consistency**: Same results from cache and database
- **Scalability**: Performance maintained under 150 concurrent requests

### Throughput Metrics
- **Concurrent Capacity**: 150 simultaneous requests
- **Success Rate**: 100% (no timeouts or errors)
- **Cache Behavior**: Proper warming, hitting, and regeneration
- **System Stability**: Consistent performance across test runs

## Performance Validation

### Configurable Thresholds
```kotlin
// P90 latency requirement (sub-100ms search performance)
assertTrue(p90 <= 100, "P90 latency should be ≤100ms")

// Cache effectiveness requirement (efficient cache utilization)
assertTrue(cacheHitRatio >= 95.0, "Cache hit ratio should be ≥95%")

// System reliability (no errors under load)
assertTrue(errors.isEmpty(), "No errors should occur under concurrent load")

// Performance consistency (all requests complete in time)
assertTrue(allCompleted, "All requests should complete within timeout")
```

## Benefits of This Test

### 1. **Performance Validation**
- **SLA Compliance**: Validates sub-100ms search performance requirement
- **Cache Effectiveness**: Ensures Redis cache provides expected performance boost
- **Load Handling**: Validates system behavior under realistic concurrent load

### 2. **Cache Behavior Verification**
- **Hit Ratio Validation**: Ensures cache is effectively reducing database load
- **TTL Behavior**: Validates cache expiry and regeneration work correctly
- **Consistency**: Ensures cache and database return identical results

### 3. **Production Readiness**
- **Scalability Testing**: Validates performance under concurrent user load
- **Infrastructure Validation**: Tests Redis and PostgreSQL integration
- **Monitoring**: Provides baseline metrics for production monitoring

### 4. **Regression Prevention**
- **Performance Baselines**: Establishes timing benchmarks for future changes
- **Cache Regression**: Detects cache hit ratio degradation
- **Infrastructure Issues**: Identifies Redis or database performance problems

## Running the Tests

### Prerequisites
```bash
# Start infrastructure (PostgreSQL + Redis required)
cd docker && docker compose --env-file ../.env up postgres redis -d

# Verify Redis connectivity
docker compose ps redis
```

### Execute Tests
```bash
# Run cache performance test
./gradlew test --tests "*SearchFlowWithCachePerformanceApplicationTest*"

# Run with increased load (modify application-test.yml)
# concurrent-requests: 300, target-p90-latency-ms: 150

# Monitor results in real-time
./gradlew test --tests "*SearchFlowWithCachePerformanceApplicationTest*" --info
```

### Expected Output
```
🔧 SETUP_START: Setting up cache performance test data
✅ Generated 30 flights
✅ Generated 20 journeys
🎯 SETUP_COMPLETE: 30 flights, 20 journeys for cache testing

🚀 TEST_START: Cache warming and concurrent load testing
🔥 CACHE_WARMING_START: Initial search to populate cache
✅ CACHE_WARMING_COMPLETE: Found 15 journeys in 89ms (cache miss expected)

⚡ CONCURRENT_LOAD_START: Executing 150 concurrent requests
⚡ CONCURRENT_LOAD_COMPLETE: 150/150 requests completed in 2341ms

📈 LATENCY_METRICS: Avg=12.3ms, P50=8.2ms, P90=23.1ms, P95=31.4ms, P99=45.7ms
🎯 CACHE_PERFORMANCE: Hit ratio = 98.7%, Target = 95.0%
✅ PERFORMANCE_VALIDATION_PASSED: All cache performance thresholds met

🗑️ CACHE_INVALIDATION: Clearing cache to test regeneration
🔄 CACHE_REGENERATION: Search after cache invalidation
✅ Cache regeneration completed in 76ms with 15 results
✅ CACHE_BEHAVIOR_VALIDATED: Cache expiry and regeneration working correctly
```

## Future Enhancements

### Advanced Cache Testing
- **Multi-key Testing**: Different search parameters and cache key patterns
- **Cache Eviction**: Memory pressure and LRU behavior testing
- **Distributed Cache**: Multi-instance Redis cluster testing

### Performance Profiling
- **Resource Usage**: Memory and CPU utilization during concurrent load
- **Network Latency**: Redis connection pool and network overhead analysis
- **Database Impact**: Read replica load and query performance metrics

This cache performance test ensures the search flow meets stringent performance requirements while validating the effectiveness of the Redis caching layer under realistic concurrent load conditions.