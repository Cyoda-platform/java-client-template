# Compliance Management Platform

A comprehensive workflow-driven Spring Boot application for managing KYC/AML compliance, transaction monitoring, alert management, and regulatory reporting.

## 🏗️ Architecture

### Core Components

**Entities (7):**
- Customer: KYC customer profiles with risk levels
- Account: Customer bank accounts with balances
- Transaction: Financial transactions with risk scoring
- WatchlistEntry: OFAC/PEP watchlist entries
- Alert: Compliance alerts from transaction monitoring
- Case: Investigation cases for escalated alerts
- DocumentEvidence: Supporting documents for cases

**Workflows (4):**
1. **KYCOnboarding**: Customer identity verification → risk assessment → approval/rejection
2. **TransactionMonitoring**: Transaction ingestion → normalization → rule evaluation → alert generation
3. **AlertReviewAndCaseManagement**: Alert assignment → analyst review → case creation → escalation
4. **RegulatoryReporting**: Report drafting → review → approval → regulatory submission

**Processors (21):**
Implement business logic for workflow transitions with validation, enrichment, and external integrations.

**Criteria (9):**
Evaluate conditions for workflow state transitions (risk thresholds, watchlist hits, etc.).

**Controllers (7):**
REST API endpoints for CRUD operations, search, and workflow management.

## 🚀 Getting Started

### Build
```bash
./gradlew build
```

### Validate Workflows
```bash
./gradlew validateWorkflowImplementations
```

### Run Tests
```bash
./gradlew test
```

## 📋 API Endpoints

All endpoints follow `/ui/{entity}/**` pattern:
- `POST /ui/{entity}` - Create
- `GET /ui/{entity}/{id}` - Get by UUID
- `GET /ui/{entity}/business/{businessId}` - Get by business ID
- `PUT /ui/{entity}/{id}` - Update with optional transition
- `DELETE /ui/{entity}/{id}` - Delete
- `GET /ui/{entity}/search` - Search with pagination
- `POST /ui/{entity}/export` - Stream export

## 🔒 Security & Compliance

- No Java reflection API usage
- Type-safe entity operations
- Immutable processor design
- Comprehensive audit trails
- RFC 7807 error handling
- Point-in-time query support

## 📊 Build Status

✅ Full build successful
✅ All 21 processors validated
✅ All 9 criteria validated
✅ All tests passing
✅ Zero compilation errors

