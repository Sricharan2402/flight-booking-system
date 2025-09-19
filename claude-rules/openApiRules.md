# OpenAPI Specification (OAS) Guidelines

## Table of Contents

- [File Structure](#file-structure)
- [Specification Format](#specification-format)
- [Versioning](#versioning)
- [Documentation Standards](#documentation-standards)
- [Code Generation](#code-generation)
- [Validation](#validation)
- [Required Endpoints](#required-endpoints)
- [Best Practices](#best-practices)

## File Structure

### Location

- Store OpenAPI specifications in: `src/main/resources/openapi/`
- Main specification file must be named: `api.yaml`
- Split large specs into multiple files using `$ref`
- Keep all schema definitions in a `schemas/` subdirectory

### Naming Conventions

- Use kebab-case for file names
- Use snake_case for schema property names
- Use UPPER_SNAKE_CASE for constants
- Use PascalCase for schema names

## Specification Format

### Required Structure

```yaml
openapi: 3.0.3
info:
  title: Service Name API
  description: API documentation
  version: 1.0.0
  contact:
    name: API Support
    email: support@example.com
  license:
    name: Proprietary
servers:
  - url: /api/v1
    description: Production server
paths:
  /health:
    get:
      summary: Health check endpoint
      description: Returns 200 OK if the service is running properly
      operationId: healthCheck
      tags:
        - Health
      responses:
        '200':
          description: Service is healthy
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/HealthResponse'
components:
  schemas:
    HealthResponse:
      type: object
      properties:
        status:
          type: string
          example: "UP"
          description: Current health status of the service
```

## Versioning

### API Versioning

- Include API version in the URL path (e.g., `/api/v1/health`)
- Use semantic versioning (MAJOR.MINOR.PATCH)
- Document breaking changes in release notes
- Support at least the current and previous major versions

## Documentation Standards

### Required Elements

- Clear, concise endpoint descriptions
- All request/response schemas with field-level documentation
- HTTP status codes and their meanings
- Examples with realistic data
- Request/response content types (e.g., `application/json`)
- Required vs. optional fields clearly marked

### Best Practices

- Use markdown for rich text formatting
- Include examples for all schemas
- Document all possible error responses
- Use consistent terminology
- Keep descriptions under 200 characters when possible

## Code Generation

### Configuration

- Use OpenAPI Generator for code generation
- Configure in `build.gradle.kts` with appropriate plugins:

```kotlin
plugins {
    id("org.openapi.generator") version "6.6.0"
}

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set("$rootDir/src/main/resources/openapi/api.yaml")
    outputDir.set("$buildDir/generated")
    modelPackage.set("${project.group}.generated.model")
    apiPackage.set("${project.group}.generated.api")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "useBeanValidation" to "true",
            "documentationProvider" to "none"
        )
    )
}

// Add generated sources to the main source set
sourceSets.main {
    java.srcDirs("$buildDir/generated/src/main/kotlin")
}

// Ensure the openApiGenerate task runs before compilation
tasks.compileKotlin {
    dependsOn("openApiGenerate")
}
```

### Generated Code Management

- Generate models, APIs
- Keep generated code in `build/generated`
- Never modify generated code directly - update the OpenAPI spec instead
- Add `build/generated` to `.gitignore` to prevent committing generated code

## Validation

- Validate OpenAPI specs during build using the `openapi-generator-cli`
- Add validation to CI pipeline
- Ensure all `$refs` are valid
- Verify required fields are documented
- Check for semantic errors

## Required Endpoints

### Health Endpoint

**IMPORTANT:** The API specification must ONLY include a health endpoint

- No other sample APIs, resources, or endpoints should be defined
- The health endpoint should return a 200 OK response
- Implement a controller and service class for the health endpoint
- Do not implement any security or authentication mechanisms

## Best Practices

### General

- Keep the spec DRY (Don't Repeat Yourself)
- Use `$ref` for reusable components
- Document all fields with descriptions and examples
- Use consistent naming conventions
- Include examples for all schemas
- Document default values for optional parameters

### Implementation Rules

- **DO NOT** create any sample APIs or additional endpoints without asking first explicitly
- **DO NOT** add security or authentication mechanisms
