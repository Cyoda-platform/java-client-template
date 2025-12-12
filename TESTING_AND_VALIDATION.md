# Testing and Validation Guide

## Build Verification

### 1. Clean Build
```bash
./gradlew clean build
```
**Expected Output**: `BUILD SUCCESSFUL in ~13s`

### 2. Workflow Validation
```bash
./gradlew validateWorkflowImplementations
```
**Expected Output**:
```
✅ ALL WORKFLOW IMPLEMENTATIONS VALIDATED SUCCESSFULLY!
Workflow files checked: 2
Total processors referenced: 10
Total criteria referenced: 1
Available processor classes: 10
Available criterion classes: 2
```

### 3. Compilation Check
```bash
./gradlew clean compileJava
```
**Expected Output**: `BUILD SUCCESSFUL` with no errors

## Running the Application

### Start the Application
```bash
./gradlew runApp
```

### Access Swagger UI
```
http://localhost:8080/swagger-ui/index.html
```

## API Testing Examples

### 1. Create a Customer
```bash
curl -X POST http://localhost:8080/ui/customers \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1-555-0123",
    "metadata": {
      "source": "web_registration"
    }
  }'
```

### 2. Get Customer by ID
```bash
curl -X GET http://localhost:8080/ui/customers/{id}
```

### 3. List All Customers
```bash
curl -X GET "http://localhost:8080/ui/customers?page=0&size=20"
```

### 4. Start Onboarding
```bash
curl -X POST http://localhost:8080/ui/customers/{id}/onboarding
```

### 5. Request Verification
```bash
curl -X POST http://localhost:8080/ui/customers/{id}/verification
```

### 6. Suspend Customer
```bash
curl -X POST http://localhost:8080/ui/customers/{id}/suspend
```

### 7. Reinstate Customer
```bash
curl -X POST http://localhost:8080/ui/customers/{id}/reinstate
```

### 8. Request Deactivation
```bash
curl -X POST http://localhost:8080/ui/customers/{id}/deactivation
```

### 9. Terminate Customer
```bash
curl -X POST http://localhost:8080/ui/customers/{id}/terminate
```

### 10. Search by Verification Status
```bash
curl -X GET "http://localhost:8080/ui/customers/search/verification?status=SUCCESS"
```

### 11. Advanced Search
```bash
curl -X POST http://localhost:8080/ui/customers/search/advanced \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John",
    "email": "example.com",
    "verificationStatus": "SUCCESS"
  }'
```

### 12. Get Change History
```bash
curl -X GET http://localhost:8080/ui/customers/{id}/changes
```

### 13. Update Customer
```bash
curl -X PUT http://localhost:8080/ui/customers/{id} \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "name": "Jane Doe",
    "email": "jane.doe@example.com",
    "phone": "+1-555-0124"
  }'
```

### 14. Delete Customer
```bash
curl -X DELETE http://localhost:8080/ui/customers/{id}
```

## Workflow State Transitions

### Customer Lifecycle Flow
```
initial_state
    ↓
    ├─→ start_onboarding → onboarding
    │                         ↓
    │                    request_verification → verification_pending
    │                         ↓
    │                    verification_success → verified
    │                         ↓
    │                    activate → active
    │
    └─→ cancel → terminated

active
    ├─→ suspend → suspended
    │               ├─→ reinstate → active
    │               └─→ terminate → terminated
    ├─→ update_details → active (loop-back)
    └─→ request_deactivation → termination_pending
                                    ├─→ complete_termination → terminated
                                    └─→ cancel_termination → active

verification_pending
    ├─→ verification_success → verified
    ├─→ verification_failed → onboarding
    └─→ cancel → terminated

verified
    ├─→ activate → active
    └─→ flag_for_review → suspended

onboarding
    ├─→ request_verification → verification_pending
    ├─→ skip_verification → active
    └─→ cancel → terminated
```

## Processor Execution Verification

### Check Processor Logs
When a transition is triggered, check application logs for:
```
Processing {ProcessorName} for request: {requestId}
```

### Verify Processor Execution
1. **sendVerificationRequest**: Sets verification status to PENDING
2. **grantInitialAccess**: Sets accessGranted=true in metadata
3. **notifySupport**: Logs support notification
4. **scheduleDeactivation**: Schedules deactivation task
5. **logVerificationFailure**: Logs failure details
6. **auditReinstate**: Logs reinstatement audit
7. **revokeAccess**: Revokes customer access
8. **archiveCustomerData**: Archives customer data

## Criterion Evaluation Verification

### checkVerificationResult Criterion
- Evaluates when in `verification_pending` state
- Checks if `verification.status == "SUCCESS"`
- Auto-transitions to `verified` if true
- Returns to `onboarding` if false

## Docker Testing

### Build Docker Image
```bash
docker build -t cyoda-customer-app:latest .
```

### Run Docker Container
```bash
docker run -p 8080:8080 cyoda-customer-app:latest
```

### Access Application
```
http://localhost:8080/swagger-ui/index.html
```

## Kubernetes Deployment

### Deploy with Helm
```bash
helm install cyoda-app ./helm
```

### Check Deployment Status
```bash
kubectl get deployments
kubectl get pods
kubectl get services
```

### Access Application
```bash
kubectl port-forward svc/cyoda-app 8080:8080
```

## Troubleshooting

### Build Fails
1. Ensure Java 21 is installed: `java -version`
2. Clean gradle cache: `./gradlew clean`
3. Rebuild: `./gradlew build`

### Workflow Validation Fails
1. Check processor class names match workflow JSON
2. Verify all processors implement `CyodaProcessor`
3. Verify all criteria implement `CyodaCriterion`
4. Run: `./gradlew validateWorkflowImplementations`

### Application Won't Start
1. Check port 8080 is available
2. Check logs for errors
3. Verify Cyoda backend is accessible
4. Check `.env` configuration

### API Endpoints Return 404
1. Verify application is running
2. Check endpoint path matches controller mapping
3. Verify request method (GET, POST, PUT, DELETE)
4. Check request body format for POST/PUT

## Performance Testing

### Load Test Customer Creation
```bash
for i in {1..100}; do
  curl -X POST http://localhost:8080/ui/customers \
    -H "Content-Type: application/json" \
    -d "{\"customerId\": \"CUST-$i\", \"name\": \"Customer $i\", \"email\": \"cust$i@example.com\"}"
done
```

### Measure Response Time
```bash
time curl -X GET http://localhost:8080/ui/customers?page=0&size=20
```

## Validation Checklist

- [ ] Build completes successfully
- [ ] Workflow validation passes
- [ ] Application starts without errors
- [ ] Swagger UI is accessible
- [ ] Can create a customer
- [ ] Can retrieve customer by ID
- [ ] Can list customers with pagination
- [ ] Can start onboarding
- [ ] Can request verification
- [ ] Can suspend/reinstate customer
- [ ] Can terminate customer
- [ ] Can search customers
- [ ] Can view change history
- [ ] Docker image builds successfully
- [ ] Docker container runs successfully
- [ ] Helm deployment works

---

**Last Updated**: 2025-12-12
**Status**: Ready for Testing

