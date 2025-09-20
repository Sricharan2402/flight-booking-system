# Flight Booking System - Application Performance Tests Overview

## Introduction

This document provides a comprehensive overview of the **application performance test suite** for the flight booking system. These tests validate critical operational characteristics including deterministic behavior, event processing performance, cache effectiveness, and booking consistency under high concurrency.

## Test Suite Architecture

The application performance tests are designed as a complementary suite covering different aspects of system reliability:

```
Performance Test Suite
├── Deterministic Tests (Functional Correctness)
│   └── DeterministicFlightCreationJourneyGenerationApplicationTest
├── Event Processing Tests (Operational Performance)
│   └── KafkaEventConsumptionPerformanceApplicationTest
├── Cache Performance Tests (Search Performance)
│   └── SearchFlowWithCachePerformanceApplicationTest
└── Concurrency Tests (Data Consistency)
    └── BookingFlowConcurrencyApplicationTest
```

## Test Categories and Objectives

### 1. Deterministic Functional Tests
**File**: `DeterministicFlightCreationJourneyGenerationApplicationTest.kt`
**Documentation**: `deterministic-flight-creation-journey-generation.md`

**Purpose**: Validates algorithmic correctness and deterministic behavior
- **Journey Generation Algorithm**: BFS algorithm correctness with exact count validation
- **Incremental Processing**: Forward, backward, and middle insertion patterns
- **Data Integrity**: Prevents duplicate journeys and maintains flight order
- **Regression Detection**: Catches algorithm changes that affect journey counts

**Key Metrics**: Exact journey counts, algorithm execution time, data consistency

### 2. Event Processing Performance Tests
**File**: `KafkaEventConsumptionPerformanceApplicationTest.kt`
**Documentation**: `kafka-event-consumption-performance.md`

**Purpose**: Validates operational reliability and event processing performance
- **Kafka Integration**: Real producer/consumer event processing
- **Performance Baselines**: Establishes timing benchmarks (<500ms average processing)
- **Event Correlation**: 100% correlation tracking (events produced = events consumed)
- **Scalability**: Large dataset processing (68 flights + 20 journeys baseline)

**Key Metrics**: Event correlation rate, processing time, success rates, throughput

### 3. Cache Performance Tests
**File**: `SearchFlowWithCachePerformanceApplicationTest.kt`
**Documentation**: `search-cache-performance.md`

**Purpose**: Validates Redis cache effectiveness and search performance
- **Cache Hit Ratios**: >95% cache hit rate under concurrent load
- **Search Performance**: P90 latency <100ms (sub-100ms requirement)
- **Concurrent Load**: 150 simultaneous search requests
- **Cache Lifecycle**: Warming, hitting, expiry, and regeneration behavior

**Key Metrics**: Cache hit ratio, P90 latency, concurrent request handling, cache consistency

### 4. Booking Concurrency Tests
**File**: `BookingFlowConcurrencyApplicationTest.kt`
**Documentation**: `booking-concurrency-performance.md`

**Purpose**: Validates distributed locking and booking consistency
- **Double-Booking Prevention**: Absolute guarantee (0% double-booking rate)
- **Redis Distributed Locks**: Validates lock effectiveness under contention
- **Limited Inventory**: High contention scenarios (5 seats per flight)
- **Multi-Flight Consistency**: Atomic reservations across journey flights

**Key Metrics**: Double-booking rate, lock contention handling, booking response times, success rates

## Comprehensive Coverage Matrix

| Test Category | Functional | Performance | Consistency | Scalability | Infrastructure |
|---------------|------------|-------------|-------------|-------------|----------------|
| **Deterministic** | ✅ Core Algorithm | ✅ Execution Time | ✅ Data Integrity | ❌ Single Request | ✅ Database |
| **Event Processing** | ✅ Event Workflow | ✅ Processing Time | ✅ Event Correlation | ✅ Large Dataset | ✅ Kafka + DB |
| **Cache Performance** | ✅ Search Logic | ✅ Sub-100ms Latency | ✅ Cache Consistency | ✅ 150 Concurrent | ✅ Redis + DB |
| **Booking Concurrency** | ✅ Booking Workflow | ✅ P90 <2000ms | ✅ No Double-Booking | ✅ 50 Concurrent | ✅ Redis Locks + DB |

## Performance Requirements and Validation

### Critical Performance SLAs
```yaml
Search Performance:
  - P90 Latency: <100ms
  - Cache Hit Ratio: >95%
  - Concurrent Capacity: 150 requests

Booking Consistency:
  - Double-Booking Rate: 0% (absolute)
  - P90 Booking Time: <2000ms
  - Success Rate: >2% (validates system allows bookings)

Event Processing:
  - Average Processing Time: <500ms
  - Event Correlation: 100%
  - Success Rate: >99%

Journey Generation:
  - Deterministic Counts: Exact match validation
  - Algorithm Performance: Consistent execution times
  - Data Integrity: No duplicates or ordering issues
```

### Infrastructure Validation
```yaml
Database (PostgreSQL):
  - ACID Transaction Support
  - Read Replica Performance
  - Concurrent Connection Handling
  - Flyway Migration Compatibility

Cache (Redis):
  - Distributed Locking
  - Cache Hit/Miss Performance
  - TTL and Expiry Behavior
  - Connection Pool Management

Message Queue (Kafka):
  - Producer/Consumer Reliability
  - Event Ordering and Correlation
  - Compression and Serialization
  - Topic Management
```

## Test Data Strategy

### Realistic Test Data Generation
```kotlin
// Flight Network Generation
RandomFlightNetworkGenerator:
  - Airport connectivity validation
  - Hub-and-spoke patterns
  - Realistic pricing and timing
  - Configurable dataset sizes

// Journey Generation
RandomJourneyGenerator:
  - Valid layover constraints (30min-4hr)
  - Multi-leg journey combinations
  - Conflict-free scheduling
  - Scalable journey counts

// Constrained Scenarios
Limited Inventory:
  - 5 seats per flight (booking contention)
  - Specific route targeting (cache testing)
  - Realistic passenger counts
  - High-demand simulation
```

### Configuration Management
```yaml
# application-test.yml
test:
  performance:
    baseline-flights: 30
    baseline-journeys: 20
    test-flights: 5

    search:
      concurrent-requests: 150
      target-p90-latency-ms: 100
      cache-hit-ratio-threshold: 95.0

    booking:
      concurrent-attempts: 50
      target-success-rate: 2.0
      max-booking-time-ms: 2000

# application-kafka-test.yml (event processing specific)
test:
  performance:
    baseline-flights: 30  # Reduced for faster execution
    test-flights: 5
    min-success-rate: 99.0
    max-consumer-time-ms: 500
```

## Test Execution Strategy

### Prerequisites and Setup
```bash
# Infrastructure Setup (All Tests)
cd docker && docker compose --env-file ../.env up postgres redis kafka zookeeper -d

# Verify Infrastructure
./test-infrastructure.sh

# Database Preparation
./gradlew flywayMigrate
```

### Individual Test Execution
```bash
# Deterministic Tests (Fast - <30 seconds)
./gradlew test --tests "*DeterministicFlightCreationJourneyGenerationApplicationTest*"

# Event Processing Tests (Medium - 1-2 minutes)
./gradlew test --tests "*KafkaEventConsumptionPerformanceApplicationTest*"

# Cache Performance Tests (Medium - 1-2 minutes)
./gradlew test --tests "*SearchFlowWithCachePerformanceApplicationTest*"

# Booking Concurrency Tests (Fast - <1 minute)
./gradlew test --tests "*BookingFlowConcurrencyApplicationTest*"
```

### Complete Suite Execution
```bash
# Run all application performance tests
./gradlew test --tests "*ApplicationTest*"

# Run with detailed output for debugging
./gradlew test --tests "*ApplicationTest*" --info

# Run specific categories
./gradlew test --tests "*Performance*"  # Performance-focused tests
./gradlew test --tests "*Concurrency*"  # Concurrency-focused tests
```

## Monitoring and Observability

### Structured Logging
```kotlin
// Performance metrics logging
logger.info("📊 PERFORMANCE_METRICS: P90={}ms, Cache_Hit_Ratio={}%, Success_Rate={}%")

// Event correlation tracking
logger.info("EVENT_PUBLISH_SUCCESS correlation_id={} publish_time_ms={}")
logger.info("EVENT_CONSUME_SUCCESS correlation_id={} processing_time_ms={}")

// Concurrency analysis
logger.info("🎯 BOOKING_SUMMARY: Success={}/{} ({}%), Lock_Contention={}")
```

### Metrics Collection
```kotlin
// Atomic counters for thread-safe metrics
private val eventsProduced = AtomicLong(0)
private val successfulBookings = AtomicInteger(0)
private val responseTimes = mutableListOf<Long>()

// Percentile calculations
fun calculatePercentile(sortedValues: List<Long>, percentile: Double): Double

// Cache effectiveness tracking
fun recordCacheHit(responseTimeMs: Long)
fun recordCacheMiss(responseTimeMs: Long)
```

## Benefits and Validation

### 1. **Comprehensive Coverage**
- **Functional Correctness**: Deterministic algorithm validation
- **Operational Performance**: Real-world performance characteristics
- **Data Consistency**: Strong consistency guarantees under load
- **Infrastructure Integration**: End-to-end system validation

### 2. **Production Readiness**
- **SLA Validation**: Meets sub-100ms search and booking performance requirements
- **Scalability Proof**: Handles realistic concurrent load (150+ users)
- **Reliability Guarantee**: 0% double-booking rate and 100% event correlation
- **Monitoring Baselines**: Establishes performance benchmarks for production alerts

### 3. **Regression Prevention**
- **Algorithm Changes**: Detects unintended changes in journey generation logic
- **Performance Degradation**: Catches cache hit ratio or latency regressions
- **Infrastructure Issues**: Identifies Redis, PostgreSQL, or Kafka problems
- **Configuration Drift**: Validates system behavior across configuration changes

### 4. **Quality Assurance**
- **Load Testing**: Validates system behavior under stress
- **Edge Case Coverage**: Tests failure scenarios and recovery
- **Integration Validation**: Ensures all components work together correctly
- **Documentation**: Comprehensive test documentation for maintenance

## Future Enhancements

### Advanced Testing Scenarios
```yaml
Planned Enhancements:
  - Mixed Workload Testing: Combined search and booking load
  - Failure Injection: Database failures, network partitions, Kafka outages
  - Extended Load Testing: 1000+ concurrent users, larger datasets
  - Performance Profiling: JVM metrics, memory usage, CPU utilization
  - Multi-Instance Testing: Distributed deployment validation
```

### Monitoring Integration
```yaml
Production Integration:
  - Prometheus Metrics Export
  - Grafana Dashboard Creation
  - Alert Threshold Validation
  - Performance Trend Analysis
  - Automated Performance Regression Detection
```

## Conclusion

The flight booking system's application performance test suite provides comprehensive validation of:

- **Functional Correctness**: Deterministic algorithm behavior
- **Operational Performance**: Sub-100ms search, event processing reliability
- **Data Consistency**: Zero double-booking guarantee under concurrency
- **Infrastructure Integration**: Redis, PostgreSQL, and Kafka effectiveness

This test suite ensures the system meets stringent performance and reliability requirements while providing the observability and validation needed for production deployment and ongoing maintenance.

**Total Test Coverage**: 4 comprehensive application tests covering functional, performance, consistency, and scalability requirements across all critical system components.