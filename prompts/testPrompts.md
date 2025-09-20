# Prompt for Test Generation

You are to generate **tests** and their **documentation** for the Flight Booking System.  
The system is implemented in Kotlin + Spring Boot with Postgres, Redis, and Kafka.

---

## Context

There are **two types of tests** we want:

1. **Unit Tests** – Mock out DB, Redis, and Kafka. Focus on service logic.
2. **Application Tests** – Run against locally running Docker infra (Postgres, Redis, Kafka).

Application tests should simulate real workflows.

---

## Application Tests

### 1. Flight Ingestion → Journey Creation
- Pre-existing journeys exist in Postgres.
- Simulate a new flight event arriving on Kafka.
- Expectation: consumer processes the event and generates new journeys in Postgres.
- Verify:
    - New journeys are added with valid legs and layovers.
    - No duplicates if the same event is replayed.

### 2. Search Flow with Cache
- Warm cache with an initial search (source → destination).
- Run 100–200 concurrent search requests.
- Expectation:
    - Subsequent requests are served from Redis (cache hits).
    - Cache hit ratio improves.
    - P90 latency < 100ms.
- Edge case: cache expiry → DB fetch + cache repopulation.

### 3. Booking Flow with Concurrency
- Preload DB with a journey that has N available seats.
- Fire M concurrent booking requests (M > N).
- Expectation:
    - Exactly N seats booked.
    - Extra requests rejected.
    - No double-booking or overbooking.
- Edge case: one request fails mid-way → Redis locks released, others unaffected.

---

## Unit Tests

- **JourneyGenerationService**
    - Direct journey created from flight.
    - Forward/backward/middle extensions up to 3 legs.
    - Invalid layovers rejected.
    - Duplicate signatures skipped.

- **SearchService**
    - Cache hit vs miss behavior.
    - Sorting and filtering correctness.

- **BookingService**
    - Happy path booking.
    - Seat already reserved → booking rejected.
    - Rollback if seat reservation partially succeeds.
    - Exception handling releases Redis locks.

---

## Deliverables

1. **Test Code**
    - Kotlin (JUnit5 + Spring Test).
    - Unit tests use mocks.
    - Application tests use real local Docker services (not Testcontainers).

2. **Test Documentation**
    - For each scenario, describe:
        - Pre-conditions
        - Input (requests, events, DB state)
        - Expected output/state
    - Clear separation between unit and application tests.

---

## Constraints

- Use Spring Boot profile `test` (`application-test.yml`) to connect to local Postgres, Redis, Kafka.
- Assume infra is already running (`docker-compose up` done externally).
- No need to generate Docker config.

---

## Output Format

- Well-structured Kotlin test classes.
- Accompanying Markdown documentation (`TESTING.md`) summarizing scenarios, inputs, and expected outcomes.  
