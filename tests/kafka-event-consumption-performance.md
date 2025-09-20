# Kafka Event Consumption & Durable Processing Performance Test

## Overview

This document describes the **Kafka event consumption and durable processing performance test** for the flight creation → journey generation workflow. Unlike the deterministic test, this focuses on operational reliability, Kafka integration performance, and system throughput under realistic conditions with actual Kafka message processing.

## Key Design Principles

### 1. **Kafka Integration Testing**
- **Real Kafka processing**: Producer → Consumer via actual Kafka topics (`flight-events-load`)
- **Performance metrics**: Detailed timing and success/failure tracking
- **Event correlation**: Producer events matched with consumer successes
- **Durable processing**: Validates journey generation completes successfully

### 2. **Large-Scale Dataset Simulation**
- **Baseline dataset**: 68 flights + 20 journeys (scalable configuration)
- **Test events**: 5 new flights with Kafka events (configurable)
- **Realistic data**: Valid airport networks with proper connectivity
- **Performance validation**: Configurable thresholds for success rates and timing

### 3. **Comprehensive Metrics Collection**
- **Producer metrics**: Events produced, success rate, average publish time
- **Consumer metrics**: Events consumed, processing time, success rate
- **Event correlation**: Perfect correlation tracking (events produced = events consumed)
- **Performance thresholds**: 99% success rate, <500ms average processing time

## Test Architecture

### Event Processing Flow
```
AdminFlightService.createFlight()
         ↓
FlightEventProducer.publishFlightCreatedEvent() [ENABLED]
         ↓
Kafka Topic: "flight-events-load"
         ↓
FlightEventConsumer.handleFlightCreatedEvent() [AUTO-CONSUMED]
         ↓
JourneyGenerationService.generateJourneysForNewFlight()
         ↓
Performance validation via EventMetricsService
```

### Infrastructure Requirements
- **Database**: PostgreSQL `flight_booking` (with cleanup)
- **Kafka**: Enabled with topic `flight-events-load`
- **Redis**: Available for caching
- **Configuration**: `application-kafka-test.yml` with producer enabled

## Test Data Design

### Large-Scale Flight Network (68 flights generated)
```
Airport Network: DEL, BOM, BLR, MAA, CCU, HYD, AMD, COK, GOI, PNQ, etc.

Backbone connectivity with hub-and-spoke patterns:
- Major hubs: DEL, BOM, BLR, MAA, CCU (top 8 airports)
- Hub-to-hub connections (bidirectional)
- Smaller airports connected to hubs
- 68 unique routes with realistic pricing and timing
```

### Baseline Journeys (20 journeys)
- Generated from backbone flight network
- Mix of 1-3 flight combinations
- Valid layover constraints (30min-4hr between flights)
- Pre-validated to avoid constraint violations

### Test Flight Events (5 flights)
- Routes: LKO→CCU, GAU→MAA, GAU→IXB, GOI→LKO, CCU→MAA
- Created with 25ms delay between events
- Each triggers Kafka event processing
- Designed to create journey opportunities in existing network

## Test Scenarios

### Scenario 1: High-Volume Kafka Event Processing with Performance Validation
**Objective**: Validate Kafka integration, event correlation, and performance under load

**Test Flow**:
1. **Setup**: Generate 68 flights + 20 journeys baseline dataset
2. **Execution**: Create 5 flights rapidly with Kafka events (25ms delay)
3. **Monitoring**: Track producer/consumer metrics in real-time
4. **Validation**: Verify event correlation, success rates, and timing thresholds

**Expected Outcomes**:
- **Event Correlation**: 100% (5 events produced = 5 events consumed)
- **Success Rate**: 100% for both producer and consumer
- **Performance**: Average processing time within thresholds
- **Journey Generation**: Multiple new journeys created from each flight

**Success Criteria**:
```kotlin
assertTrue(validation.allThresholdsPassed, "Performance validation failed")
assertTrue(finalMetrics.producer.eventsProduced >= createdFlightIds.size, "All events produced")
assertTrue(finalMetrics.consumer.eventsProcessedSuccessfully >= createdFlightIds.size * 0.99, "99% success rate")
assertTrue(journeyDelta > 0, "New journeys generated")
```

## Test Results Summary

### Performance Metrics Achieved
- **Total Test Duration**: 9,073ms (~9 seconds)
- **Dataset Setup Time**: 703ms (68 flights + 20 journeys)
- **Event Creation Time**: 6,923ms (5 flights, avg 1,384ms per flight)
- **Journey Generation**: 16 new journeys created

### Producer Performance
- **Events Produced**: 5/5 (100% success rate)
- **Average Publish Time**: 31.4ms per event
- **Publish Time Range**: 2-139ms (first event slower due to Kafka setup)
- **Topic**: `flight-events-load`

### Consumer Performance
- **Events Consumed**: 5/5 (100% success rate)
- **Average Processing Time**: 26.8ms per event
- **Processing Time Range**: 8-70ms per event
- **Total Journeys Generated**: 16 journeys from 5 flights

### Event Correlation
- **Perfect Correlation**: 100% (5 produced = 5 consumed)
- **Event Lag**: 0 (no lost or delayed events)
- **Real-time Processing**: All events processed as received

### Journey Generation Details
```
Flight 1 (LKO→CCU): Generated 2 journeys
Flight 2 (GAU→MAA): Generated 1 journey
Flight 3 (GAU→IXB): Generated 1 journey
Flight 4 (GOI→LKO): Generated 1 journey
Flight 5 (CCU→MAA): Generated 11 journeys (most connections)
Total: 16 new journeys created
```

## Implementation Details

### Test Class Structure
```kotlin
@SpringBootTest
@ActiveProfiles("kafka-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KafkaEventConsumptionPerformanceApplicationTest {

    @Autowired private lateinit var adminFlightService: AdminFlightService
    @Autowired private lateinit var eventMetricsService: EventMetricsService
    @Autowired private lateinit var journeyDao: JourneyDao
    @Autowired private lateinit var flightDao: FlightDao

    // Configurable test parameters
    @Value("\${test.performance.baseline-flights:30}")
    private var baselineFlightCount: Int = 30
    @Value("\${test.performance.test-flights:5}")
    private var testFlightCount: Int = 5
}
```

### Enhanced Logging and Correlation
```kotlin
// Producer logs
EVENT_PUBLISH_START correlation_id=event_92a95828-d718-4e13-8607-5ee1bbc918b6_1758344583582
EVENT_PUBLISH_SUCCESS publish_time_ms=139 total_produced=1

// Consumer logs
EVENT_CONSUME_START correlation_id=event_92a95828-d718-4e13-8607-5ee1bbc918b6_1758344584053
EVENT_CONSUME_SUCCESS processing_time_ms=36 total_successful=1

// Metrics summary
METRICS_SUMMARY producer_success_rate=100.00% consumer_success_rate=100.00% correlation_match=100.00%
```

### Configuration (application-kafka-test.yml)
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9093
    consumer:
      group-id: flight-booking-kafka-test-consumer
      auto-offset-reset: earliest
    producer:
      acks: all
      retries: 3
      compression-type: snappy

flight:
  events:
    producer:
      enabled: true  # ENABLED for Kafka performance testing

test:
  performance:
    baseline-flights: 30
    baseline-journeys: 20
    test-flights: 5
    min-success-rate: 99.0
    max-consumer-time-ms: 500
```

## Performance Validation

### Configurable Thresholds
```kotlin
val thresholds = PerformanceThresholds(
    minProducerSuccessRate = 99.0,    // 99% minimum success rate
    maxProducerTimeMs = 1000.0,       // Max 1 second publish time
    minConsumerSuccessRate = 99.0,    // 99% minimum success rate
    maxConsumerTimeMs = 500.0,        // Max 500ms processing time
    minCorrelationRate = 99.0,        // 99% event correlation
    maxEventLag = 5                   // Max 5 unprocessed events
)
```

### Validation Results
```
✅ PERFORMANCE_VALIDATION_PASSED: All performance thresholds met
✅ Producer Success Rate: 100.00% (≥99% required)
✅ Consumer Success Rate: 100.00% (≥99% required)
✅ Average Processing Time: 26.8ms (≤500ms required)
✅ Event Correlation: 100.00% (≥99% required)
✅ Event Lag: 0 (≤5 required)
```

## Key Features Validated

### 1. **Kafka Integration Reliability**
- Real Kafka producer/consumer messaging
- Topic: `flight-events-load`
- Compression: Snappy
- Durability: `acks=all` with retries

### 2. **Event Correlation Tracking**
- Correlation IDs for producer/consumer matching
- Perfect event tracking (no lost events)
- Real-time progress monitoring

### 3. **Performance Metrics Collection**
- Atomic counters for thread-safe metrics
- Detailed timing measurements
- Success/failure rate tracking

### 4. **Journey Generation Validation**
- BFS algorithm performance under load
- Multiple journey combinations per flight
- Database transaction integrity

### 5. **System Scalability**
- Configurable dataset sizes
- Realistic flight network generation
- Efficient cleanup procedures

## Benefits of This Approach

### 1. **Operational Validation**
- **Real Kafka Integration**: Validates actual message queue processing
- **Performance Baselines**: Establishes timing benchmarks for production
- **Error Handling**: Tests failure scenarios and recovery

### 2. **Scalability Testing**
- **Configurable Load**: Easy to scale up dataset sizes
- **Resource Monitoring**: Tracks database and Kafka performance
- **Bottleneck Identification**: Pinpoints performance constraints

### 3. **Production Readiness**
- **End-to-End Validation**: Complete workflow from API to database
- **Monitoring Integration**: Structured logging for production monitoring
- **Performance SLAs**: Validates system meets timing requirements

### 4. **Complementary Coverage**
- **Deterministic Test**: Validates correctness and algorithm behavior
- **Performance Test**: Validates operational characteristics and reliability
- **Combined Coverage**: Both functional correctness and performance validation

## Running the Tests

### Prerequisites
```bash
# Start complete infrastructure (PostgreSQL + Kafka required)
cd docker && docker compose --env-file ../.env up postgres kafka zookeeper -d

# Verify Kafka is accessible
docker compose ps kafka
```

### Execute Tests
```bash
# Run Kafka performance test
./gradlew test --tests "*KafkaEventConsumptionPerformanceApplicationTest*"

# Run with increased dataset (modify application-kafka-test.yml)
# baseline-flights: 100, test-flights: 20

# Monitor results in real-time
./gradlew test --tests "*KafkaEventConsumptionPerformanceApplicationTest*" --info
```

### Expected Output
```
🔧 SETUP_START: Setting up large dataset for Kafka performance test
✅ FLIGHT_GENERATION_COMPLETE: Generated 68 flights
✅ JOURNEY_GENERATION_COMPLETE: Generated 20 journeys
🎯 SETUP_COMPLETE: 68 flights, 20 journeys in 703ms

🚀 TEST_START: High-volume Kafka event processing performance test
📊 BASELINE: 68 flights, 20 journeys before test execution
🎯 EVENT_PROCESSING_START: Creating 5 flights with Kafka events
⚡ EVENT_CREATION_COMPLETE: Created 5 flights in 6923ms (avg 1384ms per flight)

📊 CONSUMPTION_PROGRESS: Produced=5, Consumed=5, Success=5, Avg=26.8ms
✅ ALL_EVENTS_PROCESSED: All 5 events processed successfully

📊 METRICS_SUMMARY producer_success_rate=100.00% consumer_success_rate=100.00%
🎯 TEST_RESULTS: Total time: 9073ms, Journeys created: 16
✅ PERFORMANCE_VALIDATION_PASSED: All performance thresholds met
🎉 TEST_COMPLETE: Kafka event consumption performance test passed successfully!
```

## Future Enhancements

### Test Coverage Extensions
- **Higher Load Testing**: 100+ flights, 1000+ events
- **Concurrent Processing**: Multiple parallel event streams
- **Error Injection**: Kafka failures, database timeouts
- **Performance Profiling**: Detailed resource utilization

### Monitoring Integration
- **Metrics Export**: Prometheus/Grafana integration
- **Alert Testing**: Performance threshold breach scenarios
- **Dashboard Validation**: Real-time monitoring verification

This Kafka performance test provides comprehensive validation of the system's operational characteristics, ensuring production readiness and establishing performance baselines for monitoring and alerting.