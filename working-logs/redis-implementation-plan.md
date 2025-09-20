# Redis Implementation Plan

## Overview
This plan outlines the implementation of Redis for search caching and booking distributed locking to address performance and concurrency issues in the flight booking system.

## 1. Dependencies and Configuration

### 1.1 Gradle Dependencies
Add to `build.gradle.kts`:
```kotlin
dependencies {
    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("redis.clients:jedis:5.0.2")

    // Connection pooling
    implementation("org.apache.commons:commons-pool2:2.11.1")
}
```

### 1.2 Redis Configuration Properties
Add to `application.yml`:
```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
      jedis:
        pool:
          max-active: 50
          max-idle: 10
          min-idle: 2
          max-wait: 2000ms

# Custom Redis configuration
redis:
  search:
    cache-ttl: 600  # 10 minutes for search cache
  booking:
    reservation-ttl: 300    # 5 minutes for seat reservations
    cleanup-interval: 60    # seconds between cleanup operations
```

### 1.3 Redis Configuration Class
Create `src/main/kotlin/com/flightbooking/common/config/RedisConfiguration.kt`:
```kotlin
@Configuration
@EnableConfigurationProperties(RedisProperties::class)
class RedisConfiguration {

    @Bean
    fun jedisConnectionFactory(redisProperties: RedisProperties): JedisConnectionFactory {
        val config = JedisPoolConfig().apply {
            maxTotal = 50
            maxIdle = 10
            minIdle = 2
            maxWaitMillis = 2000
        }

        val factory = JedisConnectionFactory().apply {
            hostName = redisProperties.host
            port = redisProperties.port
            password = redisProperties.password
            poolConfig = config
        }

        return factory
    }

    @Bean
    fun redisTemplate(connectionFactory: JedisConnectionFactory): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = GenericJackson2JsonRedisSerializer()
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = GenericJackson2JsonRedisSerializer()
        }
    }

    @Bean
    fun stringRedisTemplate(connectionFactory: JedisConnectionFactory): StringRedisTemplate {
        return StringRedisTemplate(connectionFactory)
    }

    @Bean
    fun searchCacheRedisTemplate(connectionFactory: JedisConnectionFactory): RedisTemplate<String, String> {
        return RedisTemplate<String, String>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = StringRedisSerializer()
        }
    }
}
```

## 2. Search Caching Implementation

### 2.1 Cache Service Interface
Create `src/main/kotlin/com/flightbooking/services/cache/SearchCacheService.kt`:
```kotlin
interface SearchCacheService {
    fun getCachedSearchResults(cacheKey: String): SearchResponse?
    fun cacheSearchResults(cacheKey: String, searchResponse: SearchResponse, ttlSeconds: Long)
    fun invalidateCache(pattern: String)
    fun generateCacheKey(source: String, destination: String, date: LocalDate): String
}
```

### 2.2 Cache Service Implementation
Create `src/main/kotlin/com/flightbooking/services/cache/SearchCacheServiceImpl.kt`:
```kotlin
@Service
class SearchCacheServiceImpl(
    @Qualifier("searchCacheRedisTemplate") private val searchCacheRedisTemplate: RedisTemplate<String, String>,
    @Value("\${redis.search.cache-ttl}") private val cacheTtl: Long
) : SearchCacheService {

    private val logger = LoggerFactory.getLogger(SearchCacheServiceImpl::class.java)
    private val objectMapper = ObjectMapper().registerModule(JavaTimeModule())

    override fun getCachedSearchResults(cacheKey: String): SearchResponse? {
        return try {
            val cachedJson = searchCacheRedisTemplate.opsForValue().get(cacheKey)
            cachedJson?.let {
                objectMapper.readValue(it, SearchResponse::class.java)
            }
        } catch (e: Exception) {
            logger.warn("Failed to retrieve cached search results for key: $cacheKey", e)
            null
        }
    }

    override fun cacheSearchResults(cacheKey: String, searchResponse: SearchResponse, ttlSeconds: Long) {
        try {
            val jsonValue = objectMapper.writeValueAsString(searchResponse)
            searchCacheRedisTemplate.opsForValue().set(cacheKey, jsonValue, Duration.ofSeconds(ttlSeconds))
            logger.debug("Cached search results for key: $cacheKey")
        } catch (e: Exception) {
            logger.error("Failed to cache search results for key: $cacheKey", e)
        }
    }

    override fun invalidateCache(pattern: String) {
        try {
            val keys = searchCacheRedisTemplate.keys(pattern)
            if (keys.isNotEmpty()) {
                searchCacheRedisTemplate.delete(keys)
                logger.info("Invalidated ${keys.size} cache entries for pattern: $pattern")
            }
        } catch (e: Exception) {
            logger.error("Failed to invalidate cache for pattern: $pattern", e)
        }
    }

    override fun generateCacheKey(source: String, destination: String, date: LocalDate): String {
        return "journeys:$source:$destination:$date"
    }
}
```

### 2.3 Search Service Modifications
Update `SearchService.kt`:
```kotlin
@Service
class SearchService(
    private val journeyDao: JourneyDao,
    private val seatDao: SeatDao,
    private val flightDao: FlightDao,
    private val searchCacheService: SearchCacheService
) {

    fun searchJourneys(request: SearchRequest): SearchResponse {
        logger.info("Searching journeys from ${request.sourceAirport} to ${request.destinationAirport} on ${request.departureDate}")

        // Generate cache key
        val cacheKey = searchCacheService.generateCacheKey(
            request.sourceAirport,
            request.destinationAirport,
            request.departureDate
        )

        // Check cache first
        searchCacheService.getCachedSearchResults(cacheKey)?.let { cachedResponse ->
            logger.debug("Cache hit for key: $cacheKey")
            return filterAndSortCachedResults(cachedResponse, request)
        }

        logger.debug("Cache miss for key: $cacheKey")

        // Original database query logic...
        val searchResponse = performDatabaseSearch(request)

        // Cache the results
        searchCacheService.cacheSearchResults(cacheKey, searchResponse, 600)

        return searchResponse
    }

    private fun filterAndSortCachedResults(cachedResponse: SearchResponse, request: SearchRequest): SearchResponse {
        // Apply passenger filtering and sorting to cached results
        val filteredJourneys = cachedResponse.journeys
            .filter { it.availableSeats >= request.passengers }

        val sortedJourneys = applySorting(filteredJourneys, request.sortBy)
        val limitedJourneys = applyLimit(sortedJourneys, request.limit)

        return SearchResponse(
            journeys = limitedJourneys,
            totalCount = filteredJourneys.size
        )
    }

    // Extract existing logic to separate method
    private fun performDatabaseSearch(request: SearchRequest): SearchResponse {
        // Existing search logic...
    }
}
```

## 3. Seat Reservation with Sorted Sets

### 3.1 Seat Reservation Service Interface
Create `src/main/kotlin/com/flightbooking/services/reservation/SeatReservationService.kt`:
```kotlin
interface SeatReservationService {
    fun reserveSeats(flightId: UUID, seatIds: List<UUID>, reservationTtlSeconds: Long): Boolean
    fun releaseSeats(flightId: UUID, seatIds: List<UUID>): Boolean
    fun getAvailableSeats(flightId: UUID, allSeats: List<UUID>): List<UUID>
    fun cleanupExpiredReservations(flightId: UUID): Int
    fun generateReservationKey(flightId: UUID): String
}
```

### 3.2 Seat Reservation Service Implementation
Create `src/main/kotlin/com/flightbooking/services/reservation/SeatReservationServiceImpl.kt`:
```kotlin
@Service
class SeatReservationServiceImpl(
    private val redisTemplate: RedisTemplate<String, Any>,
    @Value("\${redis.booking.reservation-ttl}") private val reservationTtl: Long
) : SeatReservationService {

    private val logger = LoggerFactory.getLogger(SeatReservationServiceImpl::class.java)

    override fun reserveSeats(flightId: UUID, seatIds: List<UUID>, reservationTtlSeconds: Long): Boolean {
        return try {
            val reservationKey = generateReservationKey(flightId)
            val currentTime = System.currentTimeMillis()
            val expiryTime = currentTime + (reservationTtlSeconds * 1000)

            // Use Lua script for atomic check-and-reserve operation
            val luaScript = """
                -- Clean up expired reservations first
                redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])

                -- Check if any seats are already reserved
                for i = 2, #ARGV, 2 do
                    local seatId = ARGV[i]
                    if redis.call('ZSCORE', KEYS[1], seatId) then
                        return 0  -- Seat already reserved
                    end
                end

                -- Reserve all seats
                for i = 2, #ARGV, 2 do
                    local seatId = ARGV[i]
                    local expiryTime = ARGV[i + 1]
                    redis.call('ZADD', KEYS[1], expiryTime, seatId)
                end

                -- Set expiry on the sorted set key
                redis.call('EXPIRE', KEYS[1], ARGV[#ARGV])

                return 1  -- Success
            """.trimIndent()

            // Prepare arguments: [currentTime, seatId1, expiryTime1, seatId2, expiryTime2, ..., keyTtl]
            val args = mutableListOf<String>().apply {
                add(currentTime.toString()) // ARGV[1] - current time for cleanup
                seatIds.forEach { seatId ->
                    add(seatId.toString())    // ARGV[i] - seat ID
                    add(expiryTime.toString()) // ARGV[i+1] - expiry time
                }
                add((reservationTtlSeconds + 300).toString()) // ARGV[#ARGV] - key TTL with buffer
            }

            val result = redisTemplate.execute<Long> { connection ->
                connection.eval(luaScript.toByteArray(), 1, reservationKey.toByteArray(), *args.map { it.toByteArray() }.toTypedArray())
            } as Long?

            if (result == 1L) {
                logger.info("Reserved ${seatIds.size} seats for flight $flightId until $expiryTime")
                true
            } else {
                logger.warn("Failed to reserve seats for flight $flightId - some seats already reserved")
                false
            }

        } catch (e: Exception) {
            logger.error("Failed to reserve seats for flight $flightId", e)
            false
        }
    }

    override fun releaseSeats(flightId: UUID, seatIds: List<UUID>): Boolean {
        return try {
            val reservationKey = generateReservationKey(flightId)
            val zSetOps = redisTemplate.opsForZSet()

            val removedCount = zSetOps.remove(reservationKey, *seatIds.map { it.toString() }.toTypedArray())

            logger.debug("Released $removedCount seats for flight $flightId")
            true

        } catch (e: Exception) {
            logger.error("Failed to release seats for flight $flightId", e)
            false
        }
    }

    override fun getAvailableSeats(flightId: UUID, allSeats: List<UUID>): List<UUID> {
        return try {
            val reservationKey = generateReservationKey(flightId)

            // Clean up expired reservations first
            cleanupExpiredReservations(flightId)

            val zSetOps = redisTemplate.opsForZSet()

            // Get all currently reserved seats
            val reservedSeatIds = zSetOps.range(reservationKey, 0, -1)
                ?.map { UUID.fromString(it.toString()) }
                ?.toSet() ?: emptySet()

            // Filter out reserved seats from all available seats
            val availableSeats = allSeats.filterNot { seatId ->
                reservedSeatIds.contains(seatId)
            }

            logger.debug("Flight $flightId: ${allSeats.size} total seats, ${reservedSeatIds.size} reserved, ${availableSeats.size} available")
            availableSeats

        } catch (e: Exception) {
            logger.error("Failed to get available seats for flight $flightId", e)
            allSeats // Return all seats if Redis fails (fallback to DB validation)
        }
    }

    override fun cleanupExpiredReservations(flightId: UUID): Int {
        return try {
            val reservationKey = generateReservationKey(flightId)
            val currentTime = System.currentTimeMillis()

            val zSetOps = redisTemplate.opsForZSet()

            // Remove all entries with score (expiry time) less than current time
            val removedCount = zSetOps.removeRangeByScore(reservationKey, 0.0, currentTime.toDouble())

            if (removedCount != null && removedCount > 0) {
                logger.debug("Cleaned up $removedCount expired reservations for flight $flightId")
            }

            removedCount?.toInt() ?: 0

        } catch (e: Exception) {
            logger.error("Failed to cleanup expired reservations for flight $flightId", e)
            0
        }
    }

    override fun generateReservationKey(flightId: UUID): String {
        return "seat_reservations:$flightId"
    }
}
```

### 3.3 Booking Service Modifications
Update `BookingService.kt`:
```kotlin
@Service
class BookingService(
    private val bookingDao: BookingDao,
    private val seatDao: SeatDao,
    private val journeyDao: JourneyDao,
    private val flightDao: FlightDao,
    private val seatReservationService: SeatReservationService
) {

    @Transactional
    fun createBooking(request: BookingRequest): BookingResponse {
        logger.info("Creating booking for journey ${request.journeyId} with ${request.passengerCount} passengers")

        // Validate journey exists and is active
        val journey = journeyDao.findById(request.journeyId)
            ?: throw IllegalArgumentException("Journey not found: ${request.journeyId}")

        // Get flight details for the journey
        val flightIds = journey.flightDetails.map { it.flightId }
        val flights = flightIds.mapNotNull { flightDao.findById(it) }
            .sortedBy { flight -> journey.flightDetails.find { it.flightId == flight.flightId }?.order ?: 0 }

        // Reserve seats using sorted set approach
        val seatAssignments = reserveSeatsWithSortedSet(flightIds, request.passengerCount)

        // Calculate total price
        val totalPrice = journey.totalPrice * BigDecimal(request.passengerCount)

        // Create booking record
        val booking = Booking(
            bookingId = UUID.randomUUID(),
            userId = request.userId ?: UUID.randomUUID(),
            journeyId = request.journeyId,
            numberOfSeats = request.passengerCount,
            status = BookingStatus.CONFIRMED,
            paymentId = request.paymentId,
            bookingTime = ZonedDateTime.now(),
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now()
        )

        val savedBooking = bookingDao.save(booking)

        try {
            // Update seats with booking ID in database
            for ((flightId, seatIds) in seatAssignments) {
                seatDao.updateSeatsForBooking(seatIds, savedBooking.bookingId, SeatStatus.BOOKED)
            }

            // Release seat reservations from Redis (they are now booked in DB)
            for ((flightId, seatIds) in seatAssignments) {
                seatReservationService.releaseSeats(flightId, seatIds)
            }

            logger.info("Successfully created booking with ID: ${savedBooking.bookingId}")

            // Build response
            return buildBookingResponse(savedBooking, seatAssignments, journey, flights)

        } catch (e: Exception) {
            logger.error("Failed to finalize booking, releasing reservations", e)
            // Release reservations if booking fails
            for ((flightId, seatIds) in seatAssignments) {
                seatReservationService.releaseSeats(flightId, seatIds)
            }
            throw e
        }
    }

    private fun reserveSeatsWithSortedSet(flightIds: List<UUID>, passengerCount: Int): Map<UUID, List<UUID>> {
        val seatAssignments = mutableMapOf<UUID, List<UUID>>()
        val reservedFlights = mutableListOf<UUID>()

        try {
            for (flightId in flightIds) {
                // Get all available seats from database
                val dbAvailableSeats = seatDao.findAvailableSeatsByFlightId(flightId)
                val allAvailableSeatIds = dbAvailableSeats.map { it.seatId }

                // Filter out seats that are reserved in Redis
                val actuallyAvailableSeats = seatReservationService.getAvailableSeats(flightId, allAvailableSeatIds)

                if (actuallyAvailableSeats.size < passengerCount) {
                    throw IllegalArgumentException("Insufficient seats available on flight $flightId. Required: $passengerCount, Available: ${actuallyAvailableSeats.size}")
                }

                val seatsToReserve = actuallyAvailableSeats.take(passengerCount)

                // Reserve seats in Redis sorted set
                if (!seatReservationService.reserveSeats(flightId, seatsToReserve, 300)) { // 5 minute reservation
                    throw IllegalStateException("Unable to reserve seats for flight $flightId. Please try again.")
                }

                seatAssignments[flightId] = seatsToReserve
                reservedFlights.add(flightId)
            }

            return seatAssignments

        } catch (e: Exception) {
            logger.error("Error during seat reservation, rolling back", e)
            // Roll back any reservations made so far
            for (flightId in reservedFlights) {
                seatAssignments[flightId]?.let { seatIds ->
                    seatReservationService.releaseSeats(flightId, seatIds)
                }
            }
            throw e
        }
    }

    // Enhanced validateSeatAvailability that considers Redis reservations
    private fun validateSeatAvailability(flightIds: List<UUID>, passengerCount: Int) {
        for (flightId in flightIds) {
            val dbAvailableSeats = seatDao.findAvailableSeatsByFlightId(flightId)
            val allAvailableSeatIds = dbAvailableSeats.map { it.seatId }

            val actuallyAvailable = seatReservationService.getAvailableSeats(flightId, allAvailableSeatIds)

            if (actuallyAvailable.size < passengerCount) {
                throw IllegalArgumentException("Insufficient seats available on flight $flightId. Required: $passengerCount, Available: ${actuallyAvailable.size}")
            }
        }
    }

    // Rest of existing methods...
}
```

## 4. Cache Invalidation Strategy

### 4.1 Journey Cache Invalidation
When new journeys are created, invalidate relevant cache entries:

```kotlin
// In JourneyGenerationService or AdminFlightService
@EventListener
fun onJourneyCreated(event: JourneyCreatedEvent) {
    // Invalidate cache for affected routes
    val pattern = "journeys:${event.sourceAirport}:${event.destinationAirport}:*"
    searchCacheService.invalidateCache(pattern)
}
```

## 5. Implementation Priority

1. **Phase 1**: Redis configuration and connection setup
2. **Phase 2**: Distributed locking for booking service
3. **Phase 3**: Search caching implementation
4. **Phase 4**: Cache invalidation strategies
5. **Phase 5**: Monitoring and performance tuning

## 6. Testing Strategy

### 6.1 Unit Tests
- Test lock acquisition/release scenarios
- Test cache hit/miss scenarios
- Test concurrent booking scenarios

### 6.2 Integration Tests
- Test Redis connectivity
- Test booking with concurrent requests
- Test search performance with caching

This implementation will address the critical concurrency issues in booking and significantly improve search performance by meeting the <100ms requirement outlined in the design document.