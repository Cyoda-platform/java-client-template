# Customer Management API - Functional Requirements

## Overview
Provide a RESTful API for managing customers with CRUD operations. The service will be built in Java using the Cyoda java-client-template. It will expose endpoints to create, read, update, delete, and list customers. The API must include input validation, error handling, pagination for list endpoints, and basic filtering.

## Actors
- System Admin: Manages customer data.
- External Client: Consumes the API to manage customers.

## Core Requirements
1. Create Customer
   - Endpoint: POST /api/customers
   - Request: JSON body with customer attributes.
   - Response: 201 Created with customer object and Location header.
   - Validation: Required fields (firstName, lastName, email); email must be unique and valid.

2. Get Customer by ID
   - Endpoint: GET /api/customers/{customerId}
   - Response: 200 OK with customer object or 404 Not Found.

3. Update Customer
   - Endpoint: PUT /api/customers/{customerId}
   - Request: JSON body with fields to replace (full update).
   - Response: 200 OK with updated customer or 404 Not Found.

4. Partial Update (Patch)
   - Endpoint: PATCH /api/customers/{customerId}
   - Request: JSON Patch or partial JSON with fields to update.
   - Response: 200 OK with updated customer or 404 Not Found.

5. Delete Customer
   - Endpoint: DELETE /api/customers/{customerId}
   - Response: 204 No Content or 404 Not Found.

6. List Customers
   - Endpoint: GET /api/customers
   - Query Params: page (default 0), size (default 20), sort (e.g., lastName,asc), filter params (email, lastName)
   - Response: 200 OK with paginated list of customers and metadata (totalElements, totalPages, page, size)

## Data Model
Customer:
- id: UUID (generated)
- firstName: string (required)
- lastName: string (required)
- email: string (required, unique)
- phone: string (optional)
- address: object (optional: street, city, state, postalCode, country)
- createdAt: timestamp (auto)
- updatedAt: timestamp (auto)

## Validation Rules
- email: valid email format, unique
- firstName/lastName: non-empty, max 100 chars
- phone: E.164 format if provided
- address.postalCode: max 20 chars

## Error Handling
- Return structured error responses with fields: timestamp, status, error, message, path
- Use standard HTTP status codes

## Security
- For now, assume API key or bearer token will be provided later. Endpoints must be ready to accept authentication headers.

## Non-functional Requirements
- API documentation generated via OpenAPI/Swagger
- Unit and integration tests covering CRUD operations
- Use in-memory H2 database for dev, configurable to other stores in prod
- Logging and basic metrics (request counts, errors)

## Acceptance Criteria
- All endpoints work as specified and pass integration tests
- Email uniqueness enforced at DB level
- Pagination and filtering work for list endpoint
- API documented and runnable via provided README

## Future Enhancements
- Soft deletes with restore endpoint
- Advanced filtering and search
- Role-based access control
