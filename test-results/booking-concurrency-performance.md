# Booking Flow Concurrency Performance Test

## Overview

This document describes the **Booking flow concurrency performance test** for the flight booking system. The test simulates multiple concurrent booking attempts on a journey with very limited seat inventory. It validates Redis-based distributed locking and PostgreSQL ACID transactions by ensuring that only one booking succeeds, while all others fail gracefully without double-booking.

## Key Design Principles

### 1. **Distributed Locking for Consistency**

* Redis sorted set–based locks prevent simultaneous seat allocations.
* PostgreSQL transactions maintain strong consistency.
* Failures roll back reservations cleanly, avoiding partial bookings.

### 2. **High Concurrency Stress Testing**

* 10 parallel booking attempts against **1 seat only**.
* Ensures system rejects excess attempts correctly.
* Verifies lock contention and proper rollback behavior.

### 3. **Atomic Reservation**

* Even with failures, one booking is completed atomically.
* Other attempts fail with `IllegalStateException` indicating unavailable seat.

## Test Architecture

### Execution Flow

```
Concurrent Requests → BookingService
            ↓
   Redis Lock Acquisition
            ↓
   Seat Availability Check
            ↓
   PostgreSQL Transaction
            ↓
   Seat Reserved or Failure Raised
```

### Test Infrastructure

* **Database**: PostgreSQL 15.14 (Debian, running on localhost:5433)
* **Cache/Locking**: Redis (sorted set based seat reservations)
* **Spring Boot**: v3.5.1 with test profile
* **Concurrency**: 10 booking attempts launched via thread pool

## Test Data Design

* **Flights Generated**: 5 random flights.
* **Journeys Generated**: 1 random journey (direct flight).
* **Seat Inventory**: 1 seat created for the journey’s flight.

This guarantees contention: only 1 attempt can ever succeed.

## Test Scenarios

### Scenario 1: 10 Concurrent Bookings for 1 Seat

**Objective**: Ensure only one booking succeeds and no double-booking occurs.

**Execution**:

* 10 simultaneous booking attempts on the same journey.
* Each attempt tries to reserve the single available seat.

**Expected Outcomes**:

* 1 booking succeeds.
* 9 bookings fail with `Unable to reserve seats...` errors.
* No double-booking in database.

**Actual Outcomes**:

* **Successes**: 1 booking created (`e584de27-c1db-432c-b368-9d513b683aff`).
* **Failures**: 9 attempts failed with `IllegalStateException`.
* **Logs show rollback**: failed reservations released properly.
* **Final DB state**: 1 booking confirmed, 1 seat marked as BOOKED.

---

## Implementation Details

### Test Setup

* Clean DB before run (deleted bookings, seats, journeys, flights).
* `RandomFlightNetworkGenerator` used to create 5 flights.
* `RandomJourneyGenerator` used to generate 1 journey.
* Single seat inserted into DB for the journey flight.

### Concurrency Execution

* 10 `CompletableFuture`s launched in thread pool.
* All attempts logged by BookingService.
* Failures rolled back and logged.

---

## Results

### Booking Summary

```
Attempts:       10
Successful:     1
Failed:         9
Failure Types:  All seat-unavailable due to contention
```

### Logs Evidence

* Multiple threads attempted to reserve seat on flight `914c858c...`
* Redis locks showed contention: “some seats already reserved” warnings.
* Only attempt #2 succeeded, all others rolled back.

### Performance

* Spring Boot test initialized in \~2.2s.
* Booking attempts completed in <500ms total.
* Rollbacks were fast, no deadlocks observed.

### Consistency Validation

* ✅ No double-booking: 1 seat reserved exactly once.
* ✅ Rollbacks successful, no orphaned reservations.
* ✅ Redis lock contention handled cleanly.

---

## Actual Test Execution Summary

* **Test Class**: `SimpleBookingConcurrencyTest`
* **Method**: `only_one_booking_should_succeed_under_concurrency`
* **Status**: ✅ PASSED
* **Outcome**: Exactly 1 booking succeeded, proving lock + transaction correctness.

---

## Benefits of This Test

* Validates **core business invariant**: no double-booking.
* Confirms Redis locks + Postgres transactions cooperate under concurrency.
* Provides baseline for scaling to higher concurrency and multi-flight journeys.
* Gives confidence in production readiness for real-world contention scenarios.

---