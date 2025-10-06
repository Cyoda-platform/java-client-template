
# Cyoda Workflows: **Accrual** & **EODAccrualBatch**
Version: 1.0  
Last updated: 2025‑10‑05

> **Scope** — Formalize the **Accrual** entity and its workflow (including back‑dated rebooks), and the **EODAccrualBatch** entity that triggers and orchestrates an EOD accounting run (today or back‑dated). The configuration follows Cyoda’s workflow schema (states, transitions, criteria, processors).

> **Accounting note** — This design supports effective‑dated postings (Dr Interest Receivable / Cr Interest Income) and back‑dated adjustments with reversals and rebooks. It assumes a monthly GL aggregation process consumes the sub‑ledger by **effective date** and handles prior‑period adjustments.

---

## References
- Cyoda **Design Principles** — Entities as finite state machines; transitions, criteria, processors, automated recursion into a stable state.  
  https://docs.cyoda.net/guides/cyoda-design-principles/
- Cyoda **Workflow Config Guide** — Workflow object shape; state/transition schema; criteria types (function/simple/group); processors and execution modes; calculation node tags.  
  https://docs.cyoda.net/guides/workflow-config-guide/

---

## Definitions & Conventions

**Business Date (AsOfDate)** — A valid business day on the relevant calendar.  
**Effective Date** — The accounting effective date for journals (equal to AsOfDate).  
**Posting Timestamp** — System time when entries are posted.  
**Rebook** — Reverse previously posted amount for AsOfDate and post the corrected amount.  
**Prior‑Period Adjustment (PPA)** — Back‑dated impact into a closed GL month; flagged on journals and surfaced in the current period’s GL feed.  
**Idempotency Key** — `loanId + asOfDate + component("DAILY_INTEREST")` used to detect duplicates.

> **Compute routing** — Heavy processors run on calculation node groups via `calculationNodesTags`: `"accruals"`, `"ledger"`, `"recalc"` (configure per environment).

---

## Entity: **Accrual**

### Data Model (illustrative)
```json
{
  "accrualId": "UUID",
  "loanId": "UUID",
  "asOfDate": "YYYY-MM-DD",
  "currency": "ISO-4217",
  "aprId": "UUID or string (index+margin ref)",
  "dayCountConvention": "ACT_360 | ACT_365 | THIRTY_360 | ...",
  "dayCountFraction": "decimal",
  "principalSnapshot": {
    "amount": "decimal",
    "effectiveAtStartOfDay": true
  },
  "interestAmount": "decimal",
  "effectiveDate": "YYYY-MM-DD",         // == asOfDate
  "postingTimestamp": "ISO-8601",        // when journals were posted
  "journalIds": ["UUID"],                // reversal and rebook journals linked when applicable
  "runId": "UUID (EODAccrualBatch)",
  "version": 2,                          // incremented on rebook
  "supersedesAccrualId": "UUID|null",    // previous accrual being superseded
  "priorPeriodFlag": false,              // PPA indicator for closed GL periods
  "error": { "code": "string", "message": "string" }
}
```

### Workflow: **Accrual Workflow v1**
> Initial state `NEW`. Automated progression validates, calculates, posts, and lands in a stable terminal state. Transitions call processors and are guarded by criteria where noted.

```json
{
  "version": "1.0",
  "name": "Accrual Workflow v1",
  "desc": "Daily interest accrual with back-dated rebook support and sub-ledger posting",
  "initialState": "NEW",
  "active": true,
  "states": {
    "NEW": {
      "transitions": [
        {
          "name": "VALIDATE",
          "next": "ELIGIBLE",
          "manual": false,
          "disabled": false,
          "criterion": {
            "type": "group",
            "operator": "AND",
            "conditions": [
              { "type": "function", "function": {
                  "name": "IsBusinessDay",
                  "config": { "attachEntity": true, "context": "asOfDate" }
              }},
              { "type": "function", "function": {
                  "name": "LoanActiveOnDate",
                  "config": { "attachEntity": true, "context": "loanId,asOfDate" }
              }},
              { "type": "function", "function": {
                  "name": "NotDuplicateAccrual",
                  "config": { "attachEntity": true, "context": "loanId,asOfDate" }
              }},
              { "type": "simple",
                "jsonPath": "$.principalSnapshot.amount",
                "operatorType": "GREATER_THAN",
                "value": 0
              }
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
          "disabled": false,
          "processors": [
            {
              "name": "CalculateAccrualAmount",
              "executionMode": "ASYNC_NEW_TX",
              "config": {
                "attachEntity": true,
                "calculationNodesTags": "accruals"
              }
            },
            {
              "name": "DeriveDayCountFraction",
              "executionMode": "SYNC",
              "config": { "attachEntity": true }
            }
          ]
        },
        {
          "name": "REJECT",
          "next": "FAILED",
          "manual": true,
          "disabled": false
        }
      ]
    },
    "CALCULATED": {
      "transitions": [
        {
          "name": "POST_JOURNALS",
          "next": "POSTED",
          "manual": false,
          "disabled": false,
          "criterion": {
            "type": "function",
            "function": {
              "name": "SubledgerAvailable",
              "config": { "attachEntity": true }
            }
          },
          "processors": [
            {
              "name": "PostSubledgerEntries",
              "executionMode": "ASYNC_NEW_TX",
              "config": {
                "attachEntity": true,
                "calculationNodesTags": "ledger"
              }
            },
            {
              "name": "UpdateLoanAccruedInterest",
              "executionMode": "ASYNC_NEW_TX",
              "config": { "attachEntity": true, "calculationNodesTags": "ledger" }
            }
          ]
        },
        {
          "name": "CANCEL",
          "next": "CANCELED",
          "manual": true,
          "disabled": false
        }
      ]
    },
    "POSTED": {
      "transitions": [
        {
          "name": "SUPERSEDE_AND_REBOOK",
          "next": "SUPERSEDED",
          "manual": false,
          "disabled": false,
          "criterion": {
            "type": "function",
            "function": {
              "name": "RequiresRebook",
              "config": { "attachEntity": true, "context": "detect delta vs previously posted" }
            }
          },
          "processors": [
            {
              "name": "ReversePriorJournals",
              "executionMode": "ASYNC_NEW_TX",
              "config": { "attachEntity": true, "calculationNodesTags": "ledger" }
            },
            {
              "name": "CreateReplacementAccrual",
              "executionMode": "ASYNC_NEW_TX",
              "config": { "attachEntity": true, "calculationNodesTags": "accruals" }
            }
          ]
        }
      ]
    },
    "SUPERSEDED": { "transitions": [] },
    "FAILED":     { "transitions": [] },
    "CANCELED":   { "transitions": [] }
  }
}
```

**Key behaviors**
- Automated transitions apply recursively until a stable state is reached (per Cyoda workflow semantics).  
- Posting processors create **Dr Interest Receivable / Cr Interest Income** journals with `effectiveDate = asOfDate` and set `priorPeriodFlag` when the GL month for `asOfDate` is closed.  
- Rebooks: when source data change for the same `asOfDate`, the `RequiresRebook` criterion fires, reverses prior journals, and creates a replacement Accrual linked via `supersedesAccrualId`.

---

## Entity: **EODAccrualBatch**

### Data Model (illustrative)
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
  "metrics": {
    "eligibleLoans": 0,
    "processedLoans": 0,
    "accrualsCreated": 0,
    "postings": 0,
    "debited": "decimal",
    "credited": "decimal",
    "imbalances": 0
  },
  "reportId": "UUID|null",
  "error": { "code": "string", "message": "string" }
}
```

### Workflow: **EOD Accrual Batch Orchestration v1**
> Initial state `REQUESTED`. A manual transition kicks off the run; subsequent steps are automated until completion or failure.

```json
{
  "version": "1.0",
  "name": "EOD Accrual Batch Orchestration v1",
  "desc": "Triggers and orchestrates an EOD accrual run (today or back-dated), including cascade recalculation and reconciliation",
  "initialState": "REQUESTED",
  "active": true,
  "states": {
    "REQUESTED": {
      "transitions": [
        {
          "name": "START",
          "next": "VALIDATED",
          "manual": true,
          "disabled": false,
          "criterion": {
            "type": "group",
            "operator": "AND",
            "conditions": [
              { "type": "function", "function": {
                  "name": "IsBusinessDay",
                  "config": { "attachEntity": true, "context": "asOfDate" }
              }},
              { "type": "function", "function": {
                  "name": "NoActiveBatchForDate",
                  "config": { "attachEntity": true, "context": "asOfDate" }
              }},
              { "type": "function", "function": {
                  "name": "UserHasPermission",
                  "config": { "attachEntity": true, "context": "backdated_eod_execute" }
              }}
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
          "disabled": false,
          "processors": [
            {
              "name": "CaptureEffectiveDatedSnapshots",
              "executionMode": "ASYNC_NEW_TX",
              "config": { "attachEntity": true, "calculationNodesTags": "accruals" }
            },
            {
              "name": "ResolvePeriodStatus",
              "executionMode": "SYNC",
              "config": { "attachEntity": true }
            }
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
          "disabled": false,
          "processors": [
            {
              "name": "SpawnAccrualsForEligibleLoans",
              "executionMode": "ASYNC_NEW_TX",
              "config": {
                "attachEntity": true,
                "calculationNodesTags": "accruals"
              }
            }
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
          "disabled": false,
          "criterion": {
            "type": "function",
            "function": {
              "name": "AllAccrualsPosted",
              "config": { "attachEntity": true, "context": "batchId" }
            }
          }
        }
      ]
    },
    "POSTING_COMPLETE": {
      "transitions": [
        {
          "name": "CASCADE_RECALC_IF_BACKDATED",
          "next": "CASCADING",
          "manual": false,
          "disabled": false,
          "criterion": {
            "type": "function",
            "function": {
              "name": "IsBackDatedRun",
              "config": { "attachEntity": true }
            }
          },
          "processors": [
            {
              "name": "SpawnCascadeRecalc",
              "executionMode": "ASYNC_NEW_TX",
              "config": {
                "attachEntity": true,
                "calculationNodesTags": "recalc"
              }
            }
          ]
        },
        {
          "name": "SKIP_CASCADE_FOR_TODAY",
          "next": "RECONCILING",
          "manual": false,
          "disabled": false,
          "criterion": { "type": "function", "function": {
              "name": "IsTodayRun",
              "config": { "attachEntity": true }
          }}
        }
      ]
    },
    "CASCADING": {
      "transitions": [
        {
          "name": "CASCADE_COMPLETE",
          "next": "RECONCILING",
          "manual": false,
          "disabled": false,
          "criterion": {
            "type": "function",
            "function": {
              "name": "CascadeSettled",
              "config": { "attachEntity": true }
            }
          }
        }
      ]
    },
    "RECONCILING": {
      "transitions": [
        {
          "name": "FINALIZE",
          "next": "COMPLETED",
          "manual": false,
          "disabled": false,
          "processors": [
            {
              "name": "ProduceReconciliationReport",
              "executionMode": "ASYNC_NEW_TX",
              "config": { "attachEntity": true, "calculationNodesTags": "ledger" }
            }
          ],
          "criterion": {
            "type": "function",
            "function": {
              "name": "BatchBalanced",
              "config": { "attachEntity": true }
            }
          }
        }
      ]
    },
    "COMPLETED": { "transitions": [] },
    "FAILED":    { "transitions": [] },
    "CANCELED":  { "transitions": [] }
  }
}
```

---

## Processors (to implement)

| Name | Purpose | Suggested Mode | Calc Nodes Tag |
|---|---|---|---|
| `CalculateAccrualAmount` | `interestAmount = outstandingPrincipal × APR × DayCountFraction`; derive currency rounding | `ASYNC_NEW_TX` | `accruals` |
| `DeriveDayCountFraction` | Compute day‑count per product convention for `asOfDate` | `SYNC` | — |
| `PostSubledgerEntries` | Create Dr **Interest Receivable** / Cr **Interest Income** journals with `effectiveDate = asOfDate`; set `priorPeriodFlag` based on `periodStatus` | `ASYNC_NEW_TX` | `ledger` |
| `UpdateLoanAccruedInterest` | Add net accrual delta to loan’s accruedInterest | `ASYNC_NEW_TX` | `ledger` |
| `ReversePriorJournals` | Post equal‑and‑opposite reversal for prior accrual | `ASYNC_NEW_TX` | `ledger` |
| `CreateReplacementAccrual` | Create a corrected Accrual (version+1) linked via `supersedesAccrualId` | `ASYNC_NEW_TX` | `accruals` |
| `CaptureEffectiveDatedSnapshots` | Snapshot principal, APR, and policy as‑of start of `asOfDate` | `ASYNC_NEW_TX` | `accruals` |
| `ResolvePeriodStatus` | Determine if GL month of `asOfDate` is open/closed | `SYNC` | — |
| `SpawnAccrualsForEligibleLoans` | Fan‑out Accruals for loans **ACTIVE on asOfDate** (respect non‑accrual rules) | `ASYNC_NEW_TX` | `accruals` |
| `SpawnCascadeRecalc` | For back‑dated runs, enqueue day‑by‑day recompute from `asOfDate+1` to last posted date; post deltas only | `ASYNC_NEW_TX` | `recalc` |
| `ProduceReconciliationReport` | Summarize counts, totals, per‑state breakdown, and any PPAs | `ASYNC_NEW_TX` | `ledger` |

---

## Criteria (to implement)

| Name | Type | Purpose |
|---|---|---|
| `IsBusinessDay` | function | Validate `asOfDate` is a business day per configured calendar |
| `LoanActiveOnDate` | function | Loan state was `ACTIVE` on `asOfDate`; exclude `NON_ACCRUAL`/charged‑off when policy dictates |
| `NotDuplicateAccrual` | function | No existing Accrual for `loanId + asOfDate` with same idempotency key |
| `SubledgerAvailable` | function | Sub‑ledger service reachable and accounts configured |
| `RequiresRebook` | function | Detect underlying data change for same `asOfDate` producing a non‑zero delta |
| `NoActiveBatchForDate` | function | Enforce single batch per `asOfDate` concurrently |
| `UserHasPermission` | function | Caller has `backdated_eod_execute` |
| `AllAccrualsPosted` | function | Fan‑out Accruals for batch are in `POSTED` or terminal with zero imbalance |
| `IsBackDatedRun` / `IsTodayRun` | function | Branching in batch flow |
| `CascadeSettled` | function | All cascade recomputations finished |
| `BatchBalanced` | function | Debit/credit totals match; no unsettled items |

---


## API Surface (OpenAPI stub summary)

> The API exposes **entity save endpoints**; the engine runs after persistence. There are **no** criteria/processor endpoints.

**Accruals**
- `POST /accruals` — create (optionally with `transitionRequest` and `engineOptions`).
- `PATCH /accruals/{accrualId}` — update (optionally with `transitionRequest`).
- `GET /accruals/{accrualId}` — fetch.
- `GET /accruals/{accrualId}/history` — transition & processor audit.

**EOD Batches**
- `POST /eod-batches` — create (use `transitionRequest: { "name": "START" }` to kick off a run).
- `PATCH /eod-batches/{batchId}` — amend (loopback) or request manual transitions.
- `GET /eod-batches/{batchId}` — fetch.
- `GET /eod-batches/{batchId}/history` — audit trail.
- `GET /eod-batches/{batchId}/report` — download reconciliation report.

**Engine jobs**
- `GET /engine/jobs/{jobId}` — poll async work spawned by the engine.

**Download the stub (YAML):** [openapi-accrual-eod.v2.yaml](sandbox:/mnt/data/openapi-accrual-eod.v2.yaml)

**Common save request fields**
- `transitionRequest`: `{ "name": "START", "comment": "PPA correction for Aug 2025" }`
- `engineOptions`: `{ "simulate": true, "maxSteps": 50 }` (use `simulate=true` for “Validate” in the UI).

---

## UI/UX Requirements (aligned)

- **Principle**: UI performs only **entity saves**—with or without `transitionRequest`.
- **Validate flow**: `simulate=true` + `transitionRequest: START`; show any blocking criterion reason.
- **Start run**: Create batch with `transitionRequest: START`.
- **Amend and continue**: Patch batch **without** transition (loopback); engine may advance automatically.
- **Retry failed subset**: Start a **new** batch with `loanFilter` containing failed `loanIds`.
- **Concurrency guard**: Disable Start when an active batch exists for the date.
- **Audit**: Surface `/history` entries including transitions applied, processors launched, journals posted.
- **Downloads**: Provide reconciliation report download from the batch.

**Download the concise UI/UX doc:** [eod-accrual-uiux.v2.md](sandbox:/mnt/data/eod-accrual-uiux.v2.md)

---

## Operational Notes

- **Automated recursion to stable state**: When an entity enters a state, Cyoda executes the first eligible automated transition within the same transaction and recurses until no further automated transitions apply, yielding a stable state. This is why `VALIDATE → CALCULATE → POST_JOURNALS` are `manual=false`.  
- **Back‑dated logic**: For back‑dated runs, journals are effective‑dated to `asOfDate`. If the GL month is closed, mark `priorPeriodFlag=true` so the EOM aggregation can route to a PPA section.  
- **Idempotency**: Re‑runs for the same `asOfDate` with no underlying changes should post **no net new** journals. The `NotDuplicateAccrual`/`RequiresRebook` gates enforce this.  
- **Failure handling**: Any processor may raise an error; transition to `FAILED` with `error` payload. Partial batch failures should remain retryable via re‑entering the failed state or by manual `START` of a new batch scoped to the failed subset.

---

## Importing & Execution

1. Import the two JSON workflow objects above via Cyoda’s workflow import endpoints.  
2. Register the **function‑based criteria** and **processors** named above on your calculation nodes, tagging nodes (`accruals`, `ledger`, `recalc`) as desired.  
3. Create an `EODAccrualBatch` with `asOfDate` (today or past business day) and trigger `START`. The batch will fan‑out `Accrual` entities and drive them to `POSTED`, then cascade if back‑dated, then reconcile and complete.

---

## Appendix A — Journal Line Shape (suggested)
```json
{
  "journalId": "UUID",
  "effectiveDate": "YYYY-MM-DD",
  "postingTimestamp": "ISO-8601",
  "loanId": "UUID",
  "accrualId": "UUID",
  "account": "INTEREST_RECEIVABLE | INTEREST_INCOME",
  "direction": "DR | CR",
  "amount": "decimal",
  "currency": "ISO-4217",
  "runId": "UUID",
  "adjustsJournalId": "UUID|null",
  "priorPeriodFlag": false,
  "memo": "Back-dated EOD rebook ..."
}
```

---

### Change Log
- 1.0 — Initial definition of **Accrual** and **EODAccrualBatch** workflows; back‑dated rebooks; GL period handling; reconciliation.
