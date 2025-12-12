# Customer Management Application - Quick Start Guide

## Overview
This is a fully functional Customer Management System built with Spring Boot and Cyoda framework. It manages customers through their complete lifecycle: onboarding, verification, active management, suspension, and termination.

## Prerequisites
- Java 17 or higher
- Gradle 8.7 (included via gradlew)

## Quick Start

### 1. Build the Application
```bash
./gradlew clean build
```
This will:
- Compile all Java sources
- Run all tests
- Generate the executable JAR file
- Validate workflow implementations

**Expected Output**: `BUILD SUCCESSFUL`

### 2. Run the Application
```bash
java -jar build/libs/app.jar
```

The application will start on `http://localhost:8080`

### 3. Access the API
The API is available at: `http://localhost:8080/ui/customers`

## API Examples

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

### List All Customers
```bash
curl http://localhost:8080/ui/customers
```

### Get Customer by ID
```bash
curl http://localhost:8080/ui/customers/{id}
```

### Start Onboarding
```bash
curl -X POST http://localhost:8080/ui/customers/{id}/onboarding
```

### Request Verification
```bash
curl -X POST http://localhost:8080/ui/customers/{id}/verification
```

### Suspend Customer
```bash
curl -X POST http://localhost:8080/ui/customers/{id}/suspend
```

### Reinstate Customer
```bash
curl -X POST http://localhost:8080/ui/customers/{id}/reinstate
```

### Request Deactivation
```bash
curl -X POST http://localhost:8080/ui/customers/{id}/deactivation
```

### Search by Verification Status
```bash
curl "http://localhost:8080/ui/customers/search/verification?status=SUCCESS"
```

### Advanced Search
```bash
curl -X POST http://localhost:8080/ui/customers/search/advanced \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John",
    "email": "example.com",
    "verificationStatus": "SUCCESS"
  }'
```

## Customer Lifecycle States

1. **initial_state** - Customer created, awaiting onboarding
2. **onboarding** - Customer in onboarding process
3. **verification_pending** - Verification request sent to provider
4. **verified** - Customer verified by provider
5. **active** - Customer fully activated and using service
6. **suspended** - Customer account suspended
7. **termination_pending** - Deactivation scheduled
8. **terminated** - Customer account terminated (final state)

## Key Features

### Workflow-Driven Architecture
- All state transitions managed through workflow definitions
- Automatic transitions for verification and activation
- Manual transitions for user-initiated actions
- Processor-based business logic execution

### Entity Management
- Technical ID (UUID) for performance
- Business ID (customerId) for user-facing operations
- Metadata support for extensibility
- Verification tracking with provider integration

### Search & Filtering
- Pagination support
- Filter by state, email, name
- Advanced search with multiple criteria
- Point-in-time queries for audit trails

### Validation
- Server-side validation of required fields
- Email format validation
- Unique constraint checking for customerId
- Entity state validation

## Project Structure
```
src/main/java/com/java_template/
├── Application.java                    # Main Spring Boot app
├── application/
│   ├── controller/CustomerController.java
│   ├── entity/customer/version_1/Customer.java
│   ├── processor/ (8 processors)
│   └── criterion/checkVerificationResult.java
└── common/ (Framework - DO NOT MODIFY)

src/main/resources/
├── entity/customer/version_1/customer.json
├── workflow/customerlifecycle/version_1/CustomerLifecycle.json
└── functional_requirements/customer_management.md
```

## Validation Commands

### Validate Workflow Implementations
```bash
./gradlew validateWorkflowImplementations
```

### Run Tests
```bash
./gradlew test
```

### Check Build Status
```bash
./gradlew build
```

## Troubleshooting

### Build Fails
```bash
./gradlew clean build --stacktrace
```

### Tests Fail
```bash
./gradlew test --info
```

### Application Won't Start
- Check Java version: `java -version` (requires Java 17+)
- Check port 8080 is available
- Review logs in console output

## Documentation

- **Functional Requirements**: `src/main/resources/functional_requirements/customer_management.md`
- **Workflow Definition**: `src/main/resources/workflow/customerlifecycle/version_1/CustomerLifecycle.json`
- **Build Summary**: `BUILD_COMPLETION_SUMMARY.md`
- **Implementation Verification**: `IMPLEMENTATION_VERIFICATION.md`

## Support

For issues or questions:
1. Check the logs in console output
2. Review the functional requirements document
3. Examine the workflow definition JSON
4. Check the processor implementations

## Status

✅ **Application is fully implemented, tested, and ready for deployment**

All 8 processors and 1 criterion have been implemented and validated.
The application compiles successfully with no errors.
All functional requirements have been met.

