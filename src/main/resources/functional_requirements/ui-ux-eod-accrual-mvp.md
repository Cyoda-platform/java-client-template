# UI/UX Specification — EOD Accrual MVP

**Date**: 2025-10-07  
**Version**: 1.0  
**Scope**: Minimal viable product for EOD accrual batch operations and loan accrual drill-down

---

## 1. Overview

This specification defines the user interface for:
1. **EODAccrualBatch Panel** — Initiate and monitor daily accrual runs
2. **Loan Accrual Tab** — View accrual history and journal entry relationships for a single loan

**Design Principles**:
- Simple, form-based inputs with clear validation
- Real-time progress indicators
- Tabbed detail views for batch results
- Timeline visualization for loan accrual history

---

## 2. EODAccrualBatch Panel

### 2.1 Location
- **Path**: `/eod-accrual` or accessible via main navigation menu
- **Permission**: Requires `eod_accrual_view`; back-dated runs require `backdated_eod_execute`

### 2.2 Layout Structure

```
┌─────────────────────────────────────────────────────────────┐
│ EOD Accrual Batch                                           │
├─────────────────────────────────────────────────────────────┤
│ [Create New Batch]                                          │
│                                                             │
│ Recent Batches:                                             │
│ ┌───────────────────────────────────────────────────────┐  │
│ │ 2025-10-06 | TODAY     | COMPLETED | 1,234 loans     │  │
│ │ 2025-10-05 | TODAY     | COMPLETED | 1,230 loans     │  │
│ │ 2025-08-15 | BACKDATED | COMPLETED | 45 loans (PPA)  │  │
│ └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 Create New Batch Form

**Trigger**: Click `[Create New Batch]` button

**Form Fields**:

| Field | Type | Required | Validation | Notes |
|-------|------|----------|------------|-------|
| **As Of Date** | Date picker | Yes | Must be business day | Calendar integration; disable non-business days |
| **Mode** | Radio buttons | Yes | TODAY \| BACKDATED | Default: TODAY |
| **Reason Code** | Dropdown | Conditional | Required if BACKDATED | Options: DATA_CORRECTION, RATE_ADJUSTMENT, SYSTEM_ERROR, OTHER |
| **Loan Filter** | Multi-select | No | Valid loan IDs | Optional; if empty, processes all eligible loans |

**Form Behavior**:
- **Mode = TODAY**: Reason Code field is hidden/disabled
- **Mode = BACKDATED**: 
  - Reason Code field becomes required
  - Show warning: *"Back-dated runs may create Prior Period Adjustments (PPAs)"*
  - Require `backdated_eod_execute` permission

**Actions**:

| Button | Behavior | API Call |
|--------|----------|----------|
| **Validate** | Simulate run; show eligibility count | `POST /eod-batches` with `engineOptions.simulate=true` |
| **Start Run** | Create batch and begin processing | `POST /eod-batches` with `transitionRequest.name=START` |
| **Cancel** | Close form without saving | — |

**Validation Response** (after Validate):
```
✓ Eligible Loans: 1,234
✓ Business Day: Valid
✓ No Active Batch for Date
⚠ Period Status: CLOSED (PPAs will be flagged)
```

### 2.4 Batch Detail View

**Trigger**: Click on a batch row from Recent Batches list

**Header Section**:
```
┌─────────────────────────────────────────────────────────────┐
│ Batch: 2025-10-06 (TODAY)                    [Refresh]      │
│ Status: GENERATING → POSTING_COMPLETE → RECONCILING         │
│ Progress: ████████░░ 80% (1,000 / 1,234 loans)              │
├─────────────────────────────────────────────────────────────┤
│ Eligible: 1,234 | Processed: 1,000 | Failed: 12             │
│ Total DR: $45,678.90 | Total CR: $45,678.90 | Imbalance: $0 │
│ PPAs: 0                                                      │
└─────────────────────────────────────────────────────────────┘
```

**Progress Indicators**:
- **State**: Display current workflow state with visual progression
- **Progress Bar**: Percentage of loans processed
- **Metrics**: Real-time counts and totals

**Tabs**:

#### Tab 1: Accruals
**Purpose**: List all accruals created by this batch

**Columns**:
| Column | Source | Format |
|--------|--------|--------|
| Loan ID | `accrual.loanId` | Link to loan detail |
| As Of Date | `accrual.asOfDate` | YYYY-MM-DD |
| Interest Amount | `accrual.interestAmount` | Currency |
| State | `accrual.state` | Badge (color-coded) |
| PPA | `accrual.priorPeriodFlag` | ⚠ icon if true |
| Posted At | `accrual.postingTimestamp` | ISO timestamp |

**Filters**: State (dropdown), PPA flag (checkbox)

**Actions**: Click row to view accrual detail (opens Loan Accrual Tab for that loan)

#### Tab 2: Failures
**Purpose**: Show accruals that failed processing

**Columns**:
| Column | Source | Format |
|--------|--------|--------|
| Loan ID | `accrual.loanId` | Link to loan detail |
| Error Code | `accrual.error.code` | Text |
| Error Message | `accrual.error.message` | Truncated; tooltip for full text |
| State | `accrual.state` | FAILED badge |

**Actions**: 
- Export to CSV
- Retry (if batch is in terminal state)

#### Tab 3: Journals
**Purpose**: Aggregated view of all journal entries from batch accruals

**Columns**:
| Column | Source | Format |
|--------|--------|--------|
| Loan ID | `accrual.loanId` | Link |
| Entry ID | `journalEntry.entryId` | UUID (truncated) |
| Account | `journalEntry.account` | Text |
| Direction | `journalEntry.direction` | DR/CR badge |
| Amount | `journalEntry.amount` | Currency |
| Kind | `journalEntry.kind` | ORIGINAL/REVERSAL/REPLACEMENT |
| Adjusts | `journalEntry.adjustsEntryId` | Link to related entry (if present) |

**Filters**: Account (dropdown), Direction (dropdown), Kind (dropdown)

**Footer**: 
- Total DR: $X
- Total CR: $Y
- Imbalance: $Z (highlight if non-zero)

---

## 3. Loan Accrual Tab

### 3.1 Location
- **Path**: `/loans/{loanId}` → **Accruals** tab
- **Context**: Existing loan detail page; add new tab alongside existing tabs (Overview, Transactions, etc.)

### 3.2 Layout Structure

```
┌─────────────────────────────────────────────────────────────┐
│ Loan: L-12345                                               │
│ [Overview] [Transactions] [Accruals] [Documents]            │
├─────────────────────────────────────────────────────────────┤
│ Accrual Timeline                                            │
│                                                             │
│ 2025-10-06  ●───────────────────────────────────────────   │
│             │ $123.45 (POSTED)                              │
│             │ DR INTEREST_RECEIVABLE $123.45                │
│             │ CR INTEREST_INCOME $123.45                    │
│                                                             │
│ 2025-10-05  ●───────────────────────────────────────────   │
│             │ $122.00 (POSTED)                              │
│             │ DR INTEREST_RECEIVABLE $122.00                │
│             │ CR INTEREST_INCOME $122.00                    │
│                                                             │
│ 2025-08-15  ●───────────────────────────────────────────   │
│             │ $96.00 (POSTED) ⚠ PPA                         │
│             │ REVERSAL ↔ Original Entry e1234               │
│             │   DR INTEREST_INCOME $100.00                  │
│             │   CR INTEREST_RECEIVABLE $100.00              │
│             │ REPLACEMENT                                   │
│             │   DR INTEREST_RECEIVABLE $96.00               │
│             │   CR INTEREST_INCOME $96.00                   │
│             │ Supersedes: Accrual a5678                     │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 Timeline View

**Data Source**: Query all `Accrual` entities where `loanId = {loanId}`, ordered by `asOfDate DESC`

**Timeline Entry Structure**:

For each accrual:
1. **Date Marker**: `asOfDate` with visual bullet point
2. **Summary Line**: `interestAmount` and `state` badge
3. **PPA Indicator**: ⚠ icon if `priorPeriodFlag = true`
4. **Journal Entries**: Nested list of `journalEntries[]`
   - Format: `{direction} {account} {amount}`
   - **Kind Indicator**:
     - `ORIGINAL`: No prefix
     - `REVERSAL`: Prefix with "REVERSAL ↔" and link to original entry
     - `REPLACEMENT`: Prefix with "REPLACEMENT"
5. **Supersedence Link**: If `supersedesAccrualId` is present, show "Supersedes: Accrual {id}" with link

**Entry Linking**:
- When `journalEntry.adjustsEntryId` is present:
  - Display bidirectional link: `REVERSAL ↔ Original Entry {adjustsEntryId}`
  - Click link to scroll/highlight the original entry (may be in a different accrual)

**Visual Cues**:
- **State Colors**:
  - POSTED: Green
  - FAILED: Red
  - SUPERSEDED: Gray/strikethrough
  - CALCULATED: Yellow
- **PPA Warning**: Orange ⚠ icon with tooltip: "Prior Period Adjustment"

### 3.4 Expandable Detail

**Trigger**: Click on timeline entry to expand

**Expanded View**:
```
┌─────────────────────────────────────────────────────────────┐
│ 2025-08-15  ●─────────────────────────────────────────── ▼  │
│             │ Accrual ID: f0b8a123-4567-89ab-cdef-...       │
│             │ Interest Amount: $96.00                       │
│             │ State: POSTED                                 │
│             │ Posted At: 2025-10-06T03:12:45Z               │
│             │ Prior Period: Yes ⚠                           │
│             │ Run ID: r9876-5432-...                        │
│             │ Supersedes: a5678-1234-... [View]             │
│             │                                               │
│             │ Principal Snapshot: $120,000.00               │
│             │ APR ID: PRIME_PLUS_2                          │
│             │ Day Count: ACT_360                            │
│             │ Day Count Fraction: 0.002778                  │
│             │                                               │
│             │ Journal Entries:                              │
│             │ ┌─────────────────────────────────────────┐  │
│             │ │ e1 | REVERSAL | DR | INTEREST_INCOME   │  │
│             │ │    | $100.00 | Adjusts: e1234 [View]   │  │
│             │ │ e2 | REVERSAL | CR | INTEREST_RECEIVABLE│ │
│             │ │    | $100.00 | Adjusts: e5678 [View]   │  │
│             │ │ e3 | REPLACEMENT | DR | INTEREST_RECEIVABLE│
│             │ │    | $96.00                             │  │
│             │ │ e4 | REPLACEMENT | CR | INTEREST_INCOME │  │
│             │ │    | $96.00                             │  │
│             │ └─────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**Actions**:
- **[View]** next to `adjustsEntryId`: Navigate to the original entry (scroll + highlight)
- **[View]** next to `supersedesAccrualId`: Navigate to the superseded accrual

---

## 4. API Integration

### 4.1 EODAccrualBatch Panel

**List Recent Batches**:
```
GET /eod-batches?limit=10&sort=asOfDate:desc
Response: { "batches": [ {...}, {...} ] }
```

**Create & Validate**:
```
POST /eod-batches
Body: {
  "batch": { "asOfDate": "2025-10-06", "mode": "TODAY" },
  "transitionRequest": null,
  "engineOptions": { "simulate": true }
}
Response: { "metrics": { "eligibleLoans": 1234 }, "validationErrors": [] }
```

**Create & Start**:
```
POST /eod-batches
Body: {
  "batch": { "asOfDate": "2025-10-06", "mode": "TODAY" },
  "transitionRequest": { "name": "START" },
  "engineOptions": { "simulate": false, "maxSteps": 50 }
}
Response: { "batchId": "...", "state": "VALIDATED", ... }
```

**Poll Batch Status**:
```
GET /eod-batches/{batchId}
Response: { "batchId": "...", "state": "GENERATING", "metrics": {...} }
```

**Fetch Batch Accruals**:
```
GET /accruals?runId={batchId}&limit=100&offset=0
Response: { "accruals": [ {...}, {...} ], "total": 1234 }
```

### 4.2 Loan Accrual Tab

**Fetch Loan Accruals**:
```
GET /accruals?loanId={loanId}&sort=asOfDate:desc
Response: { "accruals": [ {...}, {...} ] }
```

**Fetch Single Accrual** (for drill-down):
```
GET /accruals/{accrualId}
Response: { "accrualId": "...", "journalEntries": [...], ... }
```

---

## 5. Error Handling

### 5.1 Validation Errors
- **Non-Business Day**: "Selected date is not a business day. Please choose a valid date."
- **Missing Reason Code**: "Reason code is required for back-dated runs."
- **Permission Denied**: "You do not have permission to execute back-dated runs. Contact your administrator."
- **Active Batch Exists**: "An active batch already exists for this date. Please wait for it to complete or cancel it."

### 5.2 Runtime Errors
- **Batch Failure**: Display error message from `batch.error` field; offer retry option
- **Accrual Failure**: Show in Failures tab with error code and message
- **Network Error**: "Unable to connect to server. Please check your connection and try again."

---

## 6. Responsive Design

**Desktop** (primary target):
- Full tabbed interface
- Timeline with expanded detail panels

**Tablet**:
- Stacked tabs (vertical)
- Condensed timeline entries

**Mobile** (read-only):
- Single-column layout
- Collapsible timeline entries
- No batch creation (redirect to desktop)

---

## 7. Performance Considerations

**Pagination**:
- Accruals tab: 100 rows per page
- Journals tab: 100 rows per page
- Timeline: Load 30 days at a time; infinite scroll for older entries

**Polling**:
- Batch status: Poll every 5 seconds while state is non-terminal
- Stop polling when state = COMPLETED | FAILED | CANCELED

**Caching**:
- Cache batch list for 30 seconds
- Cache accrual detail for 60 seconds
- Invalidate on user-initiated refresh

---

## 8. Accessibility

- **Keyboard Navigation**: All forms and tabs accessible via Tab/Shift+Tab
- **Screen Reader**: ARIA labels for all interactive elements
- **Color Contrast**: WCAG AA compliance for state badges and warnings
- **Focus Indicators**: Visible focus rings on all interactive elements

---

*End of UI/UX Specification — EOD Accrual MVP*

