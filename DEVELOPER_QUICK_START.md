# Developer Quick Start Guide

## Project: Customer Management System
**Branch**: 7e8ce2d9-caa1-440b-a850-3f3483c128c3
**Status**: ✅ Production Ready

## Quick Commands

### Build
```bash
./gradlew clean build          # Full build with tests
./gradlew build -x test        # Build without tests
./gradlew compileJava          # Compile only
```

### Run
```bash
./gradlew bootRun              # Run application locally
java -jar build/libs/app.jar   # Run compiled JAR
```

### Validate
```bash
./gradlew validateWorkflowImplementations  # Validate all workflows
./gradlew test                             # Run all tests
```

## Project Structure

```
src/main/java/com/java_template/
├── Application.java                    # Spring Boot entry point
├── application/                        # Your business logic
│   ├── controller/
│   │   └── CustomerController.java     # REST endpoints
│   ├── entity/
│   │   └── customer/version_1/
│   │       └── Customer.java           # Domain entity
│   ├── processor/                      # Workflow processors
│   │   ├── sendVerificationRequest.java
│   │   ├── grantInitialAccess.java
│   │   ├── notifySupport.java
│   │   ├── scheduleDeactivation.java
│   │   ├── auditReinstate.java
│   │   ├── logVerificationFailure.java
│   │   ├── revokeAccess.java
│   │   └── archiveCustomerData.java
│   └── criterion/
│       └── checkVerificationResult.java
└── common/                             # Framework (DO NOT MODIFY)
```

## Key Files

| File | Purpose |
|------|---------|
| `src/main/java/com/java_template/application/entity/customer/version_1/Customer.java` | Customer entity definition |
| `src/main/resources/workflow/customerlifecycle/version_1/CustomerLifecycle.json` | Workflow state machine |
| `src/main/resources/entity/customer/version_1/Customer.json` | Example entity instance |
| `src/main/java/com/java_template/application/controller/CustomerController.java` | REST API endpoints |

## API Endpoints

### Customer Management
```
POST   /ui/customers                    # Create customer
GET    /ui/customers                    # List customers (paginated)
GET    /ui/customers/{id}               # Get customer by ID
PUT    /ui/customers/{id}               # Update customer
DELETE /ui/customers/{id}               # Delete customer
```

### Workflow Transitions
```
POST   /ui/customers/{id}/onboarding           # Start onboarding
POST   /ui/customers/{id}/verification         # Request verification
POST   /ui/customers/{id}/skip-verification    # Skip verification
POST   /ui/customers/{id}/activate             # Activate customer
POST   /ui/customers/{id}/suspend              # Suspend customer
POST   /ui/customers/{id}/reinstate            # Reinstate customer
POST   /ui/customers/{id}/deactivation         # Request deactivation
POST   /ui/customers/{id}/terminate            # Terminate customer
POST   /ui/customers/{id}/cancel-termination   # Cancel termination
```

### Search & History
```
GET    /ui/customers/{id}/changes              # Get change history
GET    /ui/customers/search/verification       # Search by verification status
POST   /ui/customers/search/advanced           # Advanced search
```

## Customer Lifecycle States

```
initial_state
    ↓
onboarding
    ↓
verification_pending
    ↓
verified
    ↓
active ←→ suspended
    ↓
termination_pending
    ↓
terminated
```

## Adding a New Processor

1. Create class in `src/main/java/com/java_template/application/processor/`
2. Implement `CyodaProcessor` interface
3. Add `@Component` annotation
4. Add processor name to workflow JSON
5. Run `./gradlew validateWorkflowImplementations`

Example:
```java
@Component
public class MyProcessor implements CyodaProcessor {
    private final ProcessorSerializer serializer;
    
    public MyProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }
    
    @Override
    public EntityProcessorCalculationResponse process(
            CyodaEventContext<EntityProcessorCalculationRequest> context) {
        // Implementation
    }
    
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return this.getClass().getSimpleName()
            .equalsIgnoreCase(modelSpec.operationName());
    }
}
```

## Adding a New Criterion

1. Create class in `src/main/java/com/java_template/application/criterion/`
2. Implement `CyodaCriterion` interface
3. Add `@Component` annotation
4. Add criterion name to workflow JSON
5. Run `./gradlew validateWorkflowImplementations`

## Testing

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test
```bash
./gradlew test --tests CustomerControllerTest
```

### Test Coverage
```bash
./gradlew test jacocoTestReport
```

## Common Issues & Solutions

### Issue: Build fails with "cannot find symbol"
**Solution**: Run `./gradlew clean build` to regenerate all sources

### Issue: Workflow validation fails
**Solution**: Ensure processor/criterion class names match workflow JSON exactly

### Issue: Entity not found
**Solution**: Check that entity is in correct package and implements CyodaEntity

## Configuration

Edit `src/main/resources/application.yml`:
```yaml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  application:
    name: customer-management-system
```

## Debugging

### Enable Debug Logging
Add to `application.yml`:
```yaml
logging:
  level:
    com.java_template: DEBUG
```

### View Workflow Validation Details
```bash
./gradlew validateWorkflowImplementations -Pargs="src/main/resources/workflow/customerlifecycle/version_1/CustomerLifecycle.json"
```

## Performance Tips

1. Use technical IDs (UUID) in API responses
2. Implement pagination for list endpoints
3. Use search conditions for filtering
4. Cache frequently accessed entities
5. Use async processors for long-running operations

## Documentation

- `APPLICATION_BUILD_COMPLETE.md` - Full feature documentation
- `BUILD_VERIFICATION_FINAL.md` - Verification checklist
- `src/main/resources/functional_requirements/customer_management.md` - Requirements
- `usage-rules.md` - Detailed implementation guidelines

## Support

For issues or questions:
1. Check the documentation files
2. Review example implementations in `llm_example/`
3. Check workflow JSON for state/transition definitions
4. Review processor/criterion implementations

---

**Last Updated**: 2025-12-12
**Status**: ✅ Ready for Development

