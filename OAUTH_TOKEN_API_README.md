# OAuth2 Token Issuance API (Client Credentials Flow)

This document describes the Token Issuance API implementation for client-credentials OAuth2 flow.

## Overview

The API provides JWT-based access tokens for machine-to-machine (M2M) authentication using the OAuth2 client credentials grant type. Tokens are signed with HMAC-SHA256 and include client scopes.

## Architecture

### Components

- **TechnicalUser Entity**: JPA entity representing OAuth2 service accounts
- **TechnicalUserRepository**: Spring Data JPA repository for database access
- **TechnicalUserService**: Business logic for credential validation and account management
- **TokenService**: JWT token generation and validation
- **TokenController**: REST endpoint for token issuance
- **SecurityConfig**: Spring Security configuration with bearer token support

### Database

- **H2 In-Memory Database** (default for development)
- Tables: `technical_users`, `technical_user_scopes`
- Indexes on `client_id` (unique), `owner_customer_id`, `revoked`

## Configuration

### Environment Variables

```bash
# Required: HMAC secret for token signing (minimum 32 characters recommended)
export CYODA_TOKEN_SECRET="your-secret-key-here-minimum-32-chars"
```

### application.yml Settings

```yaml
app:
  oauth:
    token-secret: ${CYODA_TOKEN_SECRET:change-me-in-production}
    token-expiration-seconds: 3600  # 1 hour default

spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driverClassName: org.h2.Driver
    username: sa
  jpa:
    hibernate:
      ddl-auto: update
```

## API Endpoints

### Issue Token

**Endpoint**: `POST /api/v1/oauth/token`

**Request** (JSON):
```json
{
  "grant_type": "client_credentials",
  "client_id": "svc-analytics-01",
  "client_secret": "your-secret-here",
  "scope": "customer:read customer:write"
}
```

**Response** (200 OK):
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "customer:read customer:write"
}
```

**Error Responses**:
- `401 Unauthorized`: Invalid client_id or client_secret
- `400 Bad Request`: Invalid grant_type or missing required fields

## Usage Examples

### 1. Obtain a Token

```bash
curl -X POST http://localhost:8080/api/v1/oauth/token \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "client_credentials",
    "client_id": "svc-analytics-01",
    "client_secret": "my-secret-password",
    "scope": "customer:read customer:write"
  }'
```

### 2. Use Token to Call Protected Endpoint

```bash
# Get the token first
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/oauth/token \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "client_credentials",
    "client_id": "svc-analytics-01",
    "client_secret": "my-secret-password"
  }' | jq -r '.access_token')

# Use the token to call a protected endpoint
curl -X GET http://localhost:8080/api/v1/customers \
  -H "Authorization: Bearer $TOKEN"
```

### 3. Create a Technical User (Programmatically)

```java
@Autowired
private TechnicalUserService technicalUserService;

public void createServiceAccount() {
    UUID customerId = UUID.fromString("3a2f1e0d-4c3d-11ec-81d3-0242ac130004");
    Set<String> scopes = Set.of("customer:read", "customer:write");
    
    TechnicalUser user = technicalUserService.createTechnicalUser(
        "svc-analytics-01",
        "my-secret-password",
        scopes,
        customerId,
        null  // no expiration
    );
}
```

## Security Considerations

1. **Token Secret**: Must be at least 32 characters for HS256. Store in environment variables, never in code.
2. **Client Secret**: Hashed with BCrypt before storage. Never transmitted in responses.
3. **Token Expiration**: Default 1 hour. Configure via `token-expiration-seconds`.
4. **Revocation**: Technical users can be revoked to prevent token issuance.
5. **HTTPS**: Always use HTTPS in production.

## Testing

Run unit tests:
```bash
./gradlew test
```

Test classes:
- `TechnicalUserServiceTest`: Credential validation, account creation
- `TokenServiceTest`: Token generation and validation

## Database Schema

### technical_users
```sql
CREATE TABLE technical_users (
    id UUID PRIMARY KEY,
    client_id VARCHAR(255) UNIQUE NOT NULL,
    client_secret_hash VARCHAR(255) NOT NULL,
    owner_customer_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);
```

### technical_user_scopes
```sql
CREATE TABLE technical_user_scopes (
    technical_user_id UUID NOT NULL,
    scope VARCHAR(255) NOT NULL,
    FOREIGN KEY (technical_user_id) REFERENCES technical_users(id)
);
```

## Integration with Existing Endpoints

To protect existing endpoints with bearer token validation:

```java
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    
    @GetMapping
    @PreAuthorize("hasAuthority('customer:read')")
    public List<Customer> getCustomers() {
        // Implementation
    }
}
```

## Troubleshooting

**Token validation fails**: Ensure `CYODA_TOKEN_SECRET` is set and matches the secret used to generate the token.

**Client not found**: Verify the client_id exists in the database and is not revoked.

**Invalid secret**: Ensure the plain text secret matches the one used during account creation.

## Future Enhancements

- Refresh token support
- Scope-based authorization with Spring Security
- Token revocation endpoint
- Client credentials rotation
- Rate limiting per client

