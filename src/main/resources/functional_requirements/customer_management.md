# Customer Management API - Functional Requirements

## Overview

Provide a simple RESTful API for managing customers with full CRUD operations. This service will be part of the Cyoda Java template application and expose HTTP endpoints for client integration.

## Scope

- Basic CRUD operations for Customer resource.
- Customer fields: id (UUID), name (string), email (string), phone (string).
- Simple validation: email format and non-empty name.
- In-memory or embedded persistence suitable for quick local runs (H2 / in-memory store).
- JSON request/response body format.

## Endpoints

- POST /api/customers
  - Create a new customer
  - Request body: {"name":"...", "email":"...", "phone":"..."}
  - Response: 201 Created with created customer and Location header

- GET /api/customers
  - List all customers
  - Response: 200 OK with array of customers

- GET /api/customers/{id}
  - Retrieve a single customer
  - Response: 200 OK if found, 404 Not Found if missing

- PUT /api/customers/{id}
  - Replace an existing customer
  - Request body: same as create
  - Response: 200 OK with updated customer, 404 if missing

- DELETE /api/customers/{id}
  - Delete a customer
  - Response: 204 No Content if deleted, 404 if missing

## Validation & Errors

- Validate email format; return 400 Bad Request with error details for invalid input.
- Validate name is not empty.

## Persistence

- Use an in-memory H2 database for simplicity during development.
- Provide repository interface and JPA entities compatible with H2.

## Non-functional

- Use Spring Boot for the REST API.
- Java 11+ compatible.
- Tests: unit tests for service layer and integration tests for controller endpoints (basic coverage).

## Deliverables

- Source code in the repository branch
- README with instructions to run locally
- Basic unit/integration tests
