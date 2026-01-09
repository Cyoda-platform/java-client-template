# Compliance Management Platform - Implementation Guide

## Quick Start

All 7 entities have been generated and are ready for workflow, processor, and controller implementation.

### File Locations

**Java Entities:** `src/main/java/com/example/application/entity/{entity_name}/version_1/{EntityName}.java`

**JSON Examples:** `src/main/resources/entity/{entity_name}/version_1/{EntityName}.json`

---

## Next Implementation Steps

### 1. Create Workflow Definitions

Create workflow JSON files in `src/main/resources/workflow/{entity_name}/version_1/{EntityName}.json`

**Example structure:**
```json
{
  "name": "Customer",
  "version": 1,
  "initialState": "initial",
  "states": [
    {"name": "initial", "type": "START"},
    {"name": "pending", "type": "INTERMEDIATE"},
    {"name": "verified", "type": "INTERMEDIATE"},
    {"name": "suspended", "type": "INTERMEDIATE"}
  ],
  "transitions": [
    {
      "from": "initial",
      "to": "pending",
      "name": "create",
      "manual": false
    },
    {
      "from": "pending",
      "to": "verified",
      "name": "verify",
      "manual": true
    }
  ],
  "processors": []
}
```

### 2. Create Processors

Extend `CyodaProcessor` in `src/main/java/com/example/application/processor/{EntityName}Processor.java`

**Key points:**
- Use `@Component` annotation with class name matching workflow processor name
- Implement `process()` method with business logic
- Return `EntityWithMetadata` (cannot modify current entity state)
- Use `EntityService` only for OTHER entities
- Follow ExampleEntityProcessor pattern

### 3. Create Controllers

Extend REST endpoints in `src/main/java/com/example/application/controller/{EntityName}Controller.java`

**Key patterns:**
- Use `/ui/{entity_name}/**` prefix
- Implement CRUD operations (create, read, update, delete)
- Use `EntityService` for all data operations
- Support pagination with `SearchAndRetrievalParams`
- Handle business identifier uniqueness checks
- Support workflow transitions via `transition` parameter

### 4. Create Criteria (Optional)

Implement search criteria in `src/main/java/com/example/application/criterion/{EntityName}Criterion.java`

**Use for:**
- Complex search logic
- Custom filtering rules
- Reusable search patterns

---

## Entity-Specific Implementation Notes

### Customer
- **Workflow:** PENDING → VERIFIED → SUSPENDED (optional)
- **Processor:** KYC verification, risk scoring
- **Controller:** Support business ID lookup by email/phone
- **Search:** By customerType, status, kycLevel

### Account
- **Workflow:** ACTIVE → INACTIVE → CLOSED
- **Processor:** Balance updates, transaction limits
- **Controller:** Support business ID lookup by accountNumber
- **Search:** By customerId, status, accountType

### Transaction
- **Workflow:** PENDING → COMPLETED/FAILED/REVERSED
- **Processor:** Risk scoring, watchlist matching, flag detection
- **Controller:** Support streaming for large exports
- **Search:** By accountId, type, status, amount range

### Alert
- **Workflow:** OPEN → IN_REVIEW → ESCALATED/CLOSED
- **Processor:** Severity calculation, case creation
- **Controller:** Support assignment to analysts
- **Search:** By severity, status, alertType

### Case
- **Workflow:** OPEN → IN_PROGRESS → RESOLVED/ESCALATED
- **Processor:** Audit trail updates, evidence linking
- **Controller:** Support bulk operations on alerts
- **Search:** By status, createdBy, assignees

### DocumentEvidence
- **Workflow:** UPLOADED → VERIFIED → ARCHIVED
- **Processor:** Checksum validation, virus scanning
- **Controller:** Support file download/preview
- **Search:** By caseId, uploadedBy, tags

### WatchlistEntry
- **Workflow:** ACTIVE → INACTIVE (no state machine needed)
- **Processor:** Fuzzy matching, alias expansion
- **Controller:** Support bulk import from OFAC/EU lists
- **Search:** By source, riskLevel, type

---

## Validation Checklist

Before deploying:

- [ ] All workflow JSON files created and valid
- [ ] All processors implement business logic
- [ ] All controllers handle CRUD operations
- [ ] All entities pass `./gradlew clean compileJava`
- [ ] All workflows pass `./gradlew validateWorkflowImplementations`
- [ ] All tests pass `./gradlew test`
- [ ] Full build succeeds `./gradlew build`

---

## Testing Strategy

1. **Unit Tests:** Test entity validation and nested classes
2. **Integration Tests:** Test entity creation, updates, searches
3. **Workflow Tests:** Test state transitions and processors
4. **Controller Tests:** Test REST endpoints and error handling
5. **End-to-End Tests:** Test complete compliance workflows

---

## Performance Considerations

- **Transaction Search:** Use streaming for large result sets (>10k)
- **Customer Search:** Use pagination for UI queries
- **Alert Search:** Index by severity and status for fast filtering
- **Case Search:** Index by createdBy for analyst dashboards
- **DocumentEvidence:** Use lazy loading for file content

---

## Security Considerations

- Validate all business IDs are unique at application level
- Encrypt sensitive fields (SSN, passport numbers) at rest
- Audit all state transitions and case assignments
- Implement RBAC for case assignment and evidence access
- Mask PII in logs and error messages

