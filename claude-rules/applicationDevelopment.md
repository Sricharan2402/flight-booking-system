# Application Development Rules

This document defines **mandatory rules** for developing Spring Boot applications with synchronous APIs.
It ensures **consistency, maintainability, and clear separation of concerns** across all layers.

---

## 1. Controller Layer

The Controller is the entry point for handling **HTTP requests** and returning **responses**.
Controllers MUST be generated using **OpenAPI-generated interfaces**.

### 1.1 Request/Response DTOs

* **Dedicated DTOs** MUST be used for all request/response bodies. These DTOs will be the objects generated from the openAPI spec
* Internal **domain models MUST NOT** be exposed.
* A **mapping layer** MUST exist between Controller DTOs and Service models.

    * Tools: MapStruct, ModelMapper, or manual mappers.
* **Input Mapping:** Request DTO → Service Input Model
* **Output Mapping:** Service Output Model → Response DTO

### 1.2 REST & HTTP Semantics

* Endpoints MUST follow **RESTful principles**:

    * Use **nouns** for resources.
    * Adhere to **HTTP method semantics** (GET, POST, PUT, PATCH, DELETE).
    * Return **appropriate status codes** (200, 201, 204, 404, 500).

### 1.3 Controller Responsibilities

Controllers MUST NOT contain business logic. Their role is limited to:

1. Receiving HTTP requests.
2. Validating input.
3. Mapping Request DTO → Service Input.
4. Delegating to Service layer.
5. Mapping Service Output → Response DTO.
6. Returning HTTP response.

---

## 2. Service Layer

The Service layer encapsulates **business logic**, orchestrates operations, applies rules, and manages transactions.

### 2.1 Design Patterns

* Services MUST apply appropriate **design patterns** for modularity and maintainability.
* The chosen pattern MUST be **documented** with name and rationale.
* Common patterns:

    * **Facade:** Simplify access to subsystems.
    * **Strategy:** Encapsulate varying business logic.
    * **Builder:** Construct complex input/domain models.
    * **Repository:** Abstract database access.
    * **Factory:** Create domain objects based on rules.

### 2.2 Database Interaction

* Service layer MUST interact with the **Database layer only through defined interfaces**.
* **Direct database client usage is prohibited.**

### 2.3 Dependency Injection

* Use **constructor-based DI** for repositories.
* Field/setter injection MUST NOT be used.

### 2.4 Domain Models

* Services operate on **domain models (POJOs/Kotlin data classes)**.
* Mapping between **domain models and database entities** MUST happen at the **Service/Database boundary**.

### 2.6 Business Logic Enforcement

* All **core rules, validations, and orchestration** MUST reside in the Service layer.

---

## 3. Database Layer

The Database layer abstracts **PostgreSQL** access using **Jooq**.

### 3.1 Interfaces & Implementations

* For each entity/aggregate root:

    * Define a **Repository Interface** (e.g., `findById`, `save`).
    * Provide a **Jooq-based implementation**.
* Direct JDBC usage is **forbidden**; all queries MUST go through Jooq `DSLContext`.

### 3.2 Database Configuration

* On startup, the application MUST validate database configuration (`spring.datasource.url`, username, password).
* If invalid/missing:

    * Application MUST **fail fast**.
    * Log a **clear error message**.

### 3.3 Client Setup

* PostgreSQL connection is configured via **Spring Boot Auto-Configuration** or explicit `@Configuration`.
* A **DataSource** MUST be created, and Jooq’s **DSLContext** MUST use it.
* `DSLContext` is the **only entry point** for all database operations.

---

## Summary of Layer Responsibilities

| Layer          | Responsibilities                                                                | MUST NOT Do                                     |
| -------------- | ------------------------------------------------------------------------------- | ----------------------------------------------- |
| **Controller** | Handle requests, validate DTOs, map to Service models, return responses.        | Contain business logic or expose domain models. |
| **Service**    | Contain business logic, apply patterns, manage transactions, use domain models. | Access DB directly or bypass Repository layer.  |
| **Database**   | Provide repository interfaces + Jooq implementations, manage DB access.         | Expose JDBC or leak DB details to upper layers. |

---
