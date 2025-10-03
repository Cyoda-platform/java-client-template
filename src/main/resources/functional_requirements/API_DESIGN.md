# REST API Design Pattern

All entity controllers follow a consistent RESTful API pattern that ensures data integrity, supports both technical and business identifiers, and integrates seamlessly with Cyoda's workflow engine.

Controllers and endpoints must be fully documented with SpringDoc annotations for automatic Swagger UI generation.

## 1. Core Principles

### Business Key Uniqueness
- Every entity has a business identifier field (e.g., `loanId`, `partyId`, `paymentId`)
- Business keys are **mandatory** - they cannot be null or empty
- Business keys must be unique within their entity type
- Business keys are user-facing, human-readable identifiers
- Technical UUIDs are system-generated and used for internal references

### Dual Identifier Support
- All operations support both technical UUID and business key access
- Technical UUID operations are faster (direct lookup)
- Business key operations are more user-friendly (no need to track UUIDs)

### Idempotency and Data Integrity
- POST operations prevent duplicate creation via business key checking
- Proper HTTP status codes communicate operation outcomes
- Clear error messages guide API consumers

## 2. Standard Endpoint Pattern

Each entity controller implements the following endpoints:

### 2.1. CREATE Operations

**Endpoint:** `POST /api/v1/{entity}`

**Request Body:** Entity object with mandatory business key
```json
{
  "loanId": "LOAN-001",
  "partyId": "PARTY-001",
  "principalAmount": 10000,
  ...
}
```

**Behavior:**
1. Validate business key is non-null and non-empty (mandatory requirement)
2. If business key is null or empty, return `400 Bad Request` with error message
3. Query for existing entity using `entityService.findByBusinessIdOrNull()` with exception handling
4. If duplicate found, return `409 Conflict` with descriptive message
5. If no duplicate, set timestamps (`createdAt`, `updatedAt`)
6. Call `entityService.create()` to persist entity
7. Return `201 Created` with `EntityWithMetadata<T>` response

**CRITICAL - Business ID Duplicate Checking:**

When checking for existing entities by business ID, you MUST handle exceptions gracefully. Use `findByBusinessIdOrNull()` which catches exceptions and returns null.

**Why This Matters:**
- During environment bootstrapping, entity models may be empty (no data exists yet)
- When the model is empty, it doesn't contain the business ID field being searched for
- This causes an exception from the Cyoda backend
- The exception simply means "entity doesn't exist" (because there's no data for that model yet)
- Treating the exception as "entity not found" allows proper bootstrapping

**Correct Pattern:**
```java
// Use findByBusinessIdOrNull which handles exceptions
EntityWithMetadata<Loan> existing = entityService.findByBusinessIdOrNull(
    modelSpec, loan.getLoanId(), "loanId", Loan.class);

if (existing != null) {
    throw new DuplicateEntityException("Loan with loanId '" + loan.getLoanId() + "' already exists");
}
// Continue with creation...
```

**Incorrect Pattern (DO NOT USE):**
```java
// DON'T use findByBusinessId without exception handling
// This will fail during bootstrapping when model is empty
try {
    EntityWithMetadata<Loan> existing = entityService.findByBusinessId(
        modelSpec, loan.getLoanId(), "loanId", Loan.class);
    // This throws exception when model is empty, breaking bootstrapping
} catch (Exception e) {
    // Wrong: treating all exceptions as errors
    throw e;
}
```

**Response Codes:**
- `201 Created` - Entity successfully created
- `409 Conflict` - Business key already exists
- `400 Bad Request` - Business key is null/empty or other validation error

### 2.2. READ Operations

**Get All Entities**
- `GET /api/v1/{entity}` - Returns list of all entities
- Uses `entityService.findAll()`
- Returns `200 OK` with list

**Get by Technical UUID**
- `GET /api/v1/{entity}/{uuid}` - Fastest retrieval method
- Uses `entityService.getById()`
- Returns `200 OK` if found, `404 Not Found` if missing

**Get by Business Key**
- `GET /api/v1/{entity}/business/{businessId}` - User-friendly retrieval
- Uses `entityService.findByBusinessId()`
- Returns `200 OK` if found, `404 Not Found` if missing

### 2.3. UPDATE Operations

**Update by Technical UUID**
- `PUT /api/v1/{entity}/{uuid}?transition=TRANSITION_NAME`
- Fastest update method
- Uses `entityService.update()`
- Supports optional workflow transition parameter
- Returns `200 OK` on success, `400 Bad Request` on error

**Update by Business Key**
- `PUT /api/v1/{entity}/business/{businessId}?transition=TRANSITION_NAME`
- User-friendly update method
- Uses `entityService.updateByBusinessId()`
- Supports optional workflow transition parameter
- Returns `200 OK` on success, `404 Not Found` if entity doesn't exist

### 2.4. DELETE Operations

**Delete by Technical UUID**
- `DELETE /api/v1/{entity}/{uuid}`
- Uses `entityService.deleteById()`
- Returns `204 No Content` on success, `400 Bad Request` on error

## 3. Entity Business Key Mapping

| Entity | Business Key Field | Field Name in Code | Example Value |
|--------|-------------------|-------------------|---------------|
| Loan | Loan ID | `loanId` | "LOAN-001" |
| Party | Party ID | `partyId` | "PARTY-001" |
| Payment | Payment ID | `paymentId` | "PAY-001" |
| PaymentFile | Payment File ID | `paymentFileId` | "FILE-001" |
| Accrual | Accrual ID | `accrualId` | "ACC-2024-001-001" |
| SettlementQuote | Settlement Quote ID | `settlementQuoteId` | "SQ-001" |
| GLBatch | GL Batch ID | `glBatchId` | "GLB-2024-01" |

## 4. HTTP Status Code Standards

| Operation | Success | Duplicate | Not Found | Validation Error |
|-----------|---------|-----------|-----------|------------------|
| POST (create) | 201 Created | 409 Conflict | N/A | 400 Bad Request |
| GET (by UUID) | 200 OK | N/A | 404 Not Found | 400 Bad Request |
| GET (by business key) | 200 OK | N/A | 404 Not Found | 400 Bad Request |
| PUT (by UUID) | 200 OK | N/A | N/A | 400 Bad Request |
| PUT (by business key) | 200 OK | N/A | 404 Not Found | 404 Not Found |
| DELETE | 204 No Content | N/A | N/A | 400 Bad Request |

### 4.1. Error Response Format

**CRITICAL:** All 400 Bad Request responses MUST include clean error messages from the Cyoda backend without stacktraces.

**Implementation Pattern:**
```java
} catch (Exception e) {
    logger.error("Error description", e);
    String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);
    return ResponseEntity.badRequest().body(errorMessage);
}
```

**Why This Matters:**
- Cyoda backend exceptions are nested (`CompletionException` → `StatusRuntimeException`)
- Raw exceptions expose internal stacktraces and implementation details
- `CyodaExceptionUtil.extractErrorMessage()` extracts only the meaningful error message
- Full exceptions are still logged for debugging
- Clients receive actionable error messages (e.g., "Invalid day count basis. Must be ACT/365F, ACT/360, or ACT/365L. Got: 30/360")

**Return Type Requirement:**
- Methods that can return error messages must use `ResponseEntity<?>` as return type
- This allows returning either the entity type on success or String on error

**Example:**
```java
@GetMapping("/{id}")
public ResponseEntity<?> getLoanById(@PathVariable UUID id) {
    try {
        EntityWithMetadata<Loan> response = loanInteractor.getLoanById(id);
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        logger.error("Error getting loan by ID: {}", id, e);
        String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);
        return ResponseEntity.badRequest().body(errorMessage);
    }
}
```

## 5. Workflow Integration

All update endpoints support optional workflow transitions via query parameter:

```
PUT /api/v1/loan/{uuid}?transition=approve_loan
PUT /api/v1/loan/business/LOAN-001?transition=fund_loan
```

**Transition Behavior:**
- If `transition` parameter is provided, the workflow engine executes the named transition
- Transition must be valid for the entity's current state
- Processors and criteria associated with the transition are executed
- Automated transitions may cascade after the manual transition completes
- If transition fails, the update is rolled back

## 6. Implementation Guidelines

### For New Entity Controllers:

1. **Identify the business key field** in your entity class
2. **Implement POST endpoint** with duplicate checking pattern
3. **Implement GET endpoints** for both UUID and business key
4. **Implement PUT endpoints** for both UUID and business key with transition support
5. **Implement DELETE endpoint** by UUID
6. **Use consistent error handling** and logging
7. **Return proper HTTP status codes** as per standards table
8. **Set timestamps** (`createdAt`, `updatedAt`) in create/update operations

### EntityService Method Selection:

- `create()` - For new entity creation
- `getById()` - For UUID-based retrieval (fastest)
- `findByBusinessId()` - For business key retrieval (user-friendly)
- `update()` - For UUID-based updates (fastest)
- `updateByBusinessId()` - For business key updates (user-friendly)
- `deleteById()` - For entity deletion

### Best Practices:

- **Enforce mandatory business keys** - All entities must have a non-null, non-empty business key annotated with Lombok's `@NonNull`
- Lombok's `@NonNull` generates null-checks in setters and constructors automatically
- Validate business keys in interactors before duplicate checking and throw `IllegalArgumentException` if null or empty
- **Use `findByBusinessIdOrNull()` for duplicate checking** - This handles bootstrapping scenarios where the model is empty
- **Extract clean error messages** - Always use `CyodaExceptionUtil.extractErrorMessage()` for 400 responses
- **Use `ResponseEntity<?>` return type** - For methods that can return either entity or error message
- Log all create, update, and delete operations with relevant identifiers
- Use descriptive error messages that include the business key value
- Leverage `EntityWithMetadata<T>` wrapper for all responses
- Support workflow transitions in all update operations
- Follow RESTful conventions for endpoint naming and HTTP methods

## 7. Controller Implementation Checklist

When implementing a new entity controller, ensure:

- [ ] Controller class annotated with `@RestController`, `@RequestMapping`, `@CrossOrigin`
- [ ] Constructor injection of `EntityService` and `ObjectMapper`
- [ ] Logger instance configured
- [ ] POST endpoint with duplicate checking implemented using `findByBusinessIdOrNull()`
- [ ] GET all endpoint implemented
- [ ] GET by UUID endpoint implemented
- [ ] GET by business key endpoint implemented
- [ ] PUT by UUID endpoint with transition support implemented
- [ ] PUT by business key endpoint with transition support implemented
- [ ] DELETE by UUID endpoint implemented
- [ ] All endpoints use proper HTTP status codes
- [ ] **All catch blocks use `CyodaExceptionUtil.extractErrorMessage()` for 400 responses**
- [ ] **All methods that return errors use `ResponseEntity<?>` return type**
- [ ] All create/update operations set timestamps
- [ ] All operations logged with relevant identifiers
- [ ] Business key field name matches entity class field
- [ ] ModelSpec uses correct entity name and version constants

