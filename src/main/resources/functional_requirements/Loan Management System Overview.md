This document outlines a specialised software system designed to manage commercial loans throughout their lifecycle.

# 1\. Business Overview

The system is a loan servicing platform focused on managing fixed-term commercial loans once they have been approved and funded. It acts as the primary record for all activities related to a loan, such as interest calculations, payments, and balances.

Built on a modern, event-driven architecture, the platform maintains a complete and unchangeable audit trail of every action taken on a loan. This ensures high levels of accuracy and traceability for financial reporting and compliance. Its main purpose is to automate the complex daily calculations and bookkeeping required for loan servicing and to prepare accurate financial summaries for the company's main accounting system.

## 1.1. Functional Scope

The system has a clearly defined set of responsibilities for its initial version (MVP).

**Key Features (In-Scope):**

* **Loan Lifecycle Management:** It handles the entire process from funding a loan, managing its active state, through to final settlement or closure.
* **Daily Interest Accrual:** The system automatically calculates interest each day based on the loan's outstanding principal and specific day-count rules.
* **Payment Processing:** It ingests and allocates borrower payments, correctly applying funds first to interest due, then fees, and finally to the principal balance.
* **Early Settlement:** It can calculate and process early settlement quotes for borrowers who wish to pay off their loan ahead of schedule.
* **Accounting Summaries:** At the end of each month, it aggregates all financial activity and prepares it for the accounting department.

**Key Exclusions (Out of Scope):**

* The system does **not** handle loan origination, credit scoring, or underwriting decisions.
* Complex loan products like variable interest rates or revolving lines of credit are not included.
* Processes for managing late payments, delinquency, or collections are also out of scope for this version.

## 1.2. System Interactions

The system is designed to be used by internal operational staff and to integrate with a downstream financial system.

### 1.2.1. User Interaction:

Users interact with the system through a straightforward operational user interface (UI) to perform specific roles:

* **Loan Administrators** use forms to set up, approve, and fund loans. They can also view detailed loan information, including balances and payment schedules.
* **Payment Processors** can enter payment information manually or manage imported payment files.
* **Finance Teams** use the system at month-end to review, prepare, and export the financial summary for the General Ledger. The system enforces a "maker/checker" workflow for critical actions like approvals and GL exports to ensure accuracy.

### 1.2.2. Downstream System Interaction:

The system's primary interaction is with the company's main General Ledger (GL).

* The loan system acts as a detailed **subledger**, recording every single daily transaction (like interest accruals) internally.
* At the end of the month, it summarises all these detailed transactions into a GLBatch.
* This batch is then exported as a file (e.g., CSV or JSON) or sent via an API to be posted to the GL. This keeps the company's main GL clean and efficient, as it only receives one set of summarised figures from the loan system per month rather than thousands of individual daily transactions.

---

# 2\. Specifications (Overview)

## **2.1. Entities**

### **2.1.1. Loan**

* Represents a funded commercial loan under servicing.
* Attributes: principal, APR, term (12/24/36 months), repayment day, funded date, outstanding principal, accrued interest, status.

### **2.1.2. Payment**

* Represents a borrower’s remittance, either manually captured or imported from a file.
* Attributes: amount, value date, allocation (interest, fees, principal), source reference, status.

### **2.1.3. PaymentFile**

* Batch import container (e.g., bank statement or receivables file) that yields Payment entities.
* Attributes: file metadata, validation status, processing results.

### **2.1.4. Accrual**

* Daily record of interest calculation per loan.
* Attributes: accrual date, principal base, APR, day-count basis, interest amount.

### **2.1.5. SettlementQuote**

* Early settlement payoff calculation snapshot.
* Attributes: as-of date, quoted total (principal + accrued interest + fee), expiry, acceptance status.

### **2.1.6. GLBatch**

* Periodic summary of loan system financial activity for export to the General Ledger.
* Attributes: period, batch ID, totals (interest, principal, fees), export file/record, status.

### **2.1.7. Party (reference)**

* Borrower or counterparty to the loan.
* Attributes: identity, status (active/suspended), metadata.

## **2.2. Lifecycle Workflows**

### **2.2.1. Loan Workflow**

* **DRAFT → APPROVAL_PENDING → APPROVED → FUNDED → ACTIVE → {SETTLED | CLOSED}**
* Automated transitions:
  * FUNDED → ACTIVE (when funded_at ≤ current date).
  * ACTIVE → CLOSED (at maturity if principal = 0).
  * ACTIVE → SETTLED (if early settlement quote accepted and paid).

### **2.2.2. PaymentFile Workflow**

* **RECEIVED → VALIDATING → {VALID | INVALID} → IMPORTED**
* Automation:
  * RECEIVED → VALIDATING (file header validation).
  * VALIDATING → VALID/INVALID (based on validation results).
  * VALID → IMPORTED (creates individual Payment entities).

### **2.2.3. Payment Workflow**

* **CAPTURED → MATCHED → ALLOCATED → POSTED**
* Automation:
  * CAPTURED → MATCHED (loan identified by reference).
  * MATCHED → ALLOCATED (apply interest → fees → principal).
  * ALLOCATED → POSTED (subledger entries written, loan updated).
* Special handling for back-dated payments triggers accrual recomputation.

### **2.2.4. Accrual Workflow**

* **SCHEDULED → CALCULATING → RECORDED**
* Automation:
  * Triggered daily by EOD timer for each ACTIVE loan.
  * CALCULATING → RECORDED (interest amount persisted, subledger updated, loan updated).
* Supports recomputation when triggered by back-dated payments.

### **2.2.5. SettlementQuote Workflow**

* **QUOTED → ACCEPTED → {EXECUTED | EXPIRED}**
* Automation:
  * ACCEPTED → EXECUTED (on receipt of covering payment).
  * QUOTED → EXPIRED (if now > expiry date via timer).

### **2.2.6. GLBatch Workflow**

* **OPEN → PREPARED → EXPORTED → POSTED → ARCHIVED**
* Automation:
  * OPEN → PREPARED (monthly close timer event).
  * PREPARED → EXPORTED (manual maker/checker).
  * EXPORTED → POSTED (on acknowledgment from GL).
  * POSTED → ARCHIVED (end of retention cycle).

## **2.3. Processes Triggered on Transitions**

### **2.3.1. Loan**

* **On Create:** Validate loan terms and party references.
* **On Approve:** Record approval metadata with maker/checker controls.
* **On Fund:** Initialize balances, generate reference schedule, trigger automated "Go Active".
* **On Apply Settlement:** Stop further accruals, finalize balances, emit settlement event.
* **On Maturity Close:** Freeze loan, set state to CLOSED.
* **On Balance Update:** React to payment allocations and accrual postings to update outstanding balances.

### **2.3.2. PaymentFile**

* **On Receive:** Validate file format and headers.
* **On Parse:** Create individual Payment entities from valid file lines.
* **On Validation Failure:** Route to exception handling for manual review.

### **2.3.3. Payment**

* **On Capture:** Record payment details from manual entry or file import.
* **On Match Loan:** Run reference matching (loan ID, virtual account, metadata).
* **On Allocate:** Apply funds to interest, fees, then principal.
* **On Post:** Write subledger entry, emit PaymentAllocated event for loan update.
* **On Back-dated Payment:** Trigger accrual recomputation from value date.

### **2.3.4. Accrual**

* **On Schedule:** Daily timer creates accrual records for all ACTIVE loans.
* **On Calculate:** Compute interest using principal, APR, and day-count basis.
* **On Record:** Write to subledger, emit AccrualPosted event for loan update.
* **On Recompute:** Re-run accruals when triggered by back-dated payment.

### **2.3.5. SettlementQuote**

* **On Quote:** Calculate payoff (principal OS + accrued interest + fee).
* **On Accept:** Lock amounts, mark valid until expiry.
* **On Execute:** Trigger Loan.ApplySettlement when payment is posted.
* **On Expire:** Mark quote invalid via timer.

### **2.3.6. GLBatch**

* **On Open:** Monthly timer creates new batch for the period.
* **On Prepare:** Summarize interest and payments for the month into balanced journal lines.
* **On Export:** Create and deliver file/API payload to GL with maker/checker controls.
* **On Post:** Record acknowledgment from GL.
* **On Archive:** Freeze batch and close for audit.

## **2.4. Event & Timer Triggers**

* **Daily EOD Timer:** Create Accrual entity for each ACTIVE loan.
* **Monthly EOM Timer:** Create GLBatch(OPEN) for the period.
* **Settlement Expiry Timer:** Auto-expire quotes past validity.
* **Reactive Events:**
  * Payment.Posted → Loan balance updates and potential maturity closure.
  * Accrual.Recorded → Loan balance updates.
  * SettlementQuote.Executed → Loan settlement.
  * Payment(back-dated) → Accrual recomputation cascade.

## **2.5. Cross-Entity Interactions**

* **Payment → Loan:** Payment posting triggers loan balance updates and may unlock maturity closure.
* **PaymentFile → Payment:** File processing creates individual payment entities for workflow processing.
* **Accrual → Loan:** Daily accruals update loan interest receivable balances.
* **SettlementQuote → Loan:** Quote execution triggers loan settlement workflow.
* **Back-dated Payments:** Trigger cascading accrual recomputation to maintain accuracy.
* **Timer Events:** Drive automated daily accruals and monthly GL batch creation.

This structure follows Cyoda’s guidance:

* Entities are FSMs with explicit states.
* Transitions have criteria (simple, functional, grouped).
* Automated transitions recurse until stable state.
* Processors are bound to transitions, executed sync or async, sometimes creating/mutating other entities.

---

# 3\. Specifications (Details)

## **3.1. Modeling approach (Cyoda alignment)**

* **Entity‑centric**: Every persisted object is modeled as an **Entity** with lifecycle **States** and **Transitions**; behavior is driven by **Events** and executed in **Processors**.
* **Workflows**: Transitions may be **manual** or **automated** (criteria‑driven). On entering a state, the **first eligible automated transition executes in the same transaction and recurses** until no automated transition applies (stable state). Each transition can invoke processors (sync/async; same or separate transaction).
* **Timers & cascades**: Events can be external (UI/API, file import) or **internal timers** (EOD/EOM), and a transition’s processors may create/mutate other entities to produce a **cascade** of domain events.

## **3.2. Entity catalog (MVP)**

Each entity stores a **tree‑structured** document (the “data”), a **State**, and an **audit timeline** of transitions.

1. **Party**
   Borrower or corporate customer reference used by loans and payments.
2. **Loan** (aggregate root)
   Fixed‑term commercial loan (12/24/36 months) with APR, day‑count basis, funding date, repayment day‑of‑month, outstanding principal, interest receivable.
3. **Payment**
   A received cash item (manual entry or imported line) that is validated and allocated to interest/fees/principal for a target loan.
4. **PaymentFile**
   A batch import (e.g., bank statement or receivables file) that yields **Payment** entities.
5. **Accrual**
   Per‑loan, per‑day interest accrual record (internal precision). Carries basis/denominator and principal base used.
6. **SettlementQuote**
   Early payoff quote for a loan “as‑of” a date; on acceptance it locks amounts and drives settlement.
7. **GLBatch**
   Month‑end summarized journal (subledger→GL) with balanced lines for accruals and cash; exported to the downstream GL system. The LMS acts as the **subledger** and emits **one summarized batch per period** as per your overview.

## **3.3. Workflows per entity (states, transitions, criteria, processors)**

**Legend:** M \= manual; A \= automated; **Proc** \= processor name (execution mode).

### **3.3.1. Party (reference)**

**States:** ACTIVE, SUSPENDED (terminal)

**Key transitions:**

* Activate (M) → ACTIVE — Proc: none (reference data).
* Suspend (M) → SUSPENDED — Proc: “BlockNewLoans” (ASYNC\_NEW\_TX) to veto future Loan.Create.

### **3.3.2. Loan**

**States:**

DRAFT → APPROVAL\_PENDING → APPROVED → FUNDED → ACTIVE → {SETTLED | CLOSED}

**Transitions & processors:**

* Create (M) DRAFT → APPROVAL\_PENDING
   **Proc:** ValidateNewLoan (SYNC) — shape/terms/day‑count policy checks.

* Approve (M, maker/checker) APPROVAL\_PENDING → APPROVED
   **Proc:** StampedApproval (SYNC) — record actor/role metadata.

* Fund (M) APPROVED → FUNDED
   **Proc (chain):**
  1. SetInitialBalances (SYNC) — set OS principal \= funded principal; clear interest receivable.
  2. GenerateReferenceSchedule (ASYNC\_NEW\_TX) — compute level‑payment schedule as a **projection** only (actuals come from daily accruals).
  3. **Auto move**: criteria “funded\_at ≤ now()” triggers GoActive (A) → ACTIVE.

* RecordMaturity (A) ACTIVE → CLOSED
   **Criterion:** maturity date reached **and** principal\_os \== 0 (from last allocation).
   **Proc:** CloseLoan (SYNC) — freeze balances, mark terminal.

* RequestSettlementQuote (M) ACTIVE → ACTIVE (self‑transition)
   **Proc:** ComputeSettlementQuote (ASYNC\_NEW\_TX) — creates **SettlementQuote** entity.

* ApplySettlement (A) ACTIVE → SETTLED
   **Criterion:** accepted **SettlementQuote** exists with value date S and receipt/payment covering quoted total.
   **Proc (chain):** StopAccrualFrom(S+1) (SYNC) → FinalizeBalances (SYNC).

   *Note:* Quote acceptance & payment are handled on **SettlementQuote** and **Payment**; this transition is **automated** when criteria become true.

* **Reactive update (not a state change):** On any **PaymentAllocated** or **AccrualPosted**, the UpdateLoanBalances processor (ASYNC\_NEW\_TX) updates interest\_receivable, principal\_os, and next\_due\_date on the **Loan**.

### **3.3.3. PaymentFile**

**States:** RECEIVED → VALIDATING → {VALID | INVALID} → IMPORTED

**Transitions & processors:**

* StartValidation (A) RECEIVED → VALIDATING — Proc: ValidateFileHeader (SYNC).

* PassValidation (A) VALIDATING → VALID — Proc: ParseLines (ASYNC\_NEW\_TX) → **creates Payment entities**.

* FailValidation (A) VALIDATING → INVALID — Proc: RaiseImportException (ASYNC\_NEW\_TX).

* MarkImported (A) VALID → IMPORTED once all child **Payments** reach terminal state for ingestion.

   *(Automated recursion to stable state aligns with Cyoda workflow semantics.)*

### **3.3.4. Payment**

**States:** CAPTURED → MATCHED → ALLOCATED → POSTED

**Transitions & processors:**

* Capture (M) → CAPTURED — manual entry or row produced by PaymentFile.ParseLines.

* MatchLoan (A) CAPTURED → MATCHED — Proc: FindLoanByRef (SYNC) with rules (loan\_id, virtual account ref, or metadata).

* Allocate (A) MATCHED → ALLOCATED — Proc: AllocateInterestFeesPrincipal (SYNC) applying interest first (from Accrual), then fees, then principal; produces PaymentAllocation outcome.

* PostToSubledger (A) ALLOCATED → POSTED — Proc: WriteSubledgerEntries (ASYNC\_NEW\_TX) \+ emit PaymentAllocated domain event that **updates the Loan**.

* **Back‑dated handling:** if value\_date \< today, RecomputeAccrualFrom(value\_date) (ASYNC\_NEW\_TX) is queued (see Accrual workflow).

### **3.3.5. Accrual**

**States:** SCHEDULED → CALCULATING → RECORDED

**Transitions & processors:**

* StartAccrual (A) SCHEDULED → CALCULATING — Proc: ComputeDailyInterest (SYNC) using principal as of prior day, APR, and configured day‑count (ACT/365F, ACT/360, or ACT/365L).

* RecordAccrual (A) CALCULATING → RECORDED — Proc: AppendToAccrualLedger (SYNC) and emit AccrualPosted event (Loan reacts).

* **Recalc path:** TriggerRecalc(from\_date) (M/A) — creates a new **Accrual** sequence for affected dates; transitions as above until re‑posted.

**How Accruals are created:** A **timer event** (EOD) drives creation of one Accrual per **ACTIVE** loan per business day; in Cyoda terms, timers are valid event sources that initiate transitions/processors.

### **3.3.6. SettlementQuote**

**States:** QUOTED → ACCEPTED → EXECUTED → EXPIRED

**Transitions & processors:**

* Quote (M/A) → QUOTED — Proc: CalculatePayoffAsOf(S) (SYNC) → principal\_os(S−1) \+ accrued interest to S \+ fee rule.

* Accept (M) QUOTED → ACCEPTED — Proc: LockQuoteUntil(S) (SYNC).

* Execute (A) ACCEPTED → EXECUTED — Criterion: a **Payment.POSTED** covering the quote total on/after S. Proc: MarkLoanForSettlement (ASYNC\_NEW\_TX) which triggers **Loan.ApplySettlement**.

* Expire (A) QUOTED → EXPIRED — Criterion: now() \> expires\_at.

### **3.3.7. GLBatch**

**States:** OPEN → PREPARED → EXPORTED → POSTED → ARCHIVED

**Transitions & processors:**

* Prepare (M/A) OPEN → PREPARED — Proc: SummarizePeriod (ASYNC\_NEW\_TX) to roll up **Accrual** and **Payment** activity by dimensions (company/product/branch).

* Export (M, maker/checker) PREPARED → EXPORTED — Proc: RenderGLFile (SYNC) (CSV/JSON) \+ SendToGL (ASYNC\_NEW\_TX via API or file drop).

* MarkPosted (A) EXPORTED → POSTED — Criterion: downstream acknowledgment received.

* Archive (A) POSTED → ARCHIVED — Proc: FreezeBatch (SYNC).

The month‑end batch embodies your **subledger→GL summarization** requirement; LMS retains full daily detail internally and emits a single summarized batch per period to the GL.

## **3.4. Processes bound to transitions (specification)**

For each transition, the table lists the **actionable processes**, mode, inputs/outputs, and cross‑entity effects.

### **3.4.1. Loan processes**

* **ValidateNewLoan** (SYNC)
   **On:** Loan.Create → APPROVAL\_PENDING
   **Inputs:** party\_id exists; term ∈ {12,24,36}; APR; day‑count basis.
   **Outputs:** validation errors or entity updated.
   **Cross‑effects:** none.

* **GenerateReferenceSchedule** (ASYNC\_NEW\_TX)
   **On:** Loan.Fund
   **Inputs:** funded principal, APR, term, repayment DOM.
   **Outputs:** nested schedule projection in loan.schedule (advisory).
   **Cross‑effects:** none.

* **UpdateLoanBalances** (ASYNC\_NEW\_TX)
   **On:** Payment.PostToSubledger or Accrual.RecordAccrual
   **Inputs:** allocation outcome; daily interest.
   **Outputs:** updates principal\_os, interest\_receivable, next\_due\_date.
   **Cross‑effects:** may trigger **Loan.RecordMaturity** when criteria satisfied.

* **FinalizeBalances** (SYNC)
   **On:** Loan.ApplySettlement
   **Inputs:** accepted quote totals.
   **Outputs:** set OS principal to zero; stop accrual effective S+1.

### **3.4.2. Payment & import processes**

* **ValidateFileHeader** (SYNC)
   **On:** PaymentFile.StartValidation
   **Outputs:** pass/fail; error list.

* **ParseLines** (ASYNC\_NEW\_TX)
   **On:** PaymentFile.PassValidation
   **Outputs:** Create **Payment** entities with source\_ref.

* **FindLoanByRef** (SYNC)
   **On:** Payment.MatchLoan
   **Inputs:** payment metadata (loan\_id, invoice ref, virtual account).
   **Outputs:** matched loan or exception to Ops queue.

* **AllocateInterestFeesPrincipal** (SYNC)
   **On:** Payment.Allocate
   **Inputs:** interest due since last clearing, fee rules, amount\_gross.
   **Outputs:** allocation split (interest→fees→principal).
   **Cross‑effects:** emits PaymentAllocated for **Loan.UpdateLoanBalances**.

* **RecomputeAccrualFrom(value\_date)** (ASYNC\_NEW\_TX)
   **On:** back‑dated **Payment**
   **Outputs:** re‑posts **Accrual** entities for the affected interval.

### **3.4.3. Accrual processes**

* **ComputeDailyInterest** (SYNC)
   **On:** Accrual.StartAccrual
   **Inputs:** principal\_{d-1}, APR, basis (ACT/365F|ACT/360|ACT/365L).
   **Outputs:** interest amount (≥8 dp), stored to **Accrual**.

* **AppendToAccrualLedger** (SYNC)
   **On:** Accrual.RecordAccrual
   **Outputs:** durable subledger entry; emits AccrualPosted.

### **3.4.4. Settlement processes**

* **CalculatePayoffAsOf(S)** (SYNC)
   **On:** SettlementQuote.Quote
   **Inputs:** principal\_os(S−1), accrued interest to S, fee policy.
   **Outputs:** computed totals; expiry.

* **MarkLoanForSettlement** (ASYNC\_NEW\_TX)
   **On:** SettlementQuote.Execute
   **Outputs:** calls **Loan.ApplySettlement** (automated criteria satisfied).

### **3.4.5. GL processes**

* **SummarizePeriod** (ASYNC\_NEW\_TX)
   **On:** GLBatch.Prepare
   **Inputs:** period; dimension filters (company/product).
   **Outputs:** lines: Dr **Interest Receivable** / Cr **Interest Income**; cash receipts splits; rounding if any.
   **Cross‑effects:** none (exported externally).

* **RenderGLFile** (SYNC) & **SendToGL** (ASYNC\_NEW\_TX)
   **On:** GLBatch.Export
   **Outputs:** CSV/JSON payload and delivery to GL endpoint.

---

## **3.5. Timer‑driven automation (modeled as events)**

* **EOD Accrual Timer**: For each **ACTIVE** loan, create Accrual(SCHEDULED) for the business date; automation carries it to RECORDED.
* **EOM GL Timer**: Create a GLBatch(OPEN) for period YYYY‑MM; automation (or Finance action) triggers Prepare.
* **Quote Expiry Timer**: Move SettlementQuote.QUOTED → EXPIRED when now() \> expires\_at.

Timers are valid initiators of transitions/processors in Cyoda’s event‑driven model.

---

## **3.6. Cross‑entity cascades (reactive patterns)**

* **Payment → Loan**: Payment.PostToSubledger emits PaymentAllocated → Loan.UpdateLoanBalances and may unlock Loan.RecordMaturity.
* **Payment(back‑dated) → Accrual**: triggers RecomputeAccrualFrom(value\_date) which re‑posts daily accruals and updates Loan.
* **SettlementQuote.Execute → Loan**: marks loan for ApplySettlement\` once funds clear.

   Cyoda explicitly supports **processors that create/mutate other entities**, enabling such cascades.

---

## **3.7. Assumptions & open points (to confirm)**

* Default **day‑count** basis (ACT/365F vs ACT/360 vs ACT/365L) and precision for internal accrual storage (≥8 dp proposed).
* Early‑settlement **fee policy** (none/fixed/%), tax handling if applicable.
* GL export **dimensions** (company/product/branch) and interface (CSV/JSON/API).
* Business‑day calendar, time zone, and EOD/EOM cutover times.
* Maker/checker **roles** mapped via your SSO.

