# Customer Management API - Next Steps

## Completed ✅
- [x] Customer entity with nested Address and KYC components
- [x] AuditLog entity for audit trail tracking
- [x] CustomerDTO and AuditLogDTO with bidirectional mapping
- [x] JSON metadata files for entity registry
- [x] Full build compilation successful

## Recommended Implementation Order

### Phase 1: Repository Layer
Create Spring Data JPA repositories in `src/main/java/com/example/application/repository/`:

```java
// CustomerRepository.java
public interface CustomerRepository extends CrudRepository<Customer, UUID> {
    Optional<Customer> findByEmail(String email);
    List<Customer> findAllBySoftDeletedFalse();
    List<Customer> findByStatus(Customer.CustomerStatus status);
    List<Customer> findByKycKycStatus(Customer.KYCStatus kycStatus);
}

// AuditLogRepository.java
public interface AuditLogRepository extends CrudRepository<AuditLog, UUID> {
    List<AuditLog> findByEntityId(UUID entityId);
    List<AuditLog> findByEntityType(String entityType);
    List<AuditLog> findByAction(AuditLog.AuditAction action);
}
```

### Phase 2: Service Layer
Create business logic services in `src/main/java/com/example/application/service/`:

```java
// CustomerService.java
@Service
public class CustomerService {
    - create(CustomerDTO) - with email uniqueness validation
    - getById(UUID)
    - update(UUID, CustomerDTO)
    - delete(UUID) - soft delete
    - findByEmail(String)
    - listAll(pageable)
    - search(filters)
}

// AuditLogService.java
@Service
public class AuditLogService {
    - create(AuditLogDTO)
    - getByEntityId(UUID)
    - getByEntityType(String)
    - listAll(pageable)
}
```

### Phase 3: REST Controllers
Create controllers in `src/main/java/com/example/application/controller/`:

```java
// CustomerController.java
@RestController
@RequestMapping("/ui/customer")
public class CustomerController {
    POST   /ui/customer              - Create customer
    GET    /ui/customer/{id}         - Get customer by ID
    GET    /ui/customer              - List customers (paginated)
    PUT    /ui/customer/{id}         - Update customer
    DELETE /ui/customer/{id}         - Soft delete customer
    GET    /ui/customer/email/{email} - Find by email
}

// AuditLogController.java
@RestController
@RequestMapping("/ui/audit-log")
public class AuditLogController {
    GET /ui/audit-log/entity/{entityId}  - Get audit logs for entity
    GET /ui/audit-log                    - List all audit logs
}
```

### Phase 4: Workflow Configuration
Create workflow JSON in `src/main/resources/workflow/customer/version_1/`:

```json
{
  "version": "1.0",
  "name": "customer_workflow",
  "initialState": "initial",
  "states": {
    "initial": {
      "transitions": [
        {
          "name": "create_customer",
          "next": "created",
          "manual": false
        }
      ]
    },
    "created": {
      "transitions": [
        {
          "name": "verify_customer",
          "next": "verified",
          "manual": true,
          "processors": [...]
        },
        {
          "name": "suspend_customer",
          "next": "suspended",
          "manual": true
        }
      ]
    }
  }
}
```

### Phase 5: Processors & Criteria
Create workflow processors in `src/main/java/com/example/application/processor/`:

```java
// CustomerVerificationProcessor.java
@Component
public class CustomerVerificationProcessor implements CyodaProcessor {
    - Verify KYC status
    - Update customer status to VERIFIED
    - Create audit log entry
}

// CustomerSuspensionProcessor.java
@Component
public class CustomerSuspensionProcessor implements CyodaProcessor {
    - Mark customer as SUSPENDED
    - Create audit log entry
}
```

### Phase 6: Integration Tests
Create tests in `src/test/java/com/example/application/`:

```java
// CustomerControllerTest.java
- Test POST /ui/customer (create)
- Test GET /ui/customer/{id} (retrieve)
- Test PUT /ui/customer/{id} (update)
- Test DELETE /ui/customer/{id} (soft delete)
- Test email uniqueness validation
- Test pagination

// CustomerServiceTest.java
- Test email uniqueness check
- Test soft delete logic
- Test search/filter operations

// CustomerDTOTest.java
- Test entity to DTO conversion
- Test DTO to entity conversion
- Test null safety
```

## Key Implementation Notes

1. **Email Uniqueness:** Implement at service layer before creating/updating
   ```java
   if (customerRepository.findByEmail(email).isPresent()) {
       throw new DuplicateEmailException();
   }
   ```

2. **Soft Delete:** Filter in list operations
   ```java
   customerRepository.findAllBySoftDeletedFalse()
   ```

3. **Audit Trail:** Create AuditLog entry for each operation
   ```java
   auditLogService.create(new AuditLogDTO(
       entityId, "Customer", "CREATE", userId, LocalDateTime.now(), details
   ));
   ```

4. **Validation:** Use @Valid on controller parameters
   ```java
   @PostMapping
   public ResponseEntity<CustomerDTO> create(@Valid @RequestBody CustomerDTO dto)
   ```

5. **Error Handling:** Return appropriate HTTP status codes
   - 201 Created (POST)
   - 200 OK (GET, PUT)
   - 204 No Content (DELETE)
   - 400 Bad Request (validation errors)
   - 409 Conflict (duplicate email)
   - 404 Not Found (entity not found)

## Build & Test Commands

```bash
# Compile
./gradlew clean compileJava

# Build
./gradlew build

# Run tests
./gradlew test

# Run specific test
./gradlew test --tests CustomerControllerTest

# Run application
./gradlew bootRun
```

## Architecture Compliance Checklist

- [ ] No reflection used
- [ ] No modifications to `src/main/java/com/java_template/common/`
- [ ] All entities implement CyodaEntity
- [ ] All DTOs have bidirectional mapping
- [ ] Controllers use `/ui/{entity}/**` prefix
- [ ] Service layer handles business logic
- [ ] Repository layer uses Spring Data JPA
- [ ] Workflow configuration matches entity names
- [ ] Processors extend CyodaProcessor
- [ ] Criteria extend CyodaCriterion
- [ ] Full build succeeds: `./gradlew build`

