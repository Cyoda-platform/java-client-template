# Corporate Loan Management System - Quick Start Guide

## What Was Built

A complete corporate loan management system with workflow-driven state management, including:
- Loan entity with comprehensive business fields
- 6-state workflow (initial → submitted → approved/rejected → active → closed)
- 4 workflow processors for state transitions
- 1 approval criterion for validation
- Full REST API with CRUD operations

## Quick Start

### 1. Build the Project
```bash
./gradlew clean build
```

### 2. Import Workflows
```bash
./gradlew runApp -PmainClass=com.java_template.common.tool.WorkflowImportTool
```

### 3. Start the Application
```bash
./gradlew runApp
```

### 4. Access Swagger UI
Open: http://localhost:8080/swagger-ui/index.html

## API Quick Reference

### Create a Loan
```bash
POST /ui/loan
Content-Type: application/json

{
  "loanId": "LOAN-001",
  "borrowerName": "Acme Corp",
  "borrowerEmail": "contact@acme.com",
  "borrowerPhone": "+1-555-0100",
  "loanAmount": 500000,
  "loanTermMonths": 60,
  "interestRate": 5.5,
  "loanPurpose": "Equipment",
  "loanType": "TERM_LOAN"
}
```

### Get Loan by ID
```bash
GET /ui/loan/{technical-uuid}
```

### Get Loan by Business ID
```bash
GET /ui/loan/business/LOAN-001
```

### Approve Loan
```bash
PUT /ui/loan/{id}?transition=approve_loan
Content-Type: application/json
{...loan data...}
```

### Disburse Loan
```bash
PUT /ui/loan/{id}?transition=disburse_loan
Content-Type: application/json
{...loan data...}
```

### Close Loan
```bash
PUT /ui/loan/{id}?transition=close_loan
Content-Type: application/json
{...loan data...}
```

### List All Loans
```bash
GET /ui/loan?page=0&size=20
```

### Search Loans
```bash
GET /ui/loan/search?borrowerName=Acme
```

### Delete Loan
```bash
DELETE /ui/loan/{id}
```

## Loan Workflow States

```
initial
  ↓ (submit_application - automatic)
submitted
  ├→ (approve_loan - manual) → approved
  │                              ↓ (disburse_loan - manual)
  │                            active
  │                              ↓ (close_loan - manual)
  │                            closed
  │
  └→ (reject_loan - manual) → rejected
```

## Key Features

✅ **Workflow-Driven** - All state changes managed by workflow engine
✅ **Validation** - Multi-level validation (entity, criterion, controller)
✅ **Audit Trail** - Complete history of all changes
✅ **Point-in-Time Queries** - Access historical loan states
✅ **Pagination** - Efficient handling of large datasets
✅ **Error Handling** - RFC 7807 compliant error responses
✅ **Logging** - Comprehensive logging for debugging

## Loan Fields

**Required:**
- loanId (business identifier)
- borrowerName
- loanAmount (> 0)
- loanTermMonths (> 0)
- interestRate (≥ 0)

**Optional:**
- borrowerEmail
- borrowerPhone
- loanPurpose
- loanType
- collateral
- collateralValue
- notes
- approverName
- approverComments

**Auto-Managed:**
- applicationDate
- approvalDate
- disbursementDate
- maturityDate
- createdAt
- updatedAt

## Approval Criteria

Loans must meet these criteria to be approved:
- Loan amount ≤ $1,000,000
- Loan term: 6-360 months
- Interest rate: 0-50%
- Borrower email provided

## File Locations

```
src/main/java/com/java_template/application/
├── entity/loan/version_1/Loan.java
├── processor/
│   ├── LoanApprovalProcessor.java
│   ├── LoanRejectionProcessor.java
│   ├── LoanDisbursementProcessor.java
│   └── LoanClosureProcessor.java
├── criterion/LoanApprovalCriterion.java
└── controller/LoanController.java

src/main/resources/workflow/loan/version_1/Loan.json
```

## Troubleshooting

**Build fails:**
- Ensure Java 21 is installed: `java -version`
- Run: `./gradlew clean build`

**Workflows not imported:**
- Run: `./gradlew runApp -PmainClass=com.java_template.common.tool.WorkflowImportTool`
- Check Cyoda connection settings in `application.yml`

**API returns 404:**
- Ensure application is running: `./gradlew runApp`
- Check endpoint path matches exactly
- Verify loan exists with correct ID

## Next Steps

1. Deploy to production environment
2. Configure Cyoda connection settings
3. Set up monitoring and alerting
4. Add additional business logic as needed
5. Extend with additional entities if required

## Support

For issues or questions, refer to:
- IMPLEMENTATION_SUMMARY.md - Detailed implementation overview
- README.md - Project setup and architecture
- usage-rules.md - Development guidelines

