# Quick Reference Guide

**Build Status**: ✅ COMPLETE  
**Date**: 2025-12-12

---

## Quick Start

### Build the Application
```bash
./gradlew clean build
```

### Validate Workflows
```bash
./gradlew validateWorkflowImplementations
```

### Run the Application
```bash
./gradlew bootRun
```

### Access Swagger UI
```
http://localhost:8080/swagger-ui/index.html
```

---

## Key Endpoints

### Create Customer
```bash
POST /ui/customers
{
  "customerId": "CUST-001",
  "name": "John Doe",
  "email": "john@example.com",
  "phone": "+1-555-0123"
}
```

### Get Customer
```bash
GET /ui/customers/{id}
GET /ui/customers/business/{customerId}
```

### Update Customer
```bash
PUT /ui/customers/{id}
{
  "name": "Jane Doe",
  "email": "jane@example.com",
  "phone": "+1-555-0124"
}
```

### Workflow Transitions
```bash
# Start onboarding
POST /ui/customers/{id}/onboarding

# Request verification
POST /ui/customers/{id}/verification

# Activate customer
POST /ui/customers/{id}/activate

# Suspend customer
POST /ui/customers/{id}/suspend

# Reinstate customer
POST /ui/customers/{id}/reinstate

# Terminate customer
POST /ui/customers/{id}/terminate
```

### Search
```bash
# List with pagination
GET /ui/customers?page=0&size=10

# Search by verification status
GET /ui/customers/search/verification?status=SUCCESS

# Advanced search
POST /ui/customers/search/advanced
{
  "conditions": [...]
}
```

---

## Project Structure

```
src/main/java/com/java_template/
├── Application.java
├── application/
│   ├── controller/
│   │   ├── CustomerController.java
│   │   ├── OrderController.java
│   │   └── ProductController.java
│   ├── entity/
│   │   ├── customer/version_1/Customer.java
│   │   ├── order/version_1/Order.java
│   │   └── product/version_1/Product.java
│   ├── processor/ (14 processors)
│   └── criterion/ (2 criteria)
└── common/ (Framework - DO NOT MODIFY)

src/main/resources/
├── entity/
│   ├── customer/version_1/customer.json
│   ├── order/version_1/order.json
│   └── product/version_1/product.json
├── workflow/
│   ├── customerlifecycle/version_1/CustomerLifecycle.json
│   ├── order/version_1/Order.json
│   └── product/version_1/Product.json
└── application.yml
```

---

## Customer Lifecycle States

1. **initial_state** - New customer
2. **onboarding** - Onboarding in progress
3. **verification_pending** - Awaiting verification
4. **verified** - Verification successful
5. **active** - Fully active customer
6. **suspended** - Temporarily suspended
7. **termination_pending** - Scheduled for termination
8. **terminated** - Final state

---

## Processors

### Customer Processors (8)
- `sendVerificationRequest` - External verification
- `grantInitialAccess` - Initial permissions
- `notifySupport` - Support notifications
- `scheduleDeactivation` - Deactivation scheduling
- `logVerificationFailure` - Failure logging
- `auditReinstate` - Reinstatement auditing
- `revokeAccess` - Access revocation
- `archiveCustomerData` - Data archival

### Order Processors (4)
- `PersistOrder` - Order persistence
- `ChargePayment` - Payment processing
- `ValidateOrder` - Order validation
- `UpdateInventory` - Inventory updates

### Notification Processors (2)
- `notifyOrderShipped` - Shipment notifications
- `notifyOrderDelivered` - Delivery notifications

---

## Criteria

- `checkVerificationResult` - Verification status evaluation
- `validateOrderStatus` - Order status validation

---

## Important Files

| File | Purpose |
|------|---------|
| `src/main/java/com/java_template/application/entity/customer/version_1/Customer.java` | Customer entity |
| `src/main/resources/workflow/customerlifecycle/version_1/CustomerLifecycle.json` | Workflow definition |
| `src/main/java/com/java_template/application/controller/CustomerController.java` | REST endpoints |
| `src/main/java/com/java_template/application/processor/` | Business logic |
| `src/main/java/com/java_template/application/criterion/` | Evaluation logic |

---

## Validation Commands

```bash
# Full build and test
./gradlew clean build

# Workflow validation
./gradlew validateWorkflowImplementations

# Run application
./gradlew bootRun

# Run specific test
./gradlew test --tests "TestClassName"
```

---

## Common Issues

### Build Fails
```bash
./gradlew clean build
```

### Workflow Validation Fails
```bash
./gradlew validateWorkflowImplementations -Pargs="src/main/resources/workflow/customerlifecycle/version_1/CustomerLifecycle.json"
```

### Application Won't Start
- Check `.env` file for Cyoda backend configuration
- Verify Java 21 is installed
- Check port 8080 is available

---

## Documentation

- **BUILD_COMPLETION_FINAL.md** - Build summary
- **FINAL_BUILD_VALIDATION.md** - Validation report
- **APPLICATION_BUILD_SUMMARY.md** - What was built
- **IMPLEMENTATION_COMPLETE.md** - Implementation details
- **README.md** - General setup
- **usage-rules.md** - Implementation guidelines

---

## Support

1. Check `README.md` for general setup
2. Review `usage-rules.md` for implementation guidelines
3. Check `llm_example/` directory for code patterns
4. Review functional requirements in `src/main/resources/functional_requirements/`

---

**Status**: ✅ COMPLETE AND VALIDATED  
**Java Version**: 21  
**Spring Boot**: 3.5.3  
**Gradle**: 8.7

