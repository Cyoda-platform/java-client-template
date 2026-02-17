# Customer Management API - Functional Requirements

## Overview
A REST API to manage customers with CRUD operations. This service provides endpoints to create, read, update, and delete customer records.

## Functional Requirements

1. Customer Entity
- id: UUID (server-generated)
- name: string (required)
- email: string (required, unique, valid email)
- phone: string (optional)

2. Endpoints
- POST /api/customers: Create a new customer. Returns 201 with Location header and created resource.
- GET /api/customers: List customers with pagination (page, size) and optional name/email filters.
- GET /api/customers/{id}: Retrieve customer by id. Returns 404 if not found.
- PUT /api/customers/{id}: Replace customer. Returns 200 with updated resource or 201 if created.
- PATCH /api/customers/{id}: Partial update of customer fields. Returns 200.
- DELETE /api/customers/{id}: Delete customer. Returns 204.

3. Validation
- name and email are required on create.
- email must be unique across customers.
- phone must be E.164 format when provided.

4. Persistence
- Use an embedded datastore (H2) for development.

5. Tests
- Unit tests for services and controllers.
- Integration tests against H2 DB.

6. Non-functional
- JSON over HTTP, application/json content-type.
- OpenAPI 3 documentation available at /swagger-ui.html.

## Acceptance Criteria
- Full CRUD endpoints working with H2 and validated inputs.
- Tests covering at least 80% of service logic.

