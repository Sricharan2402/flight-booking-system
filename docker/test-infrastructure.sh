#!/bin/bash

# Flight Booking System - Infrastructure Connection Test
# Tests connectivity to all Docker services and displays configuration details

set -e

# Load environment variables from root .env file
if [ -f "../.env" ]; then
    source "../.env"
    echo "✅ Loaded environment variables from .env file"
else
    echo "⚠️  Warning: .env file not found in parent directory, using defaults"
fi

# Set defaults if not in .env
POSTGRES_HOST=${POSTGRES_HOST:-localhost}
POSTGRES_PORT=${POSTGRES_PORT:-5433}
POSTGRES_DB=${POSTGRES_DB:-flight_booking}
POSTGRES_USER=${POSTGRES_USER:-flight_user}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-flight_password}

REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6380}

KAFKA_HOST=${KAFKA_HOST:-localhost}
KAFKA_PORT=${KAFKA_PORT:-9093}
ZOOKEEPER_HOST=${ZOOKEEPER_HOST:-localhost}
ZOOKEEPER_PORT=${ZOOKEEPER_PORT:-2182}

echo ""
echo "🧪 Testing Docker Infrastructure Connectivity..."
echo "================================================"

# Test PostgreSQL connectivity
echo ""
echo "🐘 Testing PostgreSQL connection..."
if nc -z "$POSTGRES_HOST" "$POSTGRES_PORT"; then
    echo "✅ PostgreSQL is accessible at $POSTGRES_HOST:$POSTGRES_PORT"
else
    echo "❌ PostgreSQL is NOT accessible at $POSTGRES_HOST:$POSTGRES_PORT"
fi

# Test Redis connectivity
echo ""
echo "🔴 Testing Redis connection..."
if nc -z "$REDIS_HOST" "$REDIS_PORT"; then
    echo "✅ Redis is accessible at $REDIS_HOST:$REDIS_PORT"
else
    echo "❌ Redis is NOT accessible at $REDIS_HOST:$REDIS_PORT"
fi

# Test Zookeeper connectivity
echo ""
echo "🦌 Testing Zookeeper connection..."
if nc -z "$ZOOKEEPER_HOST" "$ZOOKEEPER_PORT"; then
    echo "✅ Zookeeper is accessible at $ZOOKEEPER_HOST:$ZOOKEEPER_PORT"
else
    echo "❌ Zookeeper is NOT accessible at $ZOOKEEPER_HOST:$ZOOKEEPER_PORT"
fi

# Test Kafka connectivity
echo ""
echo "📨 Testing Kafka connection..."
if nc -z "$KAFKA_HOST" "$KAFKA_PORT"; then
    echo "✅ Kafka is accessible at $KAFKA_HOST:$KAFKA_PORT"
else
    echo "❌ Kafka is NOT accessible at $KAFKA_HOST:$KAFKA_PORT"
fi

echo ""
echo "📋 Spring Boot Configuration Examples:"
echo "======================================="
echo ""
echo "# application.yml or application.properties"
echo "spring:"
echo "  datasource:"
echo "    url: jdbc:postgresql://$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DB"
echo "    username: $POSTGRES_USER"
echo "    password: $POSTGRES_PASSWORD"
echo "    driver-class-name: org.postgresql.Driver"
echo ""
echo "  data:"
echo "    redis:"
echo "      host: $REDIS_HOST"
echo "      port: $REDIS_PORT"
echo ""
echo "  kafka:"
echo "    bootstrap-servers: $KAFKA_HOST:$KAFKA_PORT"
echo "    consumer:"
echo "      group-id: flight-booking-group"
echo "    producer:"
echo "      key-serializer: org.apache.kafka.common.serialization.StringSerializer"
echo "      value-serializer: org.apache.kafka.common.serialization.StringSerializer"
echo ""
echo "📝 Environment Variables (for use in Spring Boot):"
echo "=================================================="
echo "POSTGRES_URL=jdbc:postgresql://$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DB"
echo "POSTGRES_USERNAME=$POSTGRES_USER"
echo "POSTGRES_PASSWORD=$POSTGRES_PASSWORD"
echo "REDIS_HOST=$REDIS_HOST"
echo "REDIS_PORT=$REDIS_PORT"
echo "KAFKA_BOOTSTRAP_SERVERS=$KAFKA_HOST:$KAFKA_PORT"
echo ""
echo "🐳 Docker Commands:"
echo "=================="
echo "Start all services:    docker compose --env-file ../.env up -d"
echo "Stop all services:     docker compose --env-file ../.env down"
echo "View logs:             docker compose --env-file ../.env logs [service-name]"
echo "Restart service:       docker compose --env-file ../.env restart [service-name]"
echo ""
echo "🔍 Service Health Check:"
echo "========================"
echo "Check container status: docker compose --env-file ../.env ps"
echo "View service logs:      docker compose --env-file ../.env logs -f [postgres|redis|kafka|zookeeper]"