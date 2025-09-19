# Flight Booking System Design Document

## 1. Introduction

This document presents the high-level and low-level design for a flight booking system serving a single airline. The system enables users to search for flights, including multi-leg journeys, and book tickets while providing administrative capabilities for flight schedule management.

### Problem Statement
We need to design a scalable flight booking system that handles real-time flight searches with sub-100ms latency while maintaining strong consistency for booking operations. The system must support multi-leg journeys (up to 3 flights) and automatically generate journey combinations when new flights are added.

### Scope
The system covers flight search, multi-leg journey planning, seat booking with consistency guarantees, and administrative flight management. It's designed to handle 10M daily active users with peak loads of 3,000 searches per second.

## 2. Functional Requirements

### Core Features
1. **Flight Search**: Users can search flights by source, destination, departure time, and passenger count
2. **Multi-leg Journeys**: Support for connecting flights with up to 3 segments and 2 layovers
3. **Seat Booking**: Reserve and confirm seats with strong consistency to prevent double booking
4. **Administrative Management**: Admins can add new flight schedules
5. **Automatic Journey Generation**: System automatically creates valid journey combinations when flights are added
6. **Sorting Options**: Results sortable by price (cheapest) or duration (fastest)

## 3. Out of Scope

- Multi-day travel itineraries
- Multi-leg journey booking (booking individual segments separately)
- Payment processing integration
- Flight updates or cancellations
- Real-time flight status tracking
- Seat selection preferences

## 4. Non-Functional Requirements

### Performance
- **Search Latency**: P90 response time < 100ms
- **Journey Visibility**: New journeys visible within 5 seconds of flight addition
- **Throughput**: Support ~1,000 searches/second baseline, 3,000 peak
- **Conversion Rate**: ~10% of searches result in bookings

### Consistency & Availability
- **Booking Consistency**: Strong consistency for seat reservations
- **Search Availability**: High availability for search operations
- **Data Integrity**: No double booking scenarios

### Scale Parameters
- **Users**: 10M daily active users
- **Infrastructure**: 50 airports, ~1,500 active journeys, 3 flights per journey per day
- **Booking Volume**: ~300 bookings/second at peak

## 5. High-Level Design

### Architecture Overview
The system follows a microservices architecture with event-driven journey generation and aggressive caching for search performance.

```
┌─────────────┐    ┌──────────────┐    ┌─────────────────┐
│   Client    │───▶│ API Gateway  │───▶│  Search Service │
└─────────────┘    └──────────────┘    └─────────────────┘
                            │                     │
                            │                     ▼
                            │            ┌─────────────────┐
                            │            │     Redis       │
                            │            │  (Cache/Locks)  │
                            │            └─────────────────┘
                            │                     │
                            ▼                     │
                   ┌──────────────┐              │
                   │Booking Service│◀─────────────┘
                   └──────────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │   PostgreSQL    │◀─────┐
                   │   (Primary DB)  │      │
                   └─────────────────┘      │
                            ▲               │
                            │               │
                   ┌──────────────┐         │
                   │Admin Service │─────────┘
                   └──────────────┘
                            │
                            ▼
                   ┌──────────────┐
                   │    Kafka     │
                   └──────────────┘
                            │
                            ▼
                   ┌──────────────┐
                   │   Journey    │
                   │ Ingestion    │
                   │  Consumer    │
                   └──────────────┘
```

### Design Principles
1. **Precomputation Strategy**: Generate all valid journey combinations offline to enable fast search
2. **Cache-First Architecture**: Redis caching for both search results and seat reservations
3. **Event-Driven Updates**: Kafka-based async processing for journey generation
4. **Strong Booking Consistency**: Redis locks + PostgreSQL transactions for seat reservations

## 6. Low-Level Data Model

### Database Schema

#### Flights Table
```sql
CREATE TABLE flights (
    flight_id UUID PRIMARY KEY,
    flight_number VARCHAR(10) NOT NULL,
    source_airport CHAR(3) NOT NULL,
    destination_airport CHAR(3) NOT NULL,
    departure_time TIMESTAMP NOT NULL,
    arrival_time TIMESTAMP NOT NULL,
    airplane_id UUID NOT NULL,
    total_capacity INTEGER NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (airplane_id) REFERENCES airplanes(airplane_id),
    INDEX idx_source_dest_time (source_airport, destination_airport, departure_time),
    INDEX idx_departure_time (departure_time)
);
```

#### Journeys Table
```sql
CREATE TABLE journeys (
    journey_id UUID PRIMARY KEY,
    flight_details JSONB NOT NULL,
    source_airport CHAR(3) NOT NULL,
    destination_airport CHAR(3) NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    total_duration_minutes INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_source_dest (source_airport, destination_airport),
    INDEX idx_price (total_price),
    INDEX idx_duration (total_duration_minutes),
    UNIQUE INDEX idx_flight_combination (flight_details) -- Prevent duplicate journeys
);
```

#### Bookings Table
```sql
CREATE TABLE bookings (
    booking_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    journey_id UUID NOT NULL,
    number_of_seats INTEGER NOT NULL,
    booking_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED', -- reserved/confirmed/cancelled
    payment_id VARCHAR(50),

    FOREIGN KEY (journey_id) REFERENCES journeys(journey_id),
    INDEX idx_user_bookings (user_id),
    INDEX idx_journey_bookings (journey_id)
);
```

#### Seats Table
```sql
CREATE TABLE seats (
    seat_id UUID PRIMARY KEY,
    flight_id UUID NOT NULL,
    seat_number VARCHAR(5) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    booking_id UUID NULL,
    reserved_until TIMESTAMP NULL,

    FOREIGN KEY (flight_id) REFERENCES flights(flight_id),
    FOREIGN KEY (booking_id) REFERENCES bookings(booking_id),
    UNIQUE INDEX idx_flight_seat (flight_id, seat_number),
    INDEX idx_flight_available (flight_id, status)
);
```

#### Airplanes Table
```sql
CREATE TABLE airplanes (
    airplane_id UUID PRIMARY KEY,
    tail_number VARCHAR(20) UNIQUE NOT NULL,
    model VARCHAR(50) NOT NULL,
    capacity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Entity Relationships
- One Journey contains 1-3 Flights (stored as flightDetails with order and IDs)
- One Booking references exactly one Journey and includes paymentId
- Each Flight has a price and is associated with an Airplane
- Each Flight has multiple Seats
- Each Seat can be linked to at most one Booking
- Each Airplane has a unique tailNum and ID

## 7. Detailed Component Design

### API Gateway
**Responsibilities**: Request routing, authentication, rate limiting, request/response transformation
**Technology**: Spring Cloud Gateway
**Key Features**:
- Route `/search/*` to Search Service
- Route `/bookings/*` to Booking Service
- Route `/admin/*` to Admin Service
- JWT token validation
- Rate limiting: 100 requests/minute per user

### Admin Service
**Responsibilities**: Flight management operations for airline administrators
**Technology**: Spring Boot with Spring Data JPA
**Endpoints**:
```
POST /admin/flights
- Validates flight data
- Saves to PostgreSQL
- Publishes FlightAdded event to Kafka
```
**Key Logic**:
- Validation of airport codes, times, capacity
- Duplicate flight prevention
- Event publishing for downstream processing

### Search Service
**Responsibilities**: High-performance flight and journey search
**Technology**: Spring Boot with Redis and PostgreSQL read replicas
**Endpoints**:
```
GET /search/journeys?source={src}&destination={dest}&date={date}&passengers={num}&sort={price|duration}
```
**Search Algorithm**:
1. Generate cache key: `journeys:{source}:{dest}:{date}`
2. Check Redis cache (TTL: 10 minutes)
3. If cache miss, query PostgreSQL read replica
4. Apply passenger count filter (available seats >= requested)
5. Sort by specified criteria
6. Cache results and return

**Caching Strategy**:
- Journey data cached by route and date
- Cache invalidation on journey updates
- Separate cache for seat availability (shorter TTL: 30 seconds)

### Journey Ingestion Consumer
**Responsibilities**: Async journey generation when flights are added
**Technology**: Spring Kafka Consumer
**Processing Flow**:
1. Consume FlightAdded events from Kafka
2. Execute journey generation algorithm
3. Save new journeys to PostgreSQL
4. Update Redis cache
5. Publish JourneyCreated events

### Booking Service
**Responsibilities**: Seat reservation and booking confirmation
**Technology**: Spring Boot with Redis and PostgreSQL
**Booking Flow**:
```
POST /bookings
1. Validate journey and passenger count
2. Acquire Redis locks for required seats
3. Check seat availability in PostgreSQL
4. Create booking record
5. Update seat status
6. Release Redis locks
7. Return booking confirmation
```

**Consistency Mechanism**:
- Redis distributed locks with TTL (60 seconds)
- PostgreSQL transactions for atomicity
- Compensation logic for failed bookings

### Data Storage

#### PostgreSQL Configuration
- **Primary Instance**: All write operations
- **Read Replicas**: Search service queries
- **Connection Pooling**: HikariCP with 50 connections per service
- **Partitioning**: Flights table partitioned by date

#### Redis Configuration
- **Cluster Mode**: 3 master nodes with replication
- **Use Cases**:
  - Journey cache: TTL 10 minutes
  - Seat locks: TTL 60 seconds
  - Rate limiting counters: TTL 1 hour
- **Memory**: 16GB per node

#### Kafka Configuration
- **Topics**:
  - `flight-events`: FlightAdded, FlightUpdated
  - `journey-events`: JourneyCreated, JourneyUpdated
- **Partitions**: 6 partitions per topic
- **Replication Factor**: 3

## 8. Detailed Design Flows

### Flight Search Flow
```
1. User submits search request
   ├─ API Gateway validates and routes to Search Service

2. Search Service processes request
   ├─ Generate cache key from search parameters
   ├─ Check Redis for cached results
   │  ├─ Cache HIT: Return cached journeys
   │  └─ Cache MISS: Query PostgreSQL
   │      ├─ Execute journey query with filters
   │      ├─ Join with flights table for details
   │      ├─ Check seat availability
   │      ├─ Apply sorting (price/duration)
   │      ├─ Cache results in Redis
   │      └─ Return to user
```

### Booking Flow
```
1. User initiates booking
   ├─ API Gateway routes to Booking Service

2. Booking Service validates request
   ├─ Verify journey exists and is active
   ├─ Check passenger count <= available seats

3. Seat Reservation Process
   ├─ Generate seat lock keys for each required seat
   ├─ Acquire Redis distributed locks (60s TTL)
   ├─ Query PostgreSQL for current seat status
   ├─ Verify seats are still available

4. Booking Confirmation
   ├─ Start PostgreSQL transaction
   ├─ Create booking record
   ├─ Update seat status to 'BOOKED'
   ├─ Link seats to booking_id
   ├─ Commit transaction
   ├─ Release Redis locks
   └─ Return booking confirmation
```

### Journey Generation Flow
```
1. Admin adds new flight
   ├─ Admin Service validates and saves flight
   ├─ Publishes FlightAdded event to Kafka

2. Journey Ingestion Consumer processes event
   ├─ Receives FlightAdded event
   ├─ Executes incremental journey generation
   │  ├─ Create direct journey (single flight)
   │  ├─ Find forward connections
   │  │  ├─ Query flights where source = new_flight.destination
   │  │  ├─ Filter by valid layover time (30min - 4hours)
   │  │  └─ Create 2-leg journeys
   │  ├─ Find backward connections
   │  │  ├─ Query flights where destination = new_flight.source
   │  │  ├─ Filter by valid layover time
   │  │  └─ Create 2-leg journeys
   │  └─ Extend to 3-leg journeys
   │     ├─ For each 2-leg journey, find valid connections
   │     └─ Create 3-leg combinations
   ├─ Save new journeys to PostgreSQL
   ├─ Update Redis cache
   └─ Journey becomes searchable within 5 seconds
```

## 9. Core Algorithms

### Incremental Journey Generation Algorithm

**Input**: New flight F
**Output**: All valid journey combinations involving F

```
Algorithm: GenerateJourneys(flight F)
1. Initialize journey_signatures = Set<String>()
2. Create direct journey: [F]
   - Add to journey_signatures: F.flight_id

3. Forward Extension:
   For each existing flight G where:
   - G.source == F.destination
   - G.departure_time >= F.arrival_time + MIN_LAYOVER
   - G.departure_time <= F.arrival_time + MAX_LAYOVER
   - G.flight_id != F.flight_id (prevent cycles)
   Do:
     - Create journey [F, G]
     - signature = F.flight_id + "," + G.flight_id
     - If signature not in journey_signatures:
       - Add signature to journey_signatures
       - For each existing flight H where:
         - H.source == G.destination
         - H.departure_time >= G.arrival_time + MIN_LAYOVER
         - H.departure_time <= G.arrival_time + MAX_LAYOVER
         - H.flight_id != F.flight_id AND H.flight_id != G.flight_id
         Do:
           - Create journey [F, G, H]
           - signature = F.flight_id + "," + G.flight_id + "," + H.flight_id
           - Add to journey_signatures if not exists

4. Backward Extension:
   For each existing flight E where:
   - E.destination == F.source
   - F.departure_time >= E.arrival_time + MIN_LAYOVER
   - F.departure_time <= E.arrival_time + MAX_LAYOVER
   - E.flight_id != F.flight_id (prevent cycles)
   Do:
     - Create journey [E, F]
     - signature = E.flight_id + "," + F.flight_id
     - If signature not in journey_signatures:
       - Add signature to journey_signatures
       - For each existing flight D where:
         - D.destination == E.source
         - E.departure_time >= D.arrival_time + MIN_LAYOVER
         - E.departure_time <= D.arrival_time + MAX_LAYOVER
         - D.flight_id != E.flight_id AND D.flight_id != F.flight_id
         Do:
           - Create journey [D, E, F]
           - signature = D.flight_id + "," + E.flight_id + "," + F.flight_id
           - Add to journey_signatures if not exists

5. Middle Insertion (explicit bridging):
   For each existing 1-leg journey [X] where:
   - X.destination == F.source
   - F.departure_time >= X.arrival_time + MIN_LAYOVER
   - F.departure_time <= X.arrival_time + MAX_LAYOVER
   For each existing 1-leg journey [Y] where:
   - Y.source == F.destination
   - Y.departure_time >= F.arrival_time + MIN_LAYOVER
   - Y.departure_time <= F.arrival_time + MAX_LAYOVER
   Do:
     - Create journey [X, F, Y]
     - signature = X.flight_id + "," + F.flight_id + "," + Y.flight_id
     - Add to journey_signatures if not exists and no cycles

6. Validation & Storage:
   For each unique journey:
   - Calculate total_price = sum(flight.price)
   - Calculate total_duration = last.arrival_time - first.departure_time
   - Create flight_details JSONB: {"flights": [{"id": uuid, "order": 1}, ...]}
   - Save valid journeys to PostgreSQL
   - Update Redis cache
```

**Complexity Analysis**:
- Time: O(F²) where F is number of flights (bounded by domain size)
- Space: O(J) where J is number of generated journeys
- Bounded by domain: 50 airports × 3 flights/day = manageable scale

### Seat Availability Check Algorithm

**Purpose**: Ensure atomic seat reservation across multiple flights in a journey

```
Algorithm: ReserveSeats(journey_id, passenger_count)
1. Get flight_ids from journey
2. For each flight_id:
   a. Generate lock_keys for required seats
   b. Acquire Redis locks with 60s TTL
   c. If any lock fails, release all and return error

3. Query PostgreSQL for seat availability:
   SELECT COUNT(*) as available_seats
   FROM seats
   WHERE flight_id IN (flight_ids)
   AND status = 'AVAILABLE'
   GROUP BY flight_id

4. Validate sufficient seats available
5. Begin PostgreSQL transaction:
   a. Update seat status to 'RESERVED'
   b. Create booking record
   c. Link seats to booking
   d. Commit transaction

6. Release all Redis locks
7. Return booking confirmation
```

### Search Query Optimization

**Purpose**: Sub-100ms search performance with sorting

```
Algorithm: SearchJourneys(source, destination, date, passengers, sort_by)
1. Cache Key Generation:
   key = "journeys:" + source + ":" + destination + ":" + date

2. Cache Lookup:
   results = Redis.get(key)
   if (results != null) return applyFiltersAndSort(results)

3. Database Query:
   SELECT j.*, array_agg(f.*) as flight_details
   FROM journeys j
   JOIN flights f ON f.flight_id = ANY(j.flight_ids)
   WHERE j.source_airport = source
   AND j.destination_airport = destination
   AND f.departure_time::date = date
   GROUP BY j.journey_id

4. Seat Availability Filter:
   For each journey:
     available_seats = min(seats available across all flights in journey)
     if (available_seats >= passengers) include in results

5. Sorting:
   if (sort_by == "price") ORDER BY total_price ASC
   if (sort_by == "duration") ORDER BY total_duration_minutes ASC

6. Cache results with TTL=600s
7. Return filtered and sorted results
```

## 10. Technology Stack and Deployment

### Application Stack
- **Backend**: Kotlin + Spring Boot 3.x
- **Build Tool**: Gradle 8.x
- **Database**: PostgreSQL 15
- **Cache/Locks**: Redis 7.x
- **Message Queue**: Apache Kafka 3.x
- **Monitoring**: Micrometer + Prometheus

### Deployment Architecture
- **Load Balancer**: Nginx (for local testing)
- **Service Instances**: 2 replicas per service
- **Database**: PostgreSQL with read replicas
- **Monitoring**: Custom dashboards for search latency, booking success rate
- **Logging**: Structured JSON logs with correlation IDs

This design provides a robust foundation for a high-performance flight booking system that meets all specified functional and non-functional requirements while maintaining scalability and consistency guarantees.
