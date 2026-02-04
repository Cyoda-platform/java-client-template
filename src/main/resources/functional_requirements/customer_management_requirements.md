# Customer Management API - Functional Requirements

## Overview
Build a RESTful Java application providing CRUD operations for customer management, plus support for technical service accounts that can obtain tokens via a client-credentials flow.

## Functional Requirements

1. Customer CRUD
   - Create, Read, Update, Delete operations for Customer entities.
   - Customer fields (Enterprise profile): id, firstName, lastName, email, phone, addresses, dateOfBirth, organization, contacts, roles, billingInfo, metadata, createdAt, updatedAt.

2. Technical Users / Service Accounts
   - Support a separate TechnicalUser entity for service accounts:
     - Fields: id, clientId, clientSecret (hashed), scopes (list), ownerCustomerId, createdAt, expiresAt, revoked (boolean).
   - Endpoint to issue tokens using client credentials:
     - POST /oauth/token
     - Request: client_id, client_secret, scope
     - Response: access_token, token_type, expires_in, scope

3. Security
   - Protect Customer CRUD endpoints: require valid bearer token with appropriate scope (e.g., customer:read, customer:write).
   - Support scope-based authorization for both Customer APIs and token issuance.

4. Persistence
   - Use an embedded database for initial development (H2) with JPA/Hibernate entities.

5. API
   - REST endpoints under /api/v1/customers for Customer CRUD.
   - Endpoints under /api/v1/technical-users for managing service accounts (create/list/revoke).

## Non-Functional Requirements

- Java 17+, Spring Boot.
- Unit tests for service layer and integration tests for controllers.
- Logging and basic metrics.
- Configuration via application.yml and secrets via environment variables.

## Acceptance Criteria

- All CRUD endpoints implemented and accessible with valid token.
- Token issuance endpoint validates client credentials and returns JWT-like tokens.
- README updated with run instructions and sample curl commands.
