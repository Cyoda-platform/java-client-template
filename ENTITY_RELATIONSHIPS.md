# Compliance Management Platform - Entity Relationships

## Entity Dependency Graph

```
Customer (root entity)
├── Account (customerId → Customer.id)
│   └── Transaction (accountId → Account.id)
│       ├── Alert (transactionId → Transaction.id)
│       │   └── Case (relatedCaseId → Case.id)
│       │       └── DocumentEvidence (caseId → Case.id)
│       └── WatchlistEntry (matched via matchedWatchlistIds)
│
└── Alert (customerId → Customer.id)
    └── Case (relatedCaseId → Case.id)
        └── DocumentEvidence (caseId → Case.id)
```

---

## Entity Reference Fields

| Entity | References | Field Name | Target Entity |
|--------|-----------|-----------|----------------|
| Account | Customer | customerId | Customer.id |
| Transaction | Account | accountId | Account.id |
| Transaction | Account (optional) | fromAccountId | Account.id |
| Transaction | Account (optional) | toAccountId | Account.id |
| Transaction | WatchlistEntry | matchedWatchlistIds | WatchlistEntry.id |
| Alert | Transaction (optional) | transactionId | Transaction.id |
| Alert | Customer (optional) | customerId | Customer.id |
| Alert | Case (optional) | relatedCaseId | Case.id |
| Case | Alert | alerts | Alert.id (List) |
| Case | DocumentEvidence | evidences | DocumentEvidence.id (List) |
| DocumentEvidence | Case | caseId | Case.id |

---

## Data Flow for Compliance Workflows

### KYC Onboarding Flow
```
Customer (PENDING) → Customer (VERIFIED) → Account (ACTIVE)
```

### Transaction Monitoring Flow
```
Transaction (PENDING) → Transaction (COMPLETED)
  ↓ (if risk detected)
Alert (OPEN) → Alert (IN_REVIEW) → Case (OPEN) → Case (RESOLVED)
  ↓ (if evidence needed)
DocumentEvidence (uploaded)
```

### Watchlist Screening Flow
```
Transaction → WatchlistEntry (matched)
  ↓ (if match found)
Alert (WATCHLIST_HIT) → Case (ESCALATED)
```

---

## Validation Rules

### Customer
- `id` must be unique (business identifier)
- `legalName` required for all customers
- `dob` required for INDIVIDUAL type
- At least one address required

### Account
- `customerId` must reference existing Customer
- `accountNumber` must be unique per customer
- `status` transitions: ACTIVE → INACTIVE → CLOSED

### Transaction
- `accountId` must reference existing Account
- `amount` must be > 0
- `timestamp` must be ≤ current time
- `fromAccountId` and `toAccountId` required for TRANSFER type

### Alert
- `alertType` must be one of: WATCHLIST_HIT, VELOCITY_EXCEEDED, UNUSUAL_ACTIVITY, etc.
- `severity` determines escalation path
- `status` transitions: OPEN → IN_REVIEW → ESCALATED/CLOSED

### Case
- `title` required and unique
- `createdBy` must be valid username
- `status` transitions: OPEN → IN_PROGRESS → RESOLVED/ESCALATED
- `auditTrail` auto-populated on state changes

### DocumentEvidence
- `caseId` must reference existing Case
- `checksum` must be SHA-256 format
- `sizeBytes` must match actual file size
- `uploadedBy` must be valid username

### WatchlistEntry
- `source` determines matching rules (OFAC, EU_SANCTIONS, CUSTOM)
- `riskLevel` determines alert severity
- `identifiers` required for matching (passport, SSN, etc.)

---

## Retention Policies

| Entity | Retention | Jurisdiction |
|--------|-----------|--------------|
| Customer | 7 years | US/EU |
| Account | 7 years | US/EU |
| Transaction | 5 years | US/EU |
| Alert | 3 years | US/EU |
| Case | 7 years | US/EU |
| DocumentEvidence | 7 years | US/EU |
| WatchlistEntry | 1 year | US/EU |

---

## Search Patterns

### By Customer
```
GET /ui/customer/business/{id}
GET /ui/customer/{technicalId}
```

### By Account
```
GET /ui/account/business/{accountNumber}
GET /ui/account/search?customerId=CUST-001
```

### By Transaction
```
GET /ui/transaction/business/{id}
GET /ui/transaction/search?accountId=ACC-001&status=COMPLETED
```

### By Alert
```
GET /ui/alert/search?severity=HIGH&status=OPEN
GET /ui/alert/search?customerId=CUST-001
```

### By Case
```
GET /ui/case/search?status=OPEN&createdBy=analyst_john
GET /ui/case/search?severity=HIGH
```

