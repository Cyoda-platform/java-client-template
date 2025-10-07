
# Cyoda Specification — Accrual & EOD Orchestration
Date: 2025‑10‑06

> **Scope** — This specification defines a commercial‑loan **Accrual** domain entity with **embedded journal entries** and the **EODAccrualBatch** entity that orchestrates daily accounting runs (including back‑dated). It follows Cyoda’s workflow model: entities are **saved** (create/patch) with optional **transition requests**; after persistence, the engine evaluates **criteria** and executes **processors** during automated transitions.

---

## 1. Core Concepts

- **Business Date (AsOfDate)** — Valid business day per configured calendar.
- **Accrual** — Daily interest computation for a loan **as‑of AsOfDate**.
- **Effective Date** — Equal to **AsOfDate** for all journal entries of the Accrual.
- **Posting Timestamp** — Time at which the Accrual’s accounting effect is persisted.
- **Rebook** — Reverse previously posted amounts for a given AsOfDate and post corrected amounts (supersedence).
- **Prior‑Period Adjustment (PPA)** — Back‑dated impact into a closed GL month; flagged at the **Accrual** level.

---

## 2. Data Model

### 2.1 Accrual (single source of truth)

```json
{
  "accrualId": "UUID",
  "loanId": "UUID",
  "asOfDate": "YYYY-MM-DD",
  "currency": "ISO-4217",
  "aprId": "string",
  "dayCountConvention": "ACT_360 | ACT_365 | THIRTY_360 | ...",
  "dayCountFraction": 0.0,
  "principalSnapshot": { "amount": 0.0, "effectiveAtStartOfDay": true },
  "interestAmount": 0.0,
  "postingTimestamp": "ISO-8601",
  "priorPeriodFlag": false,
  "runId": "UUID",                       // optional, batch that produced this accrual
  "version": 1,
  "supersedesAccrualId": "UUID|null",
  "state": "NEW | ELIGIBLE | CALCULATED | POSTED | SUPERSEDED | FAILED | CANCELED",
  "journalEntries": [ /* see 2.2 */ ],
  "error": { "code": "string", "message": "string" }
}
```

**Invariants**
- `effectiveDate` for all journal entries is **inherited** from `asOfDate`.
- All journal entries inherit `currency`, `loanId`, `postingTimestamp`, `priorPeriodFlag`, and `runId` from the **Accrual** (entries do not repeat these fields).
- For any **POSTED** accrual: \(\sum DR = \sum CR\) across `journalEntries`.
- Idempotency for re‑runs is keyed by `(loanId, asOfDate, "DAILY_INTEREST")`.

### 2.2 JournalEntry (embedded; **no parent fields duplicated**)

```json
{
  "entryId": "UUID",
  "account": "INTEREST_RECEIVABLE | INTEREST_INCOME",
  "direction": "DR | CR",
  "amount": 0.0,
  "kind": "ORIGINAL | REVERSAL | REPLACEMENT",
  "adjustsEntryId": "UUID|null",
  "memo": "string|null"
}
```

> **Inheritance contract** — `asOfDate` (effective date), `currency`, `loanId`, `postingTimestamp`, `priorPeriodFlag`, and `runId` are **not present** on `JournalEntry`. These values are resolved from the parent **Accrual** at read/export time.

**Linking semantics**
- `REVERSAL` entries **must** reference the reversed `entryId` in `adjustsEntryId` (which may exist on a **superseded** Accrual).
- `REPLACEMENT` entries represent the corrected amounts for the same AsOfDate.

---

## 3. Acceptance Criteria (functional)

1. **EOD accrual run** creates one **Accrual** for each loan that was **ACTIVE on AsOfDate**.
2. **Interest amount** is calculated as `outstandingPrincipal × APR × DayCountFraction`.
3. On success, the Accrual transitions to **POSTED** and contains two embedded journal entries:
    - **DR** `INTEREST_RECEIVABLE` for `interestAmount`,
    - **CR** `INTEREST_INCOME` for `interestAmount`.
4. Back‑dated corrections create a **new Accrual** for the same `asOfDate` with:
    - `REVERSAL` entries that exactly offset the prior Accrual’s originals, and
    - `REPLACEMENT` entries for the corrected amounts.  
      The new Accrual sets `supersedesAccrualId` to the prior one.
5. The **loan.accruedInterest** balance is updated by the **net delta** implied by newly embedded entries (including reversals).
6. The **monthly GL feed** aggregates all journal entries by **effective date (asOfDate)**, honoring `priorPeriodFlag` (PPAs).

---

## 4. Workflow Configurations (JSON)

### 4.1 Accrual Workflow

```json
{
  "version": "1.0",
  "name": "Accrual Workflow",
  "initialState": "NEW",
  "states": {
    "NEW": {
      "transitions": [
        {
          "name": "VALIDATE",
          "next": "ELIGIBLE",
          "manual": false,
          "criterion": {
            "type": "group",
            "operator": "AND",
            "conditions": [
              { "type": "function", "function": { "name": "IsBusinessDay", "config": { "attachEntity": true } } },
              { "type": "function", "function": { "name": "LoanActiveOnDate", "config": { "attachEntity": true } } },
              { "type": "function", "function": { "name": "NotDuplicateAccrual", "config": { "attachEntity": true } } },
              { "type": "simple", "jsonPath": "$.principalSnapshot.amount", "operatorType": "GREATER_THAN", "value": 0 }
            ]
          }
        }
      ]
    },
    "ELIGIBLE": {
      "transitions": [
        {
          "name": "CALCULATE",
          "next": "CALCULATED",
          "manual": false,
          "processors": [
            { "name": "DeriveDayCountFraction", "executionMode": "SYNC", "config": { "attachEntity": true } },
            { "name": "CalculateAccrualAmount", "executionMode": "ASYNC_NEW_TX", "config": { "attachEntity": true, "calculationNodesTags": "accruals" } }
          ]
        },
        { "name": "REJECT", "next": "FAILED", "manual": true }
      ]
    },
    "CALCULATED": {
      "transitions": [
        {
          "name": "WRITE_JOURNALS",
          "next": "POSTED",
          "manual": false,
          "criterion": { "type": "function", "function": { "name": "SubledgerAvailable", "config": { "attachEntity": true } } },
          "processors": [
            { "name": "WriteAccrualJournalEntries", "executionMode": "ASYNC_NEW_TX", "config": { "attachEntity": true, "targetPath": "$.journalEntries" } },
            { "name": "UpdateLoanAccruedInterest", "executionMode": "ASYNC_NEW_TX", "config": { "attachEntity": true, "sourcePath": "$.journalEntries" } }
          ]
        },
        { "name": "CANCEL", "next": "CANCELED", "manual": true }
      ]
    },
    "POSTED": {
      "transitions": [
        {
          "name": "SUPERSEDE_AND_REBOOK",
          "next": "SUPERSEDED",
          "manual": false,
          "criterion": { "type": "function", "function": { "name": "RequiresRebook", "config": { "attachEntity": true } } },
          "processors": [
            { "name": "ReversePriorJournals", "executionMode": "ASYNC_NEW_TX", "config": { "attachEntity": true, "targetPath": "$.journalEntries" } },
            { "name": "CreateReplacementAccrual", "executionMode": "ASYNC_NEW_TX", "config": { "attachEntity": true, "calculationNodesTags": "accruals" } }
          ]
        }
      ]
    },
    "SUPERSEDED": { "transitions": [] },
    "FAILED": { "transitions": [] },
    "CANCELED": { "transitions": [] }
  }
}
```

**Notes**
- After each **save** (create/patch), the engine attempts automated transitions in order until a stable state.
- Supersedence produces a **new Accrual** for the same `asOfDate`; reversals/replacements live in the new Accrual’s `journalEntries` array.

### 4.2 EODAccrualBatch Workflow (orchestration)

```json
{
  "version": "1.0",
  "name": "EOD Accrual Batch",
  "initialState": "REQUESTED",
  "states": {
    "REQUESTED": {
      "transitions": [
        {
          "name": "START",
          "next": "VALIDATED",
          "manual": true,
          "criterion": {
            "type": "group",
            "operator": "AND",
            "conditions": [
              { "type": "function", "function": { "name": "IsBusinessDay", "config": { "attachEntity": true } } },
              { "type": "function", "function": { "name": "NoActiveBatchForDate", "config": { "attachEntity": true } } },
              { "type": "function", "function": { "name": "UserHasPermission", "config": { "attachEntity": true, "context": "backdated_eod_execute" } } }
            ]
          }
        }
      ]
    },
    "VALIDATED": {
      "transitions": [
        {
          "name": "TAKE_SNAPSHOT",
          "next": "SNAPSHOT_TAKEN",
          "manual": false,
          "processors": [
            { "name": "CaptureEffectiveDatedSnapshots", "executionMode": "ASYNC_NEW_TX", "config": { "attachEntity": true, "calculationNodesTags": "accruals" } },
            { "name": "ResolvePeriodStatus", "executionMode": "SYNC", "config": { "attachEntity": true } }
          ]
        }
      ]
    },
    "SNAPSHOT_TAKEN": {
      "transitions": [
        {
          "name": "GENERATE_ACCRUALS",
          "next": "GENERATING",
          "manual": false,
          "processors": [
            { "name": "SpawnAccrualsForEligibleLoans", "executionMode": "ASYNC_NEW_TX", "config": { "attachEntity": true, "calculationNodesTags": "accruals" } }
          ]
        }
      ]
    },
    "GENERATING": {
      "transitions": [
        {
          "name": "AWAIT_POSTED",
          "next": "POSTING_COMPLETE",
          "manual": false,
          "criterion": { "type": "function", "function": { "name": "AllAccrualsPosted", "config": { "attachEntity": true } } }
        }
      ]
    },
    "POSTING_COMPLETE": {
      "transitions": [
        {
          "name": "CASCADE_RECALC_IF_BACKDATED",
          "next": "CASCADING",
          "manual": false,
          "criterion": { "type": "function", "function": { "name": "IsBackDatedRun", "config": { "attachEntity": true } } },
          "processors": [
            { "name": "SpawnCascadeRecalc", "executionMode": "ASYNC_NEW_TX", "config": { "attachEntity": true, "calculationNodesTags": "recalc" } }
          ]
        },
        {
          "name": "SKIP_CASCADE_FOR_TODAY",
          "next": "RECONCILING",
          "manual": false,
          "criterion": { "type": "function", "function": { "name": "IsTodayRun", "config": { "attachEntity": true } } }
        }
      ]
    },
    "CASCADING": {
      "transitions": [
        {
          "name": "CASCADE_COMPLETE",
          "next": "RECONCILING",
          "manual": false,
          "criterion": { "type": "function", "function": { "name": "CascadeSettled", "config": { "attachEntity": true } } }
        }
      ]
    },
    "RECONCILING": {
      "transitions": [
        {
          "name": "FINALIZE",
          "next": "COMPLETED",
          "manual": false,
          "criterion": { "type": "function", "function": { "name": "BatchBalanced", "config": { "attachEntity": true } } },
          "processors": [
            { "name": "ProduceReconciliationReport", "executionMode": "ASYNC_NEW_TX", "config": { "attachEntity": true, "calculationNodesTags": "ledger" } }
          ]
        }
      ]
    },
    "COMPLETED": { "transitions": [] },
    "FAILED": { "transitions": [] },
    "CANCELED": { "transitions": [] }
  }
}
```

---

## 5. Processors (engine‑attached)

| Name | Purpose | Mode | Node Tag |
|---|---|---|---|
| `DeriveDayCountFraction` | Compute day‑count per product convention | SYNC | — |
| `CalculateAccrualAmount` | `interestAmount = principal × APR × DayCountFraction` | ASYNC_NEW_TX | accruals |
| `WriteAccrualJournalEntries` | Write **embedded** DR/CR entries to `$.journalEntries` | ASYNC_NEW_TX | ledger |
| `UpdateLoanAccruedInterest` | Update loan’s accruedInterest from net delta of entries | ASYNC_NEW_TX | ledger |
| `ReversePriorJournals` | Append equal‑and‑opposite `REVERSAL` entries | ASYNC_NEW_TX | ledger |
| `CreateReplacementAccrual` | Create **new** Accrual (superseding prior) with `REPLACEMENT` entries | ASYNC_NEW_TX | accruals |
| `CaptureEffectiveDatedSnapshots` | Snapshot principal/APR/policy at AsOfDate | ASYNC_NEW_TX | accruals |
| `ResolvePeriodStatus` | Determine GL period status (open/closed) for AsOfDate | SYNC | — |
| `SpawnAccrualsForEligibleLoans` | Fan‑out Accruals for ACTIVE loans on AsOfDate | ASYNC_NEW_TX | accruals |
| `SpawnCascadeRecalc` | For back‑dated, recompute forward days & post deltas | ASYNC_NEW_TX | recalc |
| `ProduceReconciliationReport` | Summaries and PPAs; persist file for download | ASYNC_NEW_TX | ledger |

---

## 6. Criteria (engine‑evaluated)

| Name | Purpose |
|---|---|
| `IsBusinessDay` | Validate AsOfDate against business calendar |
| `LoanActiveOnDate` | Loan was ACTIVE on AsOfDate; handle NON_ACCRUAL/charge‑off policy |
| `NotDuplicateAccrual` | Prevent duplicate for `(loanId, asOfDate)` unless superseding |
| `SubledgerAvailable` | Sub‑ledger reachable and accounts configured |
| `RequiresRebook` | Underlying data change yields non‑zero delta for same AsOfDate |
| `NoActiveBatchForDate` | Only one active batch per AsOfDate |
| `UserHasPermission` | `backdated_eod_execute` for back‑dated runs |
| `AllAccrualsPosted` | All fan‑out Accruals are POSTED or terminal |
| `IsBackDatedRun` / `IsTodayRun` | Branch orchestration flow |
| `CascadeSettled` | All cascade recomputations finished |
| `BatchBalanced` | Debits equal credits; no unsettled items |

---

## 7. API Interaction Model (entity‑save + transition)

The API exposes **entity save** (create/patch) and **fetch** endpoints. There are **no** endpoints to call criteria or processors directly. The engine runs after persistence.

**Common request envelope**
```json
{
  "transitionRequest": { "name": "START|REJECT|CANCEL", "comment": "string|null" },
  "engineOptions": { "simulate": false, "maxSteps": 50 }
}
```

### 7.1 Create & start a back‑dated EOD run (example)

`POST /eod-batches`
```json
{
  "batch": {
    "asOfDate": "2025-08-15",
    "mode": "BACKDATED",
    "reasonCode": "DATA_CORRECTION"
  },
  "transitionRequest": { "name": "START" },
  "engineOptions": { "simulate": false, "maxSteps": 50 }
}
```

### 7.2 Accrual after posting (example shape)

```json
{
  "accrualId": "f0b8...",
  "loanId": "1111-2222-...",
  "asOfDate": "2025-08-15",
  "currency": "USD",
  "interestAmount": 96.00,
  "postingTimestamp": "2025-10-06T03:12:45Z",
  "priorPeriodFlag": true,
  "state": "POSTED",
  "journalEntries": [
    { "entryId": "e1", "account": "INTEREST_RECEIVABLE", "direction": "DR", "amount": 96.00, "kind": "REPLACEMENT" },
    { "entryId": "e2", "account": "INTEREST_INCOME",     "direction": "CR", "amount": 96.00, "kind": "REPLACEMENT" }
  ]
}
```

> **Back‑dated rebook**: The engine first created a new Accrual that **reversed** the original \$100 entries (`kind="REVERSAL"`, referencing prior `entryId`s), then wrote the **replacement** \$96 entries as above. Both sets inherit `asOfDate`, `currency`, `postingTimestamp`, and PPA status from the parent Accrual.

---

## 8. GL Aggregation (end‑of‑month)

**Input**: Iterate `Accrual.journalEntries` for all accruals whose `asOfDate` falls in month **M**.  
**Grouping**: `(asOfDate, account, direction, currency, priorPeriodFlag)` — all inherited from the parent Accrual, except `account`, `direction`, and `amount` which come from entries.  
**Summation**: Sum amounts (DR positive, CR negative or separate buckets).  
**Restatement trail**: Maintain batch file id, checksums, totals; include PPAs in designated section.

---

## 9. Audit & History

- **Entity history** records: Full entity history is captured by Cyoda’s built‑in capabilities.
- **Supersedence chain**: traverse `supersedesAccrualId` to reconstruct prior versions.
- **Traceability**: `entryId` is unique; `adjustsEntryId` links reversals to originals (possibly across accruals).

---

## 10. UI/UX Outline (operations)

- **EOD Console**: AsOfDate picker (business‑day), Mode (Today/Back‑dated), Reason Code (required if Back‑dated), Loan Filter (optional). Actions: **Validate** (simulate), **Start Run**.
- **Batch Detail**: Progress (eligible/processed/failed), totals (DR/CR, imbalance, PPA count), tabs for **Accruals**, **Failures**, **Journals**, **Reconciliation**. Journals tab renders from `accrual.journalEntries`.
- **Loan Drill‑down**: Date‑timeline of accruals; pairs/links (REVERSAL ↔ ORIGINAL) via `adjustsEntryId`.
- **Audit Log**: Immutable events; export CSV.

---

## 11. Non‑functional Considerations

- **Idempotency**: deterministic keys avoid duplicate entries on retry.
- **Concurrency**: optimistic version control on Accrual.
- **Performance**: shard fan‑out; checkpoints in orchestration.
- **Security**: permission `backdated_eod_execute` for back‑dated runs; audit all operator actions.
- **Resilience**: partial failures isolate; retries operate on failed subset only.

---

## 12. Example — Worked Back‑dated Scenario

- Prior Accrual (2025‑08‑15) posted \$100 DR/CR (ORIGINAL).
- On 2025‑10‑06, a data fix affects AsOfDate=2025‑08‑15; a new Accrual is created with:
    - **REVERSAL** entries of \$100 (DR Income, CR Receivable) referencing the prior `entryId`s.
    - **REPLACEMENT** entries of \$96 (DR Receivable, CR Income).
- Net effect for 2025‑08‑15: –\$4 income and receivable; `priorPeriodFlag=true`; GL feed for August reflects the delta.

---

## 13. JSON Shapes (consolidated)

### Accrual (read model)
```json
{
  "accrualId": "UUID",
  "loanId": "UUID",
  "asOfDate": "YYYY-MM-DD",
  "currency": "ISO-4217",
  "aprId": "string",
  "dayCountConvention": "string",
  "dayCountFraction": 0.0,
  "principalSnapshot": { "amount": 0.0, "effectiveAtStartOfDay": true },
  "interestAmount": 0.0,
  "postingTimestamp": "ISO-8601",
  "priorPeriodFlag": false,
  "runId": "UUID",
  "version": 1,
  "supersedesAccrualId": "UUID|null",
  "state": "NEW|ELIGIBLE|CALCULATED|POSTED|SUPERSEDED|FAILED|CANCELED",
  "journalEntries": [
    { "entryId": "UUID", "account": "INTEREST_RECEIVABLE", "direction": "DR", "amount": 0.0, "kind": "ORIGINAL", "adjustsEntryId": null, "memo": null },
    { "entryId": "UUID", "account": "INTEREST_INCOME",     "direction": "CR", "amount": 0.0, "kind": "ORIGINAL", "adjustsEntryId": null, "memo": null }
  ]
}
```

### EODAccrualBatch (read model)
```json
{
  "batchId": "UUID",
  "asOfDate": "YYYY-MM-DD",
  "mode": "TODAY | BACKDATED",
  "initiatedBy": "userId",
  "reasonCode": "string|null",
  "loanFilter": { "loanIds": ["UUID"], "productCodes": ["string"] },
  "periodStatus": "OPEN | CLOSED",
  "cascadeFromDate": "YYYY-MM-DD|null",
  "metrics": { "eligibleLoans": 0, "processedLoans": 0, "accrualsCreated": 0, "postings": 0, "debited": 0.0, "credited": 0.0, "imbalances": 0 },
  "reportId": "UUID|null",
  "state": "REQUESTED|VALIDATED|SNAPSHOT_TAKEN|GENERATING|POSTING_COMPLETE|CASCADING|RECONCILING|COMPLETED|FAILED|CANCELED"
}
```

---

*End of specification.*
