# Quick Start - Java Cyoda Application

## ⚡ 5-Minute Setup

### Prerequisites
- Java 21 installed
- Gradle 8.7+ (included via wrapper)
- Docker (optional, for containerization)

### Step 1: Build the Application (30 seconds)
```bash
./gradlew clean build
```
✅ Expected: `BUILD SUCCESSFUL in ~13s`

### Step 2: Validate Workflows (10 seconds)
```bash
./gradlew validateWorkflowImplementations
```
✅ Expected: `ALL WORKFLOW IMPLEMENTATIONS VALIDATED SUCCESSFULLY!`

### Step 3: Run the Application (20 seconds)
```bash
./gradlew runApp
```
✅ Expected: Application starts on `http://localhost:8080`

### Step 4: Access Swagger UI (5 seconds)
Open browser: `http://localhost:8080/swagger-ui/index.html`

---

## 🚀 First API Call

### Create a Customer
```bash
curl -X POST http://localhost:8080/ui/customers \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+1-555-0123"
  }'
```

**Response**: Returns customer with technical UUID and metadata

---

## 📋 Customer Lifecycle Flow

### 1. Create Customer
```bash
POST /ui/customers
```
→ Customer in `initial_state`

### 2. Start Onboarding
```bash
POST /ui/customers/{id}/onboarding
```
→ Customer in `onboarding` state

### 3. Request Verification
```bash
POST /ui/customers/{id}/verification
```
→ Customer in `verification_pending` state
→ Triggers `sendVerificationRequest` processor

### 4. Verify Success (Auto-transition)
→ Customer auto-transitions to `verified` state
→ Triggers `grantInitialAccess` processor

### 5. Activate
```bash
POST /ui/customers/{id}/activate
```
→ Customer in `active` state

### 6. Suspend (if needed)
```bash
POST /ui/customers/{id}/suspend
```
→ Customer in `suspended` state
→ Triggers `notifySupport` processor

### 7. Reinstate
```bash
POST /ui/customers/{id}/reinstate
```
→ Customer back to `active` state

### 8. Terminate
```bash
POST /ui/customers/{id}/terminate
```
→ Customer in `terminated` state

---

## 🔍 Common API Operations

### List Customers
```bash
curl -X GET "http://localhost:8080/ui/customers?page=0&size=20"
```

### Get Customer by ID
```bash
curl -X GET http://localhost:8080/ui/customers/{id}
```

### Search by Email
```bash
curl -X GET "http://localhost:8080/ui/customers?email=example.com"
```

### Search by Verification Status
```bash
curl -X GET "http://localhost:8080/ui/customers/search/verification?status=SUCCESS"
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

### Get Change History
```bash
curl -X GET http://localhost:8080/ui/customers/{id}/changes
```

### Update Customer
```bash
curl -X PUT http://localhost:8080/ui/customers/{id} \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "name": "Jane Doe",
    "email": "jane@example.com"
  }'
```

### Delete Customer
```bash
curl -X DELETE http://localhost:8080/ui/customers/{id}
```

---

## 🐳 Docker Deployment

### Build Docker Image
```bash
docker build -t cyoda-app:latest .
```

### Run Docker Container
```bash
docker run -p 8080:8080 cyoda-app:latest
```

### Access Application
```
http://localhost:8080/swagger-ui/index.html
```

---

## ☸️ Kubernetes Deployment

### Deploy with Helm
```bash
helm install cyoda-app ./helm
```

### Check Status
```bash
kubectl get deployments
kubectl get pods
kubectl get services
```

### Port Forward
```bash
kubectl port-forward svc/cyoda-app 8080:8080
```

### Access Application
```
http://localhost:8080/swagger-ui/index.html
```

---

## 📊 What's Implemented

### Entities (1)
- ✅ Customer - Full lifecycle management

### Workflows (1)
- ✅ CustomerLifecycle - 8 states, 15 transitions

### Processors (8)
- ✅ sendVerificationRequest
- ✅ grantInitialAccess
- ✅ notifySupport
- ✅ scheduleDeactivation
- ✅ logVerificationFailure
- ✅ auditReinstate
- ✅ revokeAccess
- ✅ archiveCustomerData

### Criteria (1)
- ✅ checkVerificationResult

### REST Endpoints (20+)
- ✅ CRUD operations
- ✅ Workflow transitions
- ✅ Search and filtering
- ✅ Change history
- ✅ Advanced queries

---

## 🔧 Troubleshooting

### Build Fails
```bash
./gradlew clean
./gradlew build
```

### Application Won't Start
1. Check port 8080 is available
2. Check Java 21 is installed: `java -version`
3. Check logs for errors

### Workflow Validation Fails
```bash
./gradlew validateWorkflowImplementations
```

### API Returns 404
1. Verify application is running
2. Check endpoint path in Swagger UI
3. Verify request method (GET, POST, PUT, DELETE)

---

## 📚 Documentation

- **IMPLEMENTATION_COMPLETE.md** - Full implementation details
- **TESTING_AND_VALIDATION.md** - Comprehensive testing guide
- **BUILD_COMPLETION_REPORT.md** - Build report and metrics
- **README.md** - General setup
- **usage-rules.md** - Implementation guidelines

---

## ✅ Validation Checklist

- [ ] Build completes successfully
- [ ] Workflow validation passes
- [ ] Application starts without errors
- [ ] Swagger UI is accessible
- [ ] Can create a customer
- [ ] Can retrieve customer by ID
- [ ] Can list customers
- [ ] Can start onboarding
- [ ] Can request verification
- [ ] Can suspend/reinstate customer

---

## 🎯 Next Steps

1. **Explore Swagger UI** - See all available endpoints
2. **Test Customer Lifecycle** - Create and manage customers
3. **Review Logs** - Check processor execution
4. **Deploy to Docker** - Containerize the application
5. **Deploy to Kubernetes** - Use Helm charts

---

## 📞 Support

For detailed information:
- Check `TESTING_AND_VALIDATION.md` for API examples
- Review `IMPLEMENTATION_COMPLETE.md` for architecture
- See `README.md` for general setup

---

**Status**: ✅ Ready to Use  
**Build Date**: 2025-12-12  
**Java Version**: 21  

