# Functional Requirements — CRUD Customer Service

## Overview
A Java-based CRUD Customer Service exposing REST endpoints to create, read, update, and delete Customer records. Uses an embedded H2 database for persistence and includes healthcheck and basic unit/integration tests. The service will follow a typical layered architecture (controller, service, repository) and expose OpenAPI documentation.

## Non-Functional Requirements
- Language: Java 11+
- Build: Maven
- Embedded DB: H2 in file mode for easy local testing
- Logging: Structured JSON logging
- Healthcheck: /health endpoint returning status
- Tests: Unit tests for service layer and integration tests for REST endpoints

## Functional Requirements
1. Customer entity with fields:
   - id: UUID
   - firstName: string (required)
   - lastName: string (required)
   - email: string (required, valid format)
   - phone: string (optional)
   - createdAt: timestamp
   - updatedAt: timestamp

2. REST API endpoints:
   - POST /customers — create a customer
   - GET /customers — list customers (pagination: page, size)
   - GET /customers/{id} — get customer by id
   - PUT /customers/{id} — update customer
   - DELETE /customers/{id} — delete customer

3. Validation:
   - firstName, lastName, email required
   - email must be a valid email format

4. Persistence:
   - Use H2 with JPA/Hibernate
   - Repository pattern using Spring Data JPA

5. Documentation:
   - OpenAPI 3 docs at /swagger-ui.html or /swagger-ui/index.html

6. Security (basic):
   - Allow unauthenticated access for now; skeleton for future auth

## Deliverables
- Maven project structure
- Entity, Repository, Service, Controller classes
- Application entry point
- Application configuration for H2
- Basic unit and integration tests

## Notes
- This is intended as a starting template; we can extend with DTOs, mapping (MapStruct), and advanced validation later.
