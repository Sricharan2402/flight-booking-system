# Booking Flow Concurrency Performance Test

## Overview

This document describes the **Booking flow concurrency performance test** for the flight booking system. This test validates Redis distributed locking prevents double-booking under high concurrency, ensuring strong consistency for seat reservations and maintaining booking system integrity.

## Key Design Principles

### 1. **Distributed Locking for Consistency**
- **Redis distributed locks**: Prevent concurrent access to same seats
- **ACID transactions**: PostgreSQL ensures database consistency
- **Seat contention**: Limited inventory creates realistic booking pressure
- **Double-booking prevention**: Core system reliability requirement

### 2. **High Concurrency Stress Testing**
- **Realistic contention**: 50 concurrent attempts for limited seats (configurable)
- **Limited inventory**: Only 5 seats per flight for maximum contention
- **Performance validation**: P90 booking time <2000ms under load
- **Success rate validation**: Expected low success rate due to limited capacity

### 3. **Multi-Flight Journey Consistency**
- **Atomic reservations**: All flights in journey reserved or none
- **Cross-flight locking**: Consistent seat allocation across journey flights
- **Transaction rollback**: Failed partial bookings properly cleaned up
- **Data integrity**: No orphaned reservations or inconsistent states

## Test Architecture

### Booking Concurrency Flow
```
BookingRequest → BookingService.createBooking()
                       ↓
               Redis Lock Acquisition (per seat)
                       ↓ (lock acquired)
               Seat Availability Check
                       ↓ (seats available)
               PostgreSQL Transaction Start
                       ↓
               Seat Reservation + Booking Creation
                       ↓
               Transaction Commit + Lock Release
                       ↓ (lock contention/unavailable seats)
               Lock Timeout/Seat Unavailable Error
```

### Infrastructure Requirements
- **Database**: PostgreSQL `flight_booking` with ACID transactions
- **Locking**: Redis with distributed lock configuration
- **Test Data**: Limited seat inventory for maximum contention
- **Configuration**: `application-test.yml` with booking performance thresholds

## Test Data Design

### Limited Flight Network (15 flights)
```
Strategic Network Design:
- DEL ↔ BOM, BLR ↔ MAA, CCU ↔ HYD (hub routes)
- Limited connectivity to focus contention
- 15 flights total for manageable test scope
```

### Constrained Journey Pool (10 journeys)
- Generated from 15-flight network
- Max 2 flights per journey (simpler booking logic)
- Realistic layover constraints
- Focused on high-traffic routes

### Critical Constraint: Limited Seat Inventory
```kotlin
// Only 5 seats per flight for maximum contention
testFlights.forEach { flight ->
    repeat(5) { seatNumber ->
        seatDao.create(
            flightId = flight.id,
            seatNumber = "${('A' + (seatNumber % 3))}${seatNumber + 1}",  // A1, B2, C3, A4, B5
            seatClass = "ECONOMY",
            price = flight.price,
            isAvailable = true
        )
    }
}
```

## Test Scenarios

### Scenario 1: High Concurrency Booking with Seat Contention
**Objective**: Validate Redis locks prevent double-booking under extreme contention

**Test Flow**:
1. **Setup**: Generate 15 flights + 10 journeys with only 5 seats per flight
2. **Target Selection**: Choose single journey for all concurrent attempts
3. **Concurrent Booking**: 50 simultaneous booking attempts for same journey
4. **Validation**: Verify no double-booking, proper error handling, performance metrics

**Expected Outcomes**:
- **Success Rate**: ~2-4% (1-2 successful bookings from 50 attempts)
- **No Double-Booking**: Each seat reserved by exactly one booking
- **Proper Error Classification**: Lock contention, seat unavailable, timeout errors
- **Performance**: P90 booking time <2000ms even under contention

**Success Criteria**:
```kotlin
assertTrue(!hasDoubleBooking, "No double-booking should occur")
assertEquals(successful, actualSuccessfulBookings, "DB consistency validation")
assertTrue(successful <= expectedMaxSuccessfulBookings, "Respect seat capacity")
assertTrue(lockContentionErrors.get() > 0, "Lock contention should occur")
assertTrue(p90 <= maxBookingTimeMs, "P90 booking time ≤2000ms")
```

### Scenario 2: Multi-Flight Journey Booking Consistency
**Objective**: Validate atomic reservations across multiple flights in a journey

**Test Flow**:
1. **Target Selection**: Choose journey with multiple flights
2. **Concurrent Attempts**: 20 simultaneous bookings for multi-flight journey
3. **Consistency Validation**: Verify all flights in successful bookings have proper seat reservations
4. **Atomicity Check**: Ensure failed bookings leave no partial reservations

**Expected Outcomes**:
- **Atomic Success**: Successful bookings reserve seats on ALL journey flights
- **Atomic Failure**: Failed bookings reserve seats on NO journey flights
- **Consistent Counts**: Each flight has same number of passengers for each booking
- **No Orphans**: No partial reservations left in database

## Implementation Details

### Test Class Structure
```kotlin
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BookingFlowConcurrencyApplicationTest {

    @Autowired private lateinit var bookingService: BookingService
    @Autowired private lateinit var journeyDao: JourneyDao
    @Autowired private lateinit var seatDao: SeatDao
    @Autowired private lateinit var bookingDao: BookingDao

    // Configurable concurrency parameters
    @Value("\${test.performance.booking.concurrent-attempts:50}")
    private var concurrentAttempts: Int = 50
}
```

### Concurrent Booking Execution
```kotlin
val futures = (1..concurrentAttempts).map { attemptId ->
    CompletableFuture.supplyAsync({
        try {
            startLatch.await()  // Synchronize all booking attempts

            val requestStartTime = System.nanoTime()
            val bookingRequest = createBookingRequest(targetJourney.id, attemptId)
            val booking = bookingService.createBooking(bookingRequest)
            val responseTimeMs = (System.nanoTime() - requestStartTime) / 1_000_000

            successfulBookings.incrementAndGet()
            BookingResult.Success(booking.id, responseTimeMs)

        } catch (e: Exception) {
            // Categorize different types of booking failures
            when {
                e.message?.contains("lock", ignoreCase = true) == true -> {
                    lockContentionErrors.incrementAndGet()
                }
                e.message?.contains("seat", ignoreCase = true) == true -> {
                    seatUnavailableErrors.incrementAndGet()
                }
                // ... other error categorization
            }
            BookingResult.Failure(e.message, responseTimeMs)
        }
    }, executor)
}
```

### Double-Booking Detection
```kotlin
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

assertTrue(!hasDoubleBooking, "No double-booking should occur: ${bookedSeats.size} vs ${uniqueBookedSeats.size}")
```

### Error Classification and Metrics
```kotlin
// Track different failure types for analysis
val lockContentionErrors = AtomicInteger(0)      // Redis lock failures
val seatUnavailableErrors = AtomicInteger(0)     // Seat inventory exhausted
val timeoutErrors = AtomicInteger(0)             // System timeout errors

// Performance metrics
val responseTimes = mutableListOf<Long>()        // All attempt response times
val successfulBookings = AtomicInteger(0)        // Successful booking count
val failedBookings = AtomicInteger(0)            // Failed booking count
```

## Configuration (application-test.yml)
```yaml
test:
  performance:
    booking:
      concurrent-attempts: 50
      target-success-rate: 2.0        # 2% expected success rate
      max-booking-time-ms: 2000

redis:
  booking:
    reservation-ttl: 30              # 30 seconds for test locks
    cleanup-interval: 10             # 10 seconds between cleanup

spring:
  data:
    redis:
      timeout: 1000ms
      jedis:
        pool:
          max-active: 10
```

## Expected Performance Results

### Concurrency Metrics
- **Concurrent Attempts**: 50 simultaneous booking requests
- **Success Rate**: 2-4% (1-2 successful bookings due to limited seats)
- **Failure Distribution**:
  - Lock contention: 40-60% of failures
  - Seat unavailable: 30-50% of failures
  - Other errors: <10% of failures

### Performance Metrics
- **P90 Booking Time**: <2000ms even under extreme contention
- **Average Response Time**: 200-800ms depending on lock wait times
- **Lock Effectiveness**: >90% of failures due to proper lock contention
- **System Stability**: No timeouts or system errors under load

### Consistency Validation
- **Double-Booking Rate**: 0% (absolute requirement)
- **Database Consistency**: 100% match between reported and actual bookings
- **Atomic Transactions**: 100% (all-or-nothing seat reservations)
- **Multi-Flight Integrity**: All journey flights consistently reserved

## Performance Validation

### Critical Consistency Requirements
```kotlin
// Absolute requirement: No double-booking
assertTrue(!hasDoubleBooking, "No double-booking should occur")

// Database consistency validation
assertEquals(successful, actualSuccessfulBookings,
    "Successful bookings count should match database")

// Capacity constraints respected
assertTrue(successful <= expectedMaxSuccessfulBookings,
    "Successful bookings should not exceed seat capacity")

// Lock effectiveness demonstration
assertTrue(lockContentionErrors.get() > 0,
    "Should have contention errors demonstrating lock effectiveness")
```

### Performance Requirements
```kotlin
// Booking response time under load
assertTrue(p90 <= maxBookingTimeMs,
    "P90 booking time should be ≤2000ms under contention")

// Minimum success rate (proves system allows valid bookings)
assertTrue(actualSuccessRate >= targetSuccessRatePercent,
    "Success rate should be ≥2% (system allows valid bookings)")

// System reliability (no infrastructure failures)
assertTrue(allCompleted, "All booking attempts should complete")
```

## Benefits of This Test

### 1. **Consistency Validation**
- **Double-Booking Prevention**: Absolute guarantee no seat is double-booked
- **ACID Compliance**: Validates PostgreSQL transaction integrity
- **Distributed Lock Effectiveness**: Proves Redis locks work under contention
- **Data Integrity**: Ensures no orphaned or inconsistent reservations

### 2. **Performance Under Load**
- **Contention Handling**: System remains responsive under lock contention
- **Scalability Validation**: Performance acceptable with concurrent users
- **Error Handling**: Proper categorization and handling of different failure types
- **Resource Management**: Efficient lock acquisition and release

### 3. **Production Readiness**
- **Peak Load Simulation**: Tests worst-case booking scenarios
- **Failure Mode Analysis**: Validates system behavior when capacity exhausted
- **Monitoring Baselines**: Establishes performance metrics for production alerts
- **Reliability Proof**: Demonstrates system handles high-demand situations

### 4. **Business Logic Validation**
- **Seat Inventory Management**: Proper tracking of available/booked seats
- **Booking Workflow**: End-to-end booking process validation
- **Multi-Flight Consistency**: Complex journey booking atomicity
- **Customer Experience**: Reasonable response times even under contention

## Running the Tests

### Prerequisites
```bash
# Start infrastructure (PostgreSQL + Redis required)
cd docker && docker compose --env-file ../.env up postgres redis -d

# Verify Redis is accessible for distributed locking
docker compose ps redis
```

### Execute Tests
```bash
# Run booking concurrency test
./gradlew test --tests "*BookingFlowConcurrencyApplicationTest*"

# Run with higher contention (modify application-test.yml)
# concurrent-attempts: 100, max-booking-time-ms: 3000

# Monitor detailed output
./gradlew test --tests "*BookingFlowConcurrencyApplicationTest*" --info
```

### Expected Output
```
🔧 SETUP_START: Setting up booking concurrency test data
✅ Generated 15 flights
✅ Generated 10 journeys
✅ Created seat inventory: 5 seats per flight
🎯 SETUP_COMPLETE: 15 flights, 10 journeys, limited seat inventory for contention testing

🚀 TEST_START: High concurrency booking with Redis lock validation
🎯 TARGET_JOURNEY: Using journey 12345 with 2 flights
📊 INITIAL_CAPACITY: 10 total available seats across journey flights
🎯 EXPECTED_SUCCESS: Maximum 2 successful bookings possible

⚡ CONCURRENT_BOOKING_START: 50 concurrent attempts for same journey
⚡ CONCURRENT_BOOKING_COMPLETE: Total time 3456ms

📈 TIMING_METRICS: Avg=245.3ms, P90=1834.2ms, P95=1987.1ms, Max=1999ms
🎯 BOOKING_SUMMARY: Success=2/50 (4.0%), Failed=48, Lock_Contention=31, Seat_Unavailable=17, Timeouts=0

📊 DB_CONSISTENCY: Expected_Max=2, Reported_Success=2, DB_Confirmed=2
🔐 DOUBLE_BOOKING_CHECK: Total_Booked_Seats=4, Unique_Booked_Seats=4, Double_Booking=false
✅ CONCURRENCY_VALIDATION_PASSED: Redis locks prevented double-booking under high concurrency

🚀 TEST_START: Multi-flight journey booking consistency
⚡ MULTI_FLIGHT_BOOKING_START: 20 concurrent attempts
📊 MULTI_FLIGHT_RESULTS: Success=1, Failed=19
✅ MULTI_FLIGHT_CONSISTENCY_VALIDATED: All multi-flight journeys maintain seat consistency
```

## Future Enhancements

### Advanced Concurrency Testing
- **Mixed Journey Testing**: Concurrent bookings across different journeys
- **Lock Timeout Scenarios**: Various lock timeout configurations
- **Deadlock Detection**: Complex multi-resource locking scenarios

### Performance Optimization
- **Lock Granularity**: Fine-tuned locking strategies for better performance
- **Connection Pool Tuning**: Redis and PostgreSQL connection optimization
- **Batch Operations**: Efficient handling of multi-seat reservations

This booking concurrency test ensures the flight booking system maintains absolute data consistency while providing acceptable performance under high-contention scenarios, validating the core business requirement of preventing double-booking.