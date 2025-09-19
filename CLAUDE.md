# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a flight booking system designed for a single airline, built with Kotlin + Spring Boot microservices architecture. The system handles flight search with sub-100ms latency requirements and strong consistency for booking operations.

## Technology Stack

- **Backend**: Kotlin + Spring Boot 3.x
- **Build**: Gradle 8.x
- **Database**: PostgreSQL 15 (primary + read replicas)
- **Cache/Locks**: Redis 7.x
- **Message Queue**: Apache Kafka 3.x
- **Local Development**: Docker Compose setup

## Architecture Overview

The system uses a microservices pattern with event-driven journey generation:

- **API Gateway**: Routes requests, handles auth and rate limiting
- **Search Service**: High-performance flight/journey search with Redis caching
- **Booking Service**: Seat reservation with Redis locks + PostgreSQL transactions
- **Admin Service**: Flight management, writes to DB then publishes to Kafka
- **Journey Ingestion Consumer**: Processes flight events, generates journey combinations using BFS algorithm

Key architectural decisions:
- **Precomputed Journeys**: All valid multi-leg combinations generated offline for fast search
- **Cache-First Search**: Redis cache with PostgreSQL read replica fallback
- **Strong Booking Consistency**: Redis distributed locks + PostgreSQL ACID transactions

## Coding Rules and Standards

IMPORTANT: Always follow the project-specific rules defined in the `claude-rules/` directory:

- **PostgreSQL/Database**: Follow `claude-rules/postgresSetupRules.md` exactly - use Jooq with coroutines, Flyway migrations, specific versions
- **OpenAPI**: Follow `claude-rules/openApiRules.md` exactly - health endpoint only, specific generator config, no additional APIs
- **Architecture**: Follow `claude-rules/applicationLayerRules.md` for layer separation
- **Docker**: Follow `claude-rules/dockerRules.md` for containerization
- **Spring Boot**: Follow `claude-rules/springBootRules.md` for framework usage
- **Kotlin**: Follow `claude-rules/kotlinWorkspaceCodingRules.md` for language conventions

These rules OVERRIDE any default behavior. Read and apply them before making any code changes.

## Development Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Start specific services (when implemented)
./gradlew :search-service:bootRun
./gradlew :booking-service:bootRun
./gradlew :admin-service:bootRun
./gradlew :journey-consumer:bootRun

# Start Docker infrastructure
cd docker && docker compose --env-file ../.env up -d

# Stop Docker infrastructure
cd docker && docker compose --env-file ../.env down

# View Docker logs
cd docker && docker compose --env-file ../.env logs -f

# Test infrastructure connectivity
cd docker && ./test-infrastructure.sh

# Restart specific service
cd docker && docker compose --env-file ../.env restart [postgres|redis|kafka|zookeeper]

# Check service status
cd docker && docker compose --env-file ../.env ps

# Database migrations (when implemented)
./gradlew flywayMigrate
```

## Key Data Models

- **Flights**: Basic flight info with price, linked to airplanes
- **Journeys**: 1-3 flight combinations stored as JSONB flight_details with order preservation
- **Bookings**: References journeys, includes payment_id
- **Seats**: Per-flight seat inventory with booking links
- **Airplanes**: Aircraft details with tail numbers

## Critical Implementation Notes

### Journey Generation Algorithm
The system uses an incremental BFS algorithm when new flights are added that:
- Preserves flight order (never sorts flight IDs for deduplication)
- Prevents cycles (no flight reused within same journey)
- Handles forward, backward, and middle insertion patterns
- Uses Set<String> for efficient incremental deduplication during generation

### Performance Requirements
- P90 search latency < 100ms
- Strong consistency for bookings (no double-booking)
- New journeys visible < 5 seconds after flight addition
- Scale: 10M DAU, 3k peak searches/sec

### Database Schema Notes
- Journeys table uses JSONB flight_details: `{"flights": [{"id": "uuid", "order": 1}, ...]}`
- Unique constraints on ordered flight combinations prevent duplicates
- Read replicas used for search queries, primary for all writes
