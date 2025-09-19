---
trigger: always_on
---

# Spring Boot Rules

Application Development Rules: Spring Boot (Synchronous)

General Rules
Spring Boot Framework: All application development MUST utilize the Spring Boot framework.

Synchronous APIs: All RESTful APIs exposed by the application MUST be synchronous (blocking) in nature. The spring-boot-starter-web dependency MUST be used, and spring-boot-starter-webflux MUST NOT be included.

Dependency Management: All project dependencies MUST be managed via Gradle. Adherence to the versions recommended by Spring Boot's dependency management is mandatory.

Configuration Management: Application configurations (e.g., database credentials, external service URLs, custom properties) MUST be externalized using Spring Boot's application.yml files, or environment variables. Sensitive information MUST NOT be hardcoded.


## Spring Boot Specifics
- Use Spring Boot starters for quick project setup and dependency management
- Implement proper use of annotations (e.g., @SpringBootApplication, @RestController, @Service)
- Utilize Spring Boot's auto-configuration features effectively
- Implement proper exception handling using @ControllerAdvice and @ExceptionHandler
- Keep controllers thin; business logic belongs in services

## Configuration and Properties
- Use application.yml over application.properties for better structure
- Implement environment-specific configurations using Spring Profiles
- Use @ConfigurationProperties for type-safe configuration properties
- Keep configuration organized by concern
- Use environment variables with default values for all configuration properties
- Follow UPPER_SNAKE_CASE naming convention for environment variables
- Never commit sensitive data in configuration files

## Dependency Injection and IoC
- Use constructor injection over field injection for better testability
- Properly scope beans (@Singleton, @Prototype, etc.)
- Use appropriate stereotypes (@Service, @Repository, @Component, @Controller)
- Avoid circular dependencies
- Document complex dependency graphs with @Qualifier

## Testing
- Write unit tests using JUnit 5 and Spring Boot Test
- Follow AAA pattern (Arrange-Act-Assert) in tests
- Use MockK for mocking in Kotlin tests
- Implement integration tests using @SpringBootTest
- Use TestContainers for database tests
- Test edge cases and error conditions
- Create test-specific properties in application-test.yml
- Use test profiles for different scenarios
- NO TEST cases for Controller and Database layers

## Security
- DO NOT USE ANY SPRING SECURITY
- DO NOT USE ANY KIND OF SECURITY
- DO NOT IMPORT SPRING SECURITY DEPENDENCIES

## Logging and Monitoring
- Use SLF4J with Logback for logging
- Implement proper log levels (ERROR, WARN, INFO, DEBUG)
- Don't log sensitive information
- Use Spring Boot Actuator for application monitoring and metrics
- Implement structured logging with JSON format
- Use correlation IDs for request tracing
- Configure log rotation and retention policy

## Error Handling
- Implement comprehensive exception handling
- Create custom exceptions for domain-specific errors
- Provide meaningful error messages
- Standardize error response format across all exception handlers
- Include request paths in log messages for better traceability
- Generate unique error IDs for unexpected exceptions
- Properly handle specific exceptions like DataIntegrityViolationException
- Structure field errors in a consistent, readable format