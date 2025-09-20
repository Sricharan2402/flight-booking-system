# Flight Booking System

A high-performance, scalable flight booking system designed for single-airline operations, featuring real-time multi-leg journey search, distributed seat locking, and event-driven architecture capable of handling 10M daily active users.

## 🚀 Features

### Core Capabilities
- **Lightning-Fast Search** - Sub-100ms P90 latency with Redis caching and PostgreSQL read replicas
- **Multi-leg Journeys** - Intelligent routing for up to 3-flight combinations with automatic connection generation
- **Atomic Booking System** - Strong consistency using Redis distributed locks and PostgreSQL ACID transactions
- **Event-Driven Architecture** - Real-time journey generation via Kafka when new flights are added
- **High Availability** - Supports 3,000+ concurrent searches/second with 99.9% uptime

### Advanced Features
- **Automatic Journey Generation** - BFS algorithm creates optimal flight combinations automatically
- **Seat Scarcity Management** - Redis sorted sets prevent overbooking under high concurrency
- **Intelligent Caching** - Multi-layer caching strategy with 100% cache hit ratios
- **Performance Monitoring** - Built-in metrics and comprehensive test coverage
- **Admin Flight Management** - RESTful APIs for airline operations

## 🏗️ Architecture


### Architecture Overview
The system follows a microservices architecture with event-driven journey generation and aggressive caching for search performance.

```
               ┌─────────────┐    ┌─────────────┐
               │ User Client │    │ Admin Client│
               └──────┬──────┘    └──────┬──────┘
                      │                 │
                      └──────┬──────────┘
                             ▼
                  ┌───────────────────────┐
                  │      API Gateway      │
                  │  (Routing/Auth/Rate)  │
                  └─────────┬─────────────┘
             ┌──────────────┼──────────────┐
             ▼              ▼              ▼
   ┌────────────────┐ ┌──────────────┐ ┌─────────────┐
   │ Journey Search │ │ Admin Service │ │ Booking Svc │
   │    Service     │ │ (Add Flights) │ │ (Book Seats)│
   └───────┬────────┘ └──────┬───────┘ └──────┬──────┘
           │                 │                │
           ▼                 │                ▼
   ┌─────────────┐           │         ┌──────────────┐
   │ Redis       │           │         │ Redis        │
   │ (Search     │           │         │ (Seat Locks) │
   │ Cache/CDN)  │           │         └──────────────┘
   └──────┬──────┘           │                │
          │                  │                │
          ▼                  │                ▼
   ┌────────────────────┐    │          ┌───────────────┐
   │    PostgreSQL      │<───┘          │ Booking Svc   │
   │ Flights/Journeys/  │               │ (final writes │
   │ Seats/Bookings     │               │ + reads)      │
   └────────────────────┘               └───────────────┘
```

```
          ┌──────────────┐
          │ Admin Service│
          │ (Add Flight) │
          └───────┬──────┘
                  │
                  ▼
        ┌──────────────────┐
        │  PostgreSQL      │
        │  (Flights Table) │
        └───────┬──────────┘
                │
                ▼
             ┌─────────┐
             │  Kafka  │
             └────┬────┘
                  │ flight-created event
                  ▼
   ┌─────────────────────────┐
   │ Journey Ingestion       │
   │ Consumer (Precompute)   │
   └─────────────┬───────────┘
                 │
                 ▼
        ┌──────────────────┐
        │  PostgreSQL      │
        │  (Journeys Table)│
        └──────────────────┘

```

### Key Design Principles
- **Precomputation Strategy** - All valid journey combinations generated offline
- **Cache-First Search** - Redis-first with PostgreSQL fallback
- **Strong Consistency** - Distributed locking with ACID transactions
- **Event-Driven Updates** - Async journey generation via Kafka

> **📋 For complete architecture details, algorithms, and data models, see [DesignDoc.md](./DesignDoc.md)**

## 🛠️ Prerequisites

### Required Software
- **Java 21** (OpenJDK or Oracle JDK)
- **Docker** 24.0+ and **Docker Compose** 2.0+
- **Gradle** 8.0+ (or use wrapper `./gradlew`)

### System Requirements
- **Memory**: 8GB RAM minimum (16GB recommended)
- **Storage**: 10GB free space

## 🚀 Quick Start

### 1. Clone and Setup
```bash
git clone <repository-url>
cd flight-booking-system
```

### 2. Start Infrastructure
```bash
# Start PostgreSQL, Redis, Kafka, Zookeeper
cd docker
docker compose --env-file ../.env up -d

# Verify services are running
docker compose ps
```

### 3. Build and Run Application
```bash
# Build the project
./gradlew build

# Run database migrations
./gradlew flywayMigrate

# Start the application
./gradlew bootRun
```

### 4. Verify Installation
```bash
# Health check
curl http://localhost:8080/actuator/health

# Expected response: {"status":"UP"}
```

## ⚙️ Configuration

### Environment Variables
Create a `.env.example` file in the project root:

```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=5433
DB_NAME=flight_booking
DB_USERNAME=flightuser
DB_PASSWORD=flightpass

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=localhost:9093
KAFKA_AUTO_OFFSET_RESET=earliest

# Application Configuration
SERVER_PORT=8080
LOGGING_LEVEL_ROOT=INFO
SPRING_PROFILES_ACTIVE=local
```

### Application Configuration
Key configuration files:
- `src/main/resources/application.yml` - Main Spring Boot configuration
- `src/main/resources/application-test.yml` - Test environment settings
- `docker/.env` - Docker infrastructure environment variables

### Database Configuration
The system uses PostgreSQL with the following key settings:
- **Connection Pool**: HikariCP with 50 connections per service
- **Read Replicas**: Configured for search service queries
- **Migrations**: Flyway-managed schema with versioned migrations

## 📖 Usage Examples

### Flight Management (Admin API)
```bash
# Create a new flight
curl -X POST "http://localhost:8080/api/v1/flights" \
  -H "Content-Type: application/json" \
  -d '{
    "source_airport": "LAX",
    "destination_airport": "NYC",
    "departure_time": "2025-09-25T08:00:00Z",
    "arrival_time": "2025-09-25T11:30:00Z",
    "airplane_id": "b48d9387-ced5-417c-8b85-4ae1e2a8bc3b",
    "price": 299.99,
    "total_seats": 180
  }'
```

### Flight Search (User API)
```bash
# Search for flights
curl "http://localhost:8080/api/v1/journeys/search?source_airport=LAX&destination_airport=NYC&departure_date=2025-09-25&passengers=1&limit=10"
```

### Booking Creation (User API)
```bash
# Create a booking
curl -X POST "http://localhost:8080/api/v1/bookings" \
  -H "Content-Type: application/json" \
  -H "userId: 550e8400-e29b-41d4-a716-446655440003" \
  -d '{
    "journey_id": "651f3ee0-8977-401f-bb65-8a1e68fba170",
    "passenger_count": 1,
    "payment_id": "payment_test_12345"
  }'
```

### Retrieve Booking
```bash
# Get booking details
curl "http://localhost:8080/api/v1/bookings/{booking_id}"
```

## 🧪 Testing

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test Categories
```bash
# Unit tests only
./gradlew test --tests="*.services.*"

# Application integration tests
./gradlew test --tests="*.application.*"

# Performance tests
./gradlew test --tests="*PerformanceApplicationTest"

# Concurrency tests
./gradlew test --tests="*ConcurrencyApplicationTest"
```

### Test Coverage
```bash
# Generate test coverage report
./gradlew jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

### Performance Benchmarks
> **📊 See detailed test results in [test-results/](./test-results/) directory**

### Environment-Specific Commands
```bash
# Production build and deployment
./gradlew clean build bootJar
java -jar -Dspring.profiles.active=prod build/libs/flight-booking-system.jar

# Docker production deployment
cd docker
docker compose -f docker-compose.prod.yml up -d
```

### Code Standards
- **Language**: Kotlin with Spring Boot conventions
- **Architecture**: Follow patterns defined in [DesignDoc.md](./DesignDoc.md)
- **Testing**: Maintain >90% test coverage
- **Documentation**: Update README and design docs for significant changes


## 📚 Documentation & Resources

### Core Documentation
- **[Design Document](./DesignDoc.md)** - Complete system architecture, algorithms, and data models
- **[CLAUDE.md](./CLAUDE.md)** - Development guidelines and project-specific coding standards
- **[API Documentation](./src/main/resources/openapi/)** - OpenAPI 3.0 specifications for all endpoints

### Test Results & Performance
- **[API Test Results](./test-results/api-test-results.md)** - End-to-end API validation results
- **[Search Cache Performance](./test-results/search-cache-performance-test-results.md)** - Cache performance benchmarks
- **[Booking Concurrency Results](./test-results/booking-concurrency-test-results.md)** - Concurrency and race condition tests

### Development Resources
- **[User Prompts Log](./prompts/userPrompts.md)** - Development history and evolution
- **[Docker Setup](./docker/)** - Local development infrastructure configuration
- **[Database Migrations](./src/main/resources/db/migration/)** - Flyway schema management

### External Resources
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [Kotlin Documentation](https://kotlinlang.org/docs/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Redis Documentation](https://redis.io/documentation)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)

---

## ⚡ Quick Commands Reference

```bash
# Development
./gradlew bootRun                    # Start application
./gradlew test                       # Run all tests
./gradlew build                      # Build project

# Infrastructure
cd docker && docker compose up -d   # Start services
cd docker && docker compose down    # Stop services
cd docker && docker compose logs -f # View logs

# Database
./gradlew flywayMigrate             # Run migrations
./gradlew flywayInfo                # Migration status

# Testing
./gradlew test --tests="*Performance*" # Performance tests
./gradlew test --tests="*Concurrency*" # Concurrency tests
curl http://localhost:8080/actuator/health # Health check
```

---

**Built with Kotlin, Spring Boot, and modern distributed systems principles**