# Docker Compose Configuration Rules

## Pre-generation Prompt

Always ask the user before generating:

1. **"What technologies, services, or dependencies (e.g., PostgreSQL, Redis, MongoDB, Kafka, etc.) do you want in the docker-compose.yml file?"**
2. **"Do you want to include multiple services (e.g., multiple databases or caches)?"**

---

## Expectations for the Coding Agent

**❗ Do not include the application service in the Docker Compose file.**

The coding agent must:

- Configure all requested infrastructure services (e.g., PostgreSQL, Redis, Kafka, MongoDB, etc.)
- Set up all required ports and expose them so the application can connect externally
- Include all connection credentials and environment variables the application would need
- Support multiple instances of the same type of service (e.g., two PostgreSQL databases with different names/ports)
- Ensure services are ready to use by including health checks
- Document all exposed variables, ports, and service names

---

## Service Dependencies

### Startup Sequencing
- Use `depends_on` with `condition: service_healthy` for proper startup sequencing
- Include health checks for each service

---

## Environment Variables

### Configuration Management
- Use environment variables for all service configuration
- Provide default values using `${VARIABLE:-default}` syntax
- Document environment variables in the docker-compose.yml file

---

## Volumes

### Data Persistence
- Use named volumes for data persistence (e.g., for database storage)
- Define volume mounts for configuration/logs if required by the service

---

## Health Checks

### Configuration Requirements
Configure health checks with:

- Appropriate `interval`, `timeout`, and `start_period`
- Correct protocol and target port
- Health checks must be reliable indicators of service readiness

---

## Docker Compose Network Configuration

### Network Isolation
- Use custom user-defined networks for isolating services
- Configure `network_aliases` for easier internal access
- Expose only required ports to the host machine
- Use internal networks for inter-service communication

---

## Docker Security Best Practices

### Security Requirements
- **Do not** store secrets in Docker images or version-controlled files
- Use Docker secrets or environment variables for sensitive data
- Pin versions for all service images (avoid `latest`)
- Run containers as non-root users where possible
- Limit container capabilities and use read-only file systems
- Ensure logs are enabled for all services

---

## Infrastructure Testing

### Connection Testing Script
- **MUST** create a test script named `test-infrastructure.sh` in the `docker/` directory
- The script **MUST**:
  - Load environment variables from the root `.env` file
  - Test port connectivity for all services using `nc -z`
  - Display connection details and Spring Boot configuration examples
  - Be executable (`chmod +x`)
  - Use proper host variables (not hardcoded localhost for all services)
- The script helps developers verify all Docker infrastructure is working correctly

---

## Docker Compose Commands

### Modern Docker Compose Usage
- Use `docker compose` (not `docker-compose`) for all operations
- Always specify the environment file: `docker compose --env-file ../.env`
- Common commands:
  - Start all services: `docker compose --env-file ../.env up -d`
  - Stop all services: `docker compose --env-file ../.env down`
  - View logs: `docker compose --env-file ../.env logs [service-name]`
  - Restart service: `docker compose --env-file ../.env restart [service-name]`