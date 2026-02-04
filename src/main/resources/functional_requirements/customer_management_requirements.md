# Customer Management - Functional Requirements

Project: Customer Management REST API (Java)
Scope: Retail (B2C customers, addresses, basic KYC)

Objectives:
- Provide a secure, scalable REST API for managing customer records.
- Support CRUD operations for customers with address management and basic KYC checks.
- Follow best practices for validation, pagination, and operation tracing.

Functional Requirements:

1. Customer Entity
   - Fields: id (UUID), firstName, lastName, email (unique), phone, dateOfBirth, status (NEW, VERIFIED, SUSPENDED), createdAt, updatedAt
   - Address: nested object with street, city, state, postalCode, country
   - KYC: kycStatus (PENDING, VERIFIED, REJECTED), kycDocumentType, kycDocumentId

2. API Endpoints
   - POST /api/customers: Create a new customer. Validate required fields and email uniqueness. Trigger initial KYC status = PENDING.
   - GET /api/customers/{id}: Retrieve customer details.
   - GET /api/customers: List customers with pagination, filtering by status, kycStatus, and search by name/email.
   - PUT /api/customers/{id}: Update customer details (except id). Validate email uniqueness.
   - DELETE /api/customers/{id}: Soft delete customer (mark status=SUSPENDED and preserve record).

3. Authentication & Authorization
   - OAuth2 / JWT for API access. Roles: ADMIN, SUPPORT, READ_ONLY. Enforce RBAC on endpoints.

4. Persistence
   - Use Cyoda Managed DB (Postgres compatible) as primary datastore.
   - Define schema with unique constraints on email.

5. Non-functional
   - Input validation and uniqueness constraints on email.
   - Pagination defaults: page=0, size=20. Support sort by createdAt.
   - Logging of create/update/delete operations with user metadata.
   - Keep service stateless to enable horizontal scaling.

6. Acceptance Criteria
   - All endpoints return appropriate HTTP status codes and JSON responses.
   - Validation errors return 400 with field-level messages.
   - Protected endpoints return 401/403 as appropriate.
   - Soft-deleted customers do not appear in list endpoints unless explicitly requested.

7. Deliverables
   - Spring Boot Java service with REST endpoints, service layer, repository layer, DTOs, entity models, basic integration tests.
   - Configuration for OAuth2/JWT using Cyoda auth integration.


