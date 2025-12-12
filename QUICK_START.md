# Quick Start Guide - Customer Management Application

## Build & Run

### 1. Build the Application
```bash
./gradlew clean build
```
Expected output: `BUILD SUCCESSFUL`

### 2. Run the Application
```bash
java -jar build/libs/app.jar
```
Expected: Application starts on `http://localhost:8080`

### 3. Verify Workflow Implementation
```bash
./gradlew validateWorkflowImplementations
```
Expected: All 8 processors and 1 criterion validated

## API Quick Reference

### Create a Customer
```bash
curl -X POST http://localhost:8080/ui/customers \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+1-555-0123",
    "metadata": {
      "source": "web_registration"
    }
  }'
```

### List Customers
```bash
curl http://localhost:8080/ui/customers?page=0&size=20
```

### Get Customer by UUID
```bash
curl http://localhost:8080/ui/customers/{uuid}
```

### Get Customer by Business ID
```bash
curl http://localhost:8080/ui/customers/business/CUST-001
```

### Update Customer
```bash
curl -X PUT http://localhost:8080/ui/customers/{uuid} \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "name": "Jane Doe",
    "email": "jane@example.com"
  }'
```

### Workflow Transitions

#### Start Onboarding
```bash
curl -X POST http://localhost:8080/ui/customers/{uuid}/onboarding
```

#### Request Verification
```bash
curl -X POST http://localhost:8080/ui/customers/{uuid}/verification
```

#### Skip Verification
```bash
curl -X POST http://localhost:8080/ui/customers/{uuid}/skip-verification
```

#### Activate Customer
```bash
curl -X POST http://localhost:8080/ui/customers/{uuid}/activate
```

#### Suspend Customer
```bash
curl -X POST http://localhost:8080/ui/customers/{uuid}/suspend
```

#### Reinstate Customer
```bash
curl -X POST http://localhost:8080/ui/customers/{uuid}/reinstate
```

#### Request Deactivation
```bash
curl -X POST http://localhost:8080/ui/customers/{uuid}/deactivation
```

#### Terminate Customer
```bash
curl -X POST http://localhost:8080/ui/customers/{uuid}/terminate
```

#### Cancel Termination
```bash
curl -X POST http://localhost:8080/ui/customers/{uuid}/cancel-termination
```

### Search Operations

#### Search by Verification Status
```bash
curl "http://localhost:8080/ui/customers/search/verification?status=SUCCESS"
```

#### Advanced Search
```bash
curl -X POST http://localhost:8080/ui/customers/search/advanced \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John",
    "email": "example.com",
    "verificationStatus": "SUCCESS"
  }'
```

#### Get Change History
```bash
curl http://localhost:8080/ui/customers/{uuid}/changes
```

### Delete Operations

#### Delete by UUID
```bash
curl -X DELETE http://localhost:8080/ui/customers/{uuid}
```

#### Delete by Business ID
```bash
curl -X DELETE http://localhost:8080/ui/customers/business/CUST-001
```

## Project Structure

```
src/main/java/com/java_template/
├── Application.java                          # Main Spring Boot app
├── application/
│   ├── controller/CustomerController.java    # REST endpoints
│   ├── entity/customer/version_1/Customer.java
│   ├── processor/                            # 8 processors
│   │   ├── sendVerificationRequest.java
│   │   ├── grantInitialAccess.java
│   │   ├── notifySupport.java
│   │   ├── scheduleDeactivation.java
│   │   ├── auditReinstate.java
│   │   ├── logVerificationFailure.java
│   │   ├── revokeAccess.java
│   │   └── archiveCustomerData.java
│   └── criterion/checkVerificationResult.java
└── common/                                   # Framework (DO NOT MODIFY)

src/main/resources/
├── entity/customer/version_1/customer.json
├── workflow/customerlifecycle/version_1/CustomerLifecycle.json
└── functional_requirements/customer_management.md
```

## Key Files

| File | Purpose |
|------|---------|
| `BUILD_COMPLETION_SUMMARY.md` | Detailed build summary |
| `DEPLOYMENT_VERIFICATION.md` | Deployment checklist |
| `build/libs/app.jar` | Executable application |
| `src/main/resources/workflow/customerlifecycle/version_1/CustomerLifecycle.json` | Workflow definition |
| `src/main/java/com/java_template/application/controller/CustomerController.java` | REST API |

## Troubleshooting

### Build Fails
```bash
# Clean and rebuild
./gradlew clean build --refresh-dependencies
```

### Application Won't Start
- Check Java version: `java -version` (requires Java 11+)
- Check port 8080 is available
- Review logs for configuration errors

### Workflow Validation Fails
```bash
# Validate specific workflow
./gradlew validateWorkflowImplementations -Pargs="src/main/resources/workflow/customerlifecycle/version_1/CustomerLifecycle.json"
```

## Documentation

- **Functional Requirements**: `src/main/resources/functional_requirements/customer_management.md`
- **Build Summary**: `BUILD_COMPLETION_SUMMARY.md`
- **Deployment Guide**: `DEPLOYMENT_VERIFICATION.md`
- **Architecture Guide**: `.augment-guidelines` (in workspace root)

## Support

For issues or questions:
1. Check the logs: `tail -f build/libs/app.jar`
2. Review functional requirements
3. Check workflow definition in CustomerLifecycle.json
4. Verify all processors are implemented

---

**Status**: ✅ Ready for Production

