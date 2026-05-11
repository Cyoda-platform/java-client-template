# Customer Management API - Implementation Guide

## Quick Start

### Building the Project
```bash
./gradlew clean build
./gradlew validateWorkflowImplementations
```

### Running the Application
```bash
./gradlew bootRun
```

The API will be available at `http://localhost:8080/ui/customers`

## File Structure

```
src/main/java/com/java_template/application/
├── entity/customer/version_1/
│   └── Customer.java                    # Customer entity with validation
├── processor/
│   └── CustomerProcessor.java            # Workflow processor
├── criterion/
│   └── CustomerCriterion.java            # Validation rules
└── controller/
    └── CustomerController.java           # REST endpoints

src/main/resources/
├── entity/Customer/version_1/
│   └── Customer.json                     # Entity schema & example
└── workflow/customer/version_1/
    └── Customer.json                     # State machine definition
```

## Core Concepts

### 1. Entity (Customer.java)
- Implements `CyodaEntity` interface
- **Required fields:** firstName, lastName, email
- **Identifiers:** id (technical UUID), email (business ID)
- **Audit:** createdAt, updatedAt, createdBy, updatedBy
- **Soft Delete:** deleted boolean flag (default: false)

### 2. Processor (CustomerProcessor.java)
- Implements `CyodaProcessor` interface
- **Responsibility:** Update timestamps on entity lifecycle
- **Constraint:** Can ONLY modify returned EntityWithMetadata, NOT update database
- **Registration:** @Component annotation for Spring discovery

### 3. Criterion (CustomerCriterion.java)
- Implements `CyodaCriterion` interface
- **Responsibility:** Validate customer data consistency
- **Checks:** Email format, required fields
- **Registration:** @Component annotation for Spring discovery

### 4. Controller (CustomerController.java)
- REST endpoints following `/ui/customers` pattern
- **Pattern:** No business logic, pure EntityService delegation
- **Error Handling:** RFC 7807 ProblemDetail responses
- **Logging:** Comprehensive operation logging

### 5. Workflow (Customer.json)
- **Initial State:** "initial" (automatic transition)
- **Active State:** "active" (manual transitions)
- **Deleted State:** "deleted" (terminal)

## API Usage Examples

### Create Customer
```bash
curl -X POST http://localhost:8080/ui/customers \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Jane",
    "lastName": "Doe",
    "email": "jane@example.com",
    "phone": "+1-555-0100",
    "address": {
      "line1": "123 Main St",
      "city": "Springfield",
      "state": "IL",
      "country": "USA"
    }
  }'
```

### List Customers (with pagination)
```bash
curl "http://localhost:8080/ui/customers?page=0&size=20&firstName=Jane"
```

### Get Customer by UUID
```bash
curl http://localhost:8080/ui/customers/{uuid}
```

### Update Customer
```bash
curl -X PUT http://localhost:8080/ui/customers/{uuid} \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Jane","lastName":"Smith",...}'
```

### Delete (Soft Delete)
```bash
curl -X DELETE http://localhost:8080/ui/customers/{uuid}
```

## Architecture Patterns

### Search Patterns Used
1. **Duplicate Check:** `entityService.findByBusinessIdOrNull()` on create
2. **Direct Lookup:** `entityService.getById()` for UUID retrieval
3. **Business ID Lookup:** `entityService.findByBusinessId()` for email queries
4. **Paginated Search:** `entityService.search()` with searchId for multi-page navigation

### Transition Rules
- **Creation:** Automatic transition from "initial" to "active"
- **Update:** Manual transition "update" within "active" state
- **Delete:** Manual transition "soft_delete" from "active" to "deleted"

### Soft Delete Pattern
- Flag: `deleted = true` (boolean field)
- List Endpoint: Always filters `deleted = false`
- Retrieval: Can access deleted by direct UUID/email lookup

## Validation & Compliance

✅ **No Reflection:** Only getClass().getSimpleName() for component names
✅ **Directory Isolation:** No modifications to common/ package
✅ **Read-Only Processors:** Return modified EntityWithMetadata, don't update DB
✅ **Type Safety:** List<QueryCondition> for all searches
✅ **Manual Transitions:** entityService.update(id, entity, "transition_name")

## Testing

Run existing test suite:
```bash
./gradlew test
```

All customer endpoints can be tested with standard HTTP clients (curl, Postman, etc.)

## Documentation
See `CUSTOMER_API_IMPLEMENTATION.md` for detailed component specifications.

