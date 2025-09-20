# Flight Booking System Testing Guide

## Overview

This document describes the comprehensive testing strategy for the Flight Booking System, covering both unit tests and application tests. The system is tested using Kotlin + JUnit5 + Spring Test + MockK for mocking.

---


## Test Architecture

### Unit Tests
- **Purpose**: Test individual service logic with external dependencies mocked
- **Scope**: Service layer business logic, validation, and error handling
- **Isolation**: All database, Redis, and Kafka interactions are mocked
- **Framework**: JUnit5 + MockK + Spring Test

### Application Tests (Future Phase)
- **Purpose**: End-to-end testing with real infrastructure
- **Scope**: Complete workflows from API to database
- **Dependencies**: Local Docker services (Postgres, Redis, Kafka)
- **Framework**: Spring Boot Test with `@SpringBootTest`

---

## Unit Test Coverage

### 1. JourneyGenerationService Tests

**Test File**: `src/test/kotlin/com/flightbooking/services/journeys/JourneyGenerationServiceTest.kt`

#### Test Scenarios

| Scenario | Description | Pre-conditions | Expected Outcome |
|----------|-------------|----------------|------------------|
| **Direct Journey Creation** | Single flight creates single journey | New flight added to system | One journey with flight details created |
| **Forward Extensions** | Connect flights with valid layovers | Two connectable flights on same day | Multi-leg journey created with proper connections |
| **Backward Extensions** | Insert flights before existing journey | Earlier connecting flight available | Extended journey with correct flight order |
| **Invalid Layover Rejection** | Reject connections with invalid timing | Flights with <30min or >4hr layovers | No multi-leg journey created |
| **Duration Validation** | Reject journeys >24 hours | Chain of long-duration flights | Long journeys not generated |
| **Duplicate Prevention** | Avoid duplicate journey signatures | Same flight processed multiple times | Unique journeys only |
| **Cycle Prevention** | Prevent flight reuse in same journey | Circular flight connections available | No cyclic journeys created |
| **Max Depth Enforcement** | Limit journeys to 3 flights maximum | 4+ connectable flights available | No journeys with >3 flights |

#### Key Validations
- ✅ BFS algorithm creates valid journey combinations
- ✅ Layover duration constraints (30min - 4hrs) enforced
- ✅ Total journey duration limit (24hrs) enforced
- ✅ Flight order preservation in multi-leg journeys
- ✅ No duplicate or cyclic journey creation

---

### 2. SearchService Tests

**Test File**: `src/test/kotlin/com/flightbooking/services/search/SearchServiceTest.kt`

#### Test Scenarios

| Scenario | Description | Pre-conditions | Expected Outcome |
|----------|-------------|----------------|------------------|
| **Cache Hit Behavior** | Return cached results when available | Previous search cached in Redis | Cached data returned, no DB query |
| **Cache Miss Behavior** | Query DB and cache results | No cached data for search | DB queried, results cached |
| **Seat Count Integration** | Calculate available seats per journey | Multi-flight journeys with varying seats | Minimum seat count across all flights |
| **Passenger Filtering** | Filter by available seat count | Journeys with insufficient seats | Only adequate journeys returned |
| **Price Sorting** | Sort journeys by total price | Multiple journeys with different prices | Ascending price order |
| **Duration Sorting** | Sort by total journey time | Journeys with different durations | Ascending duration order |
| **Result Pagination** | Apply limit to result set | More journeys than requested limit | Limited results with correct total count |
| **Empty Results** | Handle no matching journeys | Search with no available journeys | Empty result set, proper caching |

#### Key Validations
- ✅ Redis cache integration for performance
- ✅ Accurate seat availability calculation
- ✅ Proper filtering and sorting logic
- ✅ Pagination and result limiting
- ✅ Graceful handling of edge cases

---

### 3. BookingService Tests

**Test File**: `src/test/kotlin/com/flightbooking/services/booking/BookingServiceTest.kt`

#### Test Scenarios

| Scenario | Description | Pre-conditions | Expected Outcome |
|----------|-------------|----------------|------------------|
| **Happy Path Booking** | Complete successful booking | Valid journey with available seats | Booking created, seats reserved |
| **Multi-Flight Booking** | Book journey with multiple flights | Multi-leg journey available | All flight seats reserved atomically |
| **Journey Validation** | Validate journey exists | Invalid or missing journey ID | Exception thrown, no booking created |
| **Insufficient Seats** | Handle seat shortage | Fewer seats than passengers | Exception thrown, no reservation |
| **Reservation Failure** | Handle Redis lock failure | Seat already reserved by another user | Exception thrown, no booking |
| **Partial Rollback** | Rollback on mid-process failure | First flight reserved, second fails | All reservations released |
| **Database Failure** | Handle DB save errors | Redis reservation successful, DB fails | Reservations released, error thrown |
| **Payment Integration** | Include payment information | Valid payment ID provided | Payment ID in booking response |

#### Key Validations
- ✅ Atomic seat reservation across multiple flights
- ✅ Proper rollback on partial failures
- ✅ Redis distributed locking integration
- ✅ Database transaction management
- ✅ Error handling and cleanup

---

### 4. AdminFlightService Tests

**Test File**: `src/test/kotlin/com/flightbooking/services/admin/AdminFlightServiceTest.kt`

#### Test Scenarios

| Scenario | Description | Pre-conditions | Expected Outcome |
|----------|-------------|----------------|------------------|
| **Flight Creation** | Create new flight successfully | Valid flight details provided | Flight and seats created |
| **Seat Generation** | Create correct number of seats | Flight with specified seat count | Exact seat count with unique numbers |
| **Kafka Event Publishing** | Publish flight creation event | Flight successfully created | Event sent to Kafka for journey generation |
| **Validation Errors** | Reject invalid flight data | Invalid times, airports, prices | Validation errors, no flight created |
| **Database Errors** | Handle persistence failures | DB connection issues | Appropriate error handling |
| **Event Failure** | Handle Kafka publishing errors | Kafka unavailable during creation | Flight created, event error propagated |

#### Key Validations
- ✅ Complete flight creation workflow
- ✅ Automatic seat generation and numbering
- ✅ Kafka event integration for journey generation
- ✅ Input validation and error handling
- ✅ Database transaction management

---

## Running Tests

### Prerequisites
```bash
# Ensure Docker services are running (for future integration tests)
cd docker && docker compose --env-file ../.env up -d

# Verify services are healthy
docker compose --env-file ../.env ps
```

### Execute Unit Tests
```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "JourneyGenerationServiceTest"

# Run tests with coverage report
./gradlew test jacocoTestReport

# Run tests in specific package
./gradlew test --tests "com.flightbooking.services.*"
```

### Test Configuration
- **Profile**: Tests use `application-test.yml` configuration
- **Database**: Connects to `flight_booking_test` database
- **Isolation**: Each test class is isolated with fresh mocks
- **Parallel Execution**: JUnit5 configured for concurrent test execution

---

## Test Data Management

### TestDataFactory Utilities
The `TestDataFactory` provides helper methods for creating test objects:

```kotlin
// Create test flight
val flight = TestDataFactory.createTestFlight(
    sourceAirport = "DEL",
    destinationAirport = "BOM"
)

// Create connected flights for journey testing
val (flight1, flight2) = TestDataFactory.createConnectedFlights(
    firstSource = "DEL",
    connectingAirport = "BOM",
    finalDestination = "MAA"
)

// Create booking request
val bookingRequest = TestDataFactory.createTestBookingRequest(
    journeyId = journeyId,
    passengerCount = 2
)
```

### Mock Strategy
- **MockK**: Used for Kotlin-friendly mocking with DSL syntax
- **Strict Mocking**: All interactions must be explicitly defined
- **Verification**: All expected service calls are verified
- **Isolation**: Mocks are cleared between test methods

---

## Test Performance

### Execution Metrics
- **Unit Test Count**: 40+ comprehensive test cases
- **Execution Time**: <30 seconds for complete unit test suite
- **Coverage Target**: >90% line coverage for service layer
- **Parallel Execution**: Tests run concurrently for faster feedback

### CI/CD Integration
```bash
# Fast feedback for development
./gradlew test --continue

# Quality gates for CI pipeline
./gradlew check # Includes tests + static analysis
```

---

## Future Enhancements

### Application Test Phase (Next Implementation)
1. **End-to-End Workflows**: Complete user journeys from API to database
2. **Performance Testing**: Search latency and booking concurrency
3. **Infrastructure Testing**: Docker service integration
4. **Load Testing**: Concurrent user simulation

### Additional Test Types
- **Contract Testing**: API contract validation
- **Security Testing**: Authentication and authorization
- **Performance Profiling**: Memory and CPU usage analysis
- **Chaos Testing**: Failure scenario simulation

---

## Bugs Discovery During Testing

### BookingService Seat Reservation Leak

**Issue**: During unit testing, we discovered a critical distributed system consistency bug in `BookingService.createBooking()`.

**Problem**: When database save fails, Redis seat reservations are not properly cleaned up, leading to:
- Available seats appearing unavailable to other users
- Revenue loss from phantom "fully booked" flights
- Redis lock leakage requiring TTL expiration cleanup

**Root Cause**: The original code structure placed Redis seat reservations outside the try-catch block that handles cleanup:

```kotlin
// BUGGY CODE (before fix)
val reservedSeats = reserveSeatsWithSortedSet(flightIds, request.passengerCount) // ✅ Succeeds
val savedBooking = bookingDao.save(booking) // ❌ Fails, throws exception

try {
    // Seat updates and cleanup - NEVER REACHED
} catch (e: Exception) {
    // Cleanup logic - NEVER REACHED because exception thrown before try block
}
// reservedSeats remain locked in Redis! 💥
```

**Fix Applied**: Wrapped entire booking logic in try-catch to ensure Redis cleanup on any failure:

```kotlin
var reservedSeats: Map<UUID, List<UUID>>? = null
try {
    reservedSeats = reserveSeatsWithSortedSet(flightIds, request.passengerCount)
    val savedBooking = bookingDao.save(booking)
    // ... rest of booking logic
} catch (e: Exception) {
    // Clean up ANY Redis reservations that were made
    reservedSeats?.let { reservations ->
        for ((flightId, seatIds) in reservations) {
            seatReservationService.releaseSeats(flightId, seatIds)
        }
    }
    throw e
}
```

**Test Evidence**: Unit test `should release reservations when database save fails` initially failed, revealing this bug.

---

## Troubleshooting

### Common Issues

**Mock Verification Failures**
```kotlin
// Ensure all mocked calls are verified
verify(exactly = 1) { mockService.method(any()) }
verify(exactly = 0) { mockService.unexpectedMethod(any()) }
```

**Test Data Isolation**
```kotlin
@BeforeEach
fun setup() {
    clearAllMocks() // Essential for test isolation
}
```

**Async Operation Testing**
```kotlin
// Use appropriate timeout for async operations
eventually(timeout = Duration.ofSeconds(5)) {
    verify { kafkaProducer.send(any()) }
}
```

### Debug Strategies
1. **Logging**: Enable DEBUG level for test packages
2. **Breakpoints**: Use IDE debugging for complex scenarios
3. **Mock Inspection**: Verify mock interaction logs
4. **Test Isolation**: Run individual tests to isolate issues

This comprehensive testing strategy ensures high-quality, reliable flight booking system functionality with extensive coverage of business logic, error handling, and integration points.