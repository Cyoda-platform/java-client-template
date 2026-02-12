# Customer CRUD API Requirements

## Overview
Build a REST API in Java providing CRUD operations for Customer management. The API will be generated from the Cyoda Java client template branch d6ce21c1-beaa-4fc2-8787-dbef6e69bf50.

## Functional Requirements
- Create Customer: POST /api/customers
  - Validates required fields: firstName, lastName, email
  - Email must be unique and validated for format
- Read Customer: GET /api/customers/{id}
- List Customers: GET /api/customers
  - Supports pagination: page, size
  - Supports sorting by createdAt desc/asc
- Update Customer: PUT /api/customers/{id}
  - Partial updates allowed
- Delete Customer: DELETE /api/customers/{id}
  - Hard delete

## Non-functional Requirements
- Basic field validation (non-empty, email format)
- Use DTOs for input/output models
- Provide unit tests for service layer and integration tests for controllers
- API should follow RESTful principles and return appropriate HTTP status codes

## Acceptance Criteria
- All endpoints implemented and passing unit/integration tests
- Data persisted in the repository's configured storage
- Build runs via standard Maven commands
