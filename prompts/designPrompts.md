The final document must have,
1. Introduction – summarize the problem and scope.
2. Functional Requirements
3. Out of Scope – explicitly state what is excluded.
4. Non Functional Requirements
5. HLD Diagram and Components description(Brief)
6. LLD Data Model – proposed schema models
7. Detailed Design – how components interact
8. Precompute flow description with pseudo algo
9. Tech Stack and Deployment Details – Kotlin + Spring Boot, Gradle, local Docker setup with Postgres/Redis/Kafka.


## Problem statement

Design a flight booking system for a single airline. Users should be able to search flights (including multi-legged up to 3), book tickets, and admins can add new flight schedules. The system needs to handle strong booking consistency and very low latency search.


## Functional requirements

1. Search flights based on source, destination, departure time, and passengers. Sort by cheapest or fastest.
2. Multi-leg journeys (up to 3 flights, 2 layovers).
3. Booking seats in a flight/journey, prevent double booking.
4. Admins can add flights.
5. On adding a flight, journeys get created automatically within valid layover constraints.


## Non functional requirements

* P90 search < 100ms
* Strong consistency for booking
* High availability for search
* New journeys visible < 5s
* Scale: 10M DAU, \~1k searches/sec, peak 3k, \~10% searches turn into bookings
* Domain size: 50 airports, \~1500 journeys, 3 flights/journey/day


## Out of scope

* Multi-day travel
* Multi-leg journey booking
* Payment processing
* Flight updates/removals


## High level design choices

* Precomputation of journeys into Postgres (graph DB not allowed). Max is 3 flights per journey, BFS when new flight added.
* Cache: For search at search service and for seat reservations, immutable journey/flight cache with TTL in booking service.
* Seat reservation stored at seat level here (simple version). Use Redis TTL lock then commit to DB.
* Read replicas for search, strong consistency for booking.


## Entities

* Flight - flightId, flightNumber, source, destination, departureTime, arrivalTime, airplaneId, capacity, status, price
* Journey - journeyId, flightDetails (order of flights and ids), totalPrice, totalDuration, source, destination, status
* Booking - bookingId, userId, journeyId, numberOfSeats, bookingTime, status (reserved/confirmed/cancelled), paymentId
* Seat - flightId, seatId, status
* Airplane - tailNum, id
Additionally, Admin, and User are Actors

## Components

* API Gateway - routes, auth, rate limits.
* Admin Service - lets admins add flights, emits flight-added events.
* Flights Event Kafka - queue for the events.
* Journey Ingestion Consumer - listens to events, builds new journeys using BFS, saves them in Postgres, updates cache.
* Search Service - answers user queries by checking cache or DB, sorts results, seats fetched separately not cached.
* Booking Service - seat reservation + booking flow, locks seats in Redis before confirming.
* Redis (2 roles) - seat reservation cache, journey/flight cache.
* Postgres - DB: flights, journeys, bookings, seats. Is used by Admin Service, Ingestion Consumer, Search service, booking service


## Data flow example

1. Search -> User -> Gateway -> Search service -> (cache first, fallback DB) -> sorted journeys back to user.
2. Booking -> User picks journey -> Booking service -> reserve seats in Redis (TTL) -> confirm in DB -> mark seats booked.
3. Admin adds flight -> Admin service -> DB + Kafka -> Journey consumer -> BFS to build new journeys -> store Postgres + cache -> ready in <5s.

## Data model

* Flight table (id, number, src, dest, departure, arrival, airplaneId, capacity).
* Journey table (id, flightIds\[], totalPrice, totalDuration).
* Booking table (id, userId, journeyId, seatsBooked, bookingTime, status).
* Seat table (seatId, flightId, seatNumber, status).


## Core algorithm – incremental journey creation

When a new flight comes in:

1. Create a direct journey with just this flight.
2. Extend forward: for every flight whose source = new flight’s destination and departs within layover window - create a new journey \[new, next]. If journey length <3, extend further.
3. Extend backward: for every flight whose destination = new flight’s source and arrival within layover window - create new journey \[prev, new]. Again extend until max 3.
4. Idempotent journeys (by ordered flight IDs).
5. Save to Postgres, update cache.

Bounded problem size makes this cheap (3 flights max, 50 airports).


## Scalability

* Precomputation avoids graph queries at runtime - O(log N) lookups.
* Redis locks prevent double booking, Postgres is source of truth.
* Archival of old flights/journeys keeps DB lean.
* Search service scaled horizontally with replicas.

This prompt should result in a full design doc that’s structured, explains reasoning, and includes both the big picture and enough detail to code a working demo.