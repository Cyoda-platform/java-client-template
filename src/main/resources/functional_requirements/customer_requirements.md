# Customer API Functional Requirements

## Overview
Build a Java-based REST API for customer management with full CRUD operations using the Cyoda Java template. The API will implement the Standard Profile for customers and follow an Authenticated Business feature profile.

## Customer Entity (Standard Profile)
- id: UUID (string)
- firstName: string
- lastName: string
- email: string (unique, validated)
- phone: string
- address:
  - line1: string
  - line2: string
  - city: string
  - state: string
  - postalCode: string
  - country: string
- createdAt: ISO-8601 timestamp
- updatedAt: ISO-8601 timestamp

## Feature Profile: Authenticated Business
- Authentication: JWT bearer tokens
- Authorization: role-based access (ADMIN, USER)
- Pagination & sorting on list endpoints
- Soft delete: `deleted` boolean flag; excluded from default list responses
- Unique constraint: email
- Audit fields: createdBy, updatedBy
- Input validation with 400/422 responses

## API Endpoints
- POST /api/customers — create a customer (ADMIN or USER)
- GET /api/customers — list customers with pagination & sorting
- GET /api/customers/{id} — retrieve a customer by id
- PUT /api/customers/{id} — full update (ADMIN or owner)
- PATCH /api/customers/{id} — partial update (ADMIN or owner)
- DELETE /api/customers/{id} — soft delete (ADMIN only)

## Non-Functional Requirements
- Java 11+ compatibility
- Unit and integration tests for controllers, services, and repositories
- Use in-memory H2 database for dev & tests; production datasource configurable via environment
- Logging with structured logs (JSON)
- Health endpoint: /actuator/health
- Build with Maven

## Deliverables
- Entity model, DTOs, repository, service, controller
- JWT authentication filter & role-based access
- Pagination & sorting implementation
- Soft delete handling at repository/service level
- Test coverage for main flows
