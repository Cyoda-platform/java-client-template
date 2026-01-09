# Workflows Technical Reference

## Workflow Architecture Overview

All workflows follow the Cyoda state machine pattern with:
- **Automatic transitions** for initial creation and deterministic paths
- **Manual transitions** for all updates, approvals, and escalations
- **Conditional routing** using simple JSON path criteria
- **Processor chaining** for multi-step operations
- **Audit logging** on every state change

## Processor Implementation Requirements

### KYCOnboarding Processors

| Processor | Type | Purpose | Input | Output |
|-----------|------|---------|-------|--------|
| validatePayload | Externalized | Validate customer data structure | Customer entity | Validation result |
| storeDocuments | Externalized | Persist uploaded documents | DocumentEvidence list | Document IDs |
| callKYCProvider | Externalized | Call third-party KYC API | Customer data | Verification status |
| computeRiskScore | Externalized | Calculate risk score (0-100) | Customer metadata | Risk score |
| createCaseOnHighRisk | Externalized | Create compliance case | Customer + risk score | Case ID |

### TransactionMonitoring Processors

| Processor | Type | Purpose | Input | Output |
|-----------|------|---------|-------|--------|
| normalizeTransaction | Externalized | Standardize transaction format | Raw transaction | Normalized transaction |
| evaluateRulesEngine | Externalized | Apply AML rules | Transaction + rules | Rule matches |
| fuzzyWatchlistMatch | Externalized | Match against watchlists | Transaction + watchlists | Match score |
| computeRiskScore | Externalized | Calculate transaction risk | Transaction metadata | Risk score (0-100) |
| createAlert | Externalized | Generate alert entity | Transaction + score | Alert ID |
| holdFunds | Externalized | Place transaction hold | Transaction ID | Hold confirmation |

### AlertReviewAndCaseManagement Processors

| Processor | Type | Purpose | Input | Output |
|-----------|------|---------|-------|--------|
| assignAnalyst | Externalized | Auto-assign to available analyst | Alert + workload | Analyst ID |
| enrichWithCustomerHistory | Externalized | Fetch customer transaction history | Customer ID | History data |
| attachEvidence | Externalized | Link evidence documents | Alert + documents | Evidence links |
| updateAlertStatus | Externalized | Update alert status | Alert + new status | Status confirmation |
| createCase | Externalized | Create SAR/STR case | Alert + evidence | Case ID |
| generateSARReport | Externalized | Generate SAR report | Case data | Report document |

### RegulatoryReporting Processors

| Processor | Type | Purpose | Input | Output |
|-----------|------|---------|-------|--------|
| collectReportData | Externalized | Aggregate report data | Date range + filters | Report dataset |
| renderTemplate | Externalized | Render jurisdiction template | Dataset + jurisdiction | Rendered report |
| signAndHashExport | Externalized | Sign and hash report | Report document | Signed + hash |
| submitToRegulator | Externalized | Submit to regulatory API | Signed report | Submission ID |

## Transition Conditions

### KYCOnboarding Conditions

**Risk Score Threshold (riskScoring state):**
```json
{
  "type": "simple",
  "jsonPath": "$.metadata.riskScore",
  "operation": "GREATER_THAN_OR_EQUALS",
  "value": 70
}
```
- Score ≥ 70: Route to manualReview
- Score < 70: Auto-approve

### TransactionMonitoring Conditions

**Alert Threshold (scoreEvaluation state):**
```json
{
  "type": "simple",
  "jsonPath": "$.score",
  "operation": "GREATER_THAN_OR_EQUALS",
  "value": 75
}
```
- Score ≥ 75: Create alert + hold funds
- Score < 75: No action, archive

## State Transition Matrices

### KYCOnboarding
```
started → documentVerification (auto)
documentVerification → identityVerification (auto) | rejected (manual)
identityVerification → riskScoring (auto) | rejected (manual)
riskScoring → manualReview (auto, score≥70) | approved (auto, score<70)
manualReview → approved (manual) | suspended (manual) | rejected (manual)
suspended → manualReview (manual)
approved, rejected → [terminal]
```

### TransactionMonitoring
```
ingest → normalize (auto)
normalize → ruleEvaluation (auto)
ruleEvaluation → analyticsEnrichment (auto)
analyticsEnrichment → scoreEvaluation (auto)
scoreEvaluation → alertCreated (auto, score≥75) | noAction (auto, score<75)
alertCreated → closed (manual)
noAction → closed (auto)
closed → [terminal]
```

### AlertReviewAndCaseManagement
```
created → assigned (auto)
assigned → inReview (manual)
inReview → escalated (manual) | resolved (manual)
escalated → resolved (manual) | closed (manual)
resolved → closed (auto)
closed → [terminal]
```

### RegulatoryReporting
```
draft → review (manual)
review → approved (manual) | draft (manual)
approved → submitted (manual)
submitted → archived (auto)
archived → [terminal]
```

## Compliance & Audit

### Audit Fields Captured
- **actor:** User/system performing action
- **timestamp:** ISO 8601 timestamp
- **from:** Source state
- **to:** Target state
- **reason:** Transition reason/notes

### Retention Policy
- **Default:** 2555 days (7 years)
- **Jurisdiction:** US/EU compliant
- **Archival:** Automatic after terminal state

## Performance Considerations

### Pilot Scale (10k tx/day)
- **TransactionMonitoring:** ~0.12 tx/sec average
- **Peak handling:** 2-3x average (0.3-0.4 tx/sec)
- **Processor timeout:** 30 seconds recommended
- **Batch size:** 100-500 transactions

### Scaling Recommendations
1. Implement async processors for external API calls
2. Use message queues for transaction ingest
3. Cache watchlist data with 1-hour TTL
4. Implement circuit breakers for KYC provider
5. Use database connection pooling (min: 10, max: 50)

## Integration Points

### External Systems
- **KYC Provider:** REST API integration
- **Watchlist Services:** OFAC, EU sanctions feeds
- **Regulatory Submission:** FINRA/FinCEN APIs
- **Document Storage:** S3/Azure Blob
- **Audit Log:** Elasticsearch/Splunk

### Entity References
- Customer (KYC, transaction enrichment)
- Transaction (monitoring, scoring)
- Alert (case creation, escalation)
- Case (evidence attachment, reporting)
- DocumentEvidence (KYC, case management)
- WatchlistEntry (transaction screening)

---

**Last Updated:** 2026-01-09  
**Schema Version:** 1.0  
**Compliance:** GDPR, OFAC, EU AML Directive

