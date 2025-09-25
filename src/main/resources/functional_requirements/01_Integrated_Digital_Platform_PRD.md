# Integrated Digital Platform for Research & Clinical Trial Management
*Status:* Consolidated Draft v0.2 — *Date:* 2025-09-25

This Product Requirements Document (PRD) defines a phased delivery of an integrated, digital platform for managing research studies and clinical trials from submission through operational conduct and reporting. It includes an MVP (Phase 0) with clear goals and testable hypotheses and subsequent phases (V1, V1+, V2).

---

## Table of Contents
1. [Executive Summary](#1-executive-summary)  
2. [Objectives & Success Metrics](#2-objectives--success-metrics)  
3. [Users & Roles](#3-users--roles)  
4. [Phased Rollout](#4-phased-rollout)  
   - [Phase 0 — MVP (Goals & Testable Hypotheses)](#phase-0--mvp-goals--testable-hypotheses)  
   - [Phase 1 — V1 Core Platform](#phase-1--v1-core-platform)  
   - [Phase 2 — V1-plus Operations & Collaboration](#phase-2--v1-plus-operations--collaboration)  
   - [Phase 3 — V2 Integrations & Enhancements](#phase-3--v2-integrations--enhancements)  
5. [Functional Requirements by Epic](#5-functional-requirements-by-epic)  
6. [Non-Functional Requirements](#6-non-functional-requirements)  
7. [Data Model (High-Level)](#7-data-model-high-level)  
8. [Reference Workflow](#8-reference-workflow)  
9. [Feature → Phase Map](#9-feature--phase-map)  
10. [UI/UX Principles](#10-uiux-principles)  
11. [Acceptance Tests](#11-acceptance-tests)  
12. [Assumptions & TBDs](#12-assumptions--tbds)  
13. [Glossary](#13-glossary)

---

## 1) Executive Summary
An enterprise platform to manage the end-to-end lifecycle of research studies and clinical trials: submission and review, digital document management, configurable workflows, operational trial conduct (subjects, visits, medications, adverse events), two-way requests for information, analytics, and real-time progress visibility.

---

## 2) Objectives & Success Metrics

### Objectives
- Centralize study/trial/project submissions for internal and external users.  
- Digitize document handling with registration, electronic archiving, versioning, and full traceability.  
- Configure workflows to match internal review/approval processes.  
- Operate clinical trials: capture visits, medications, AEs/SAEs and related data.  
- Support two-way interactions when additional documents or clarifications are required.  
- Provide self-service statistics/reports and real-time status visibility.

### Sample KPIs
- Median days from submission to decision (by study type).  
- % submissions complete at first pass.  
- RFI (request-for-information) resolution time (median/P95).  
- % on-time patient visits; protocol deviation rate.  
- AE/SAE reporting timeliness.  
- User satisfaction (CSAT) for internal/external users.

---

## 3) Users & Roles
- **External Submitter** (sponsor, CRO, investigator)  
- **Intake/Registry Admin** (screens submissions)  
- **Scientific Reviewer / Ethics (IRB/EC) / Legal / Finance**  
- **PI / Study Coordinator / Site User**  
- **Safety Officer / Pharmacovigilance**  
- **Data Manager / Monitor (CRA)**  
- **System Admin / Compliance Officer**  

Role-based access control (RBAC) must support per-study and per-site scoping; least-privilege presets with policy-based overrides.

---

## 4) Phased Rollout

### Phase 0 — MVP (Goals & Testable Hypotheses)

#### Goals
- Validate end-to-end digital **submission → review → decision → activation** for one primary study type (**clinical_trial**) with internal and external users.  
- Prove basic operational tracking for an approved study: **subject enrollment**, **scheduled visits**, **basic AE capture**, and **lightweight medication dispensing**.  
- Provide **traceable digital documents** and **auditable actions** across the above flows.  
- Offer **transparent status visibility** to submitters and reviewers via simple dashboards.

#### Testable Hypotheses
- External submitters can complete guided submissions without help when presented with type-aware **required fields & document lists**.  
- A **single, linear review lane** with optional side-consults reduces cycle time compared with current processes.  
- Minimal **visit tracking + AE capture** meets pilot sites’ immediate operational needs.

#### Pilot Boundaries & Assumptions
- One organization (single tenant), one pilot department; ≤ 5 reviewers, ≤ 20 external submitters; ≤ 3 pilot studies; ≤ 60 subjects total.  
- English UI; desktop web (latest Chrome/Edge/Safari).  
- Real patient data only with governance approval; otherwise representative test data.  
- Authentication: internal SSO (OIDC/SAML) for staff; email+password+MFA for externals.

#### MVP User Journeys (must work end-to-end)
1) External study submission (wizard) → Intake screening → single-lane **Scientific Review** (optional consult notes from Ethics/Legal) → **Decision & activation** (study shell created).  
2) Document lifecycle: upload required docs, minor version update, view audit log, download latest.  
3) Operations slice: create subject → schedule baseline + 1 follow-up visit → record actual visit date → capture a basic AE → (optional) record a medication dispense.  
4) Reviewer ↔ submitter interaction: RFI requesting a missing doc; submitter uploads; workflow proceeds.  
5) Visibility: submitter sees status timeline; reviewers see a simple work queue; managers view a basic dashboard.

#### MVP Features — In / Out
**IN**  
- **Submission portal:** external registration/login (email verification, MFA); study type = clinical_trial only; guided wizard with dynamic required fields; pre-submit validation; virus-scanned attachments; submitter status timeline & RFIs.  
- **Documents:** metadata, **versioning**, latest/previous access, checksum, basic classifications; immutable **audit log** for create/read/update/download; role-based access; watermark (“Pilot”) on downloads.  
- **Workflows:** **linear template** (*Draft → Submitted → Intake → Scientific Review → Decision*); admin-editable SLAs (hours), assignees, and transition labels via JSON config; per-role task queue; manual escalation flag.  
- **Operations:** study shell from approved submission (title, protocol ID, visit template); subjects (create, enroll, consent status flag/date); visits (planned vs actual, status); AEs with minimal required fields (onset, seriousness, severity, relatedness, outcome, narrative); **SAE flag triggers email alert**; medications: **dispense log only** (date, lot, quantity).  
- **Reporting/Monitoring:** **prebuilt dashboards only** (funnel, per-step cycle time, reviewer workload, enrolled/due/overdue visits, AE count); auto-refresh via polling (30–60s).  
- **Admin/Security:** roles (Submitter, Intake, Reviewer, Coordinator, Safety, Admin); org settings (email, MFA policy); user management (invite, deactivate); basic audit exports (CSV).

**OUT → deferred to later phases**  
- Multi-language; multiple study types; bulk submissions; templated decision letters with e-signature; eTMF completeness checks; redaction; retention policy UI; export packages; parallel/conditional workflows, OOO auto-reassign, graphical designer; CRF designer & edit checks; data query workflow; data lock/freeze; IP returns/reconciliation; inventory management; dictionary coding; safety gateway (E2B); protocol deviation taxonomy; multi-site rollup; ad-hoc report builder & schedules; websockets real-time.

#### MVP Minimal Data Sets
- **Submission:** id, title, study_type, protocol_id, phase, sponsor, PI, sites (free text OK), start/end dates, risk_category, required_docs[], provided_docs[], status, created/updated.  
- **Document:** id, name, type, version_label, status, checksum, content_type, size, uploaded_by/at.  
- **Workflow Instance:** id, template_key, current_state, history(events[]).  
- **Study:** id, title, protocol_id, visit_template (list of visit_codes & windows).  
- **Subject:** id, screening_id, enrollment_date, consent_status/date, status.  
- **Visit:** id, subject_id, visit_code, planned_date, actual_date, status, deviations (free text).  
- **AE:** id, subject_id, onset_date, seriousness, severity, relatedness, outcome, narrative, is_SAE, follow_up_due_date.  
- **Dispense:** id, subject_id, lot_number (string ok), date, quantity, performed_by.

#### MVP Non-Functional Baselines
- **AuthN/AuthZ:** SSO for staff; MFA for externals; role checks on every API.  
- **Security:** TLS, at-rest encryption, AV scan on upload.  
- **Auditability:** append-only audit log for data/doc changes and downloads.  
- **Availability/Performance (targets):** small-pilot scale; p95 page load under ~3s for key screens; background jobs for file scanning.  
- **Accessibility:** essential keyboard navigation and labels for wizard and task lists.

#### MVP Compliance Posture
- Foundational controls only: authentication with MFA, audit trails for critical actions, immutable document versioning, basic approval records.  
- Full computerized system validation and formal regulatory alignment targeted for Phase 1 and beyond.

#### MVP Integrations
- **Must:** Corporate SSO (OIDC/SAML), SMTP for notifications, AV scan service.  
- **Deferred:** EHR/EMR, coding dictionaries, registries (e.g., ClinicalTrials.gov), safety gateways, e-signature providers.

#### MVP Go/No-Go
1) External user completes wizard with required docs; item routes through Intake and Scientific Review; decision recorded; **study shell auto-created**.  
2) Document traceability verified: upload → new version; audit log shows create/read/update/download with user and timestamp; latest clearly indicated.  
3) Operations slice: subject enrolled; baseline visit completed; one AE recorded; **SAE email** sent when flagged.  
4) RFI loop: reviewer requests missing doc; submitter uploads; workflow unblocks.  
5) Visibility: submitter sees current state and due date; reviewers see work queue counts; manager dashboard shows funnel and cycle times.

#### MVP Success Metrics
- Completion rate of external submissions without admin intervention.  
- Median time Submitted → Decision (clinical_trial).  
- RFI resolution time (median/P95).  
- Operational adherence: % visits within window; AE entry timeliness (e.g., within 48h of onset).  
- CSAT from submitters and reviewers at milestones.

#### MVP Build Cut-Lines (drop order if needed)
1) Medication dispense logging.  
2) Study shell auto-creation (fallback: manual create from template).  
3) Email MFA for externals (fallback: authenticator-app MFA only).  
4) Cycle-time dashboard (fallback: CSV export).

#### MVP Migration & Data
- No legacy migration; allow **CSV import** for initial sites/subjects (admin-only).  
- Provide **CSV exports** for submissions, document metadata, subjects, visits, and AEs.

#### MVP Delivery Artifacts
- Configured **linear workflow template**.  
- Wizard field configuration and required document lists for clinical_trial.  
- Minimal **RBAC policy** JSON.  
- Dashboard query definitions (prebuilt).  
- Pilot runbook: environments, users, data handling, rollback, issue triage.

#### MVP Risks & Mitigations
- Workflow variance beyond linear lane → document out-of-band steps; escalate to Phase 1 template designer.  
- Data privacy in pilot → restrict access; mask PII/PHI in reports; audit downloads.  
- Scope creep from operational teams → enforce cut-lines; weekly scope review; feature flags.  
- External adoption friction → wizard checklists and inline validation; simple help text/examples.

---

### Phase 1 — V1 Core Platform
- **Submission portal (internal/external)** with guided wizards by type, templates/checklists, pre-submission validation, submitter timeline & tasks.  
- **Digital documents (eTMF-style):** registration, metadata, versioning, supersede/withdraw; immutable audit; e-archiving and retention policies; exports; access control, watermarks, read-and-acknowledge, redaction support; bulk upload/mapping.  
- **Configurable workflows:** visual state machines, parallel steps, conditional branches, SLAs, rework loops, delegated approvers; task queues, escalations/reminders; e-signatures for approvals; decision letters.  
- **Dashboards:** submission funnel, cycle time by step, workload, overdue tasks; study operational metrics.  
- **Security/Compliance:** RBAC, MFA, encryption, field-level access for PHI, auditability; availability/performance baselines and observability; accessibility improvements.

**Exit Criteria (V1):** PRD acceptance tests for submission/doc/workflow/dashboards pass; availability/observability in place; CSV/validation assets prepared per SOPs.

---

### Phase 2 — V1-plus Operations & Collaboration
- **Operational trial management:** study/site setup (arms, visit schedules, procedures), subject lifecycle, visit tracking with deviations, medications/IP tracking (dispense/return, lot/expiry, accountability logs, basic inventory), AEs/SAEs with notifications; CRFs with edit checks; data entry roles; data queries; freeze/lock.  
- **RFIs & messaging:** threaded conversations, @mentions, file requests with due dates, templates, activity feed, email deep links.  
- **Reporting:** ad-hoc analytics, saved reports, scheduled delivery.  
- **Real-time monitoring:** live status boards (event-driven).

**Exit Criteria (V1+):** Operational KPIs and data-quality controls verified; reporting coverage meets stakeholder needs; real-time boards reflect state changes promptly.

---

### Phase 3 — V2 Integrations & Enhancements
- **Integrations:** EHR/EMR (FHIR), safety gateways, MedDRA/WHO Drug coding, registry exports (e.g., ClinicalTrials.gov), eConsent, IRT.  
- **Experience:** mobile and additional usability enhancements as prioritized.

**Exit Criteria (V2):** Successful external integrations validated; compliance sign-offs completed per scope.

---

## 5) Functional Requirements by Epic

### 5.1 Submission Portal (Studies, Clinical Trials, Research Projects)
**Features**
- Public login/registration (SSO/OIDC + MFA), invitations for externals.  
- Guided submission wizards by study type; dynamic required fields & document lists.  
- Attachment upload with virus scanning; templates & checklists.  
- Pre-submission validation (field completeness, document presence).  
- Submitter view: status timeline, outstanding actions, messaging thread.  
- Decision letters from templates (with e-signature).

**Acceptance (examples)**
- New submission receives a unique ID and timestamps.  
- Missing mandatory items block submission with a clear list of missing items.

---

### 5.2 Digital Document Management (eTMF-style)
**Features**
- Document registration (type, category), metadata, versioning, supersede/withdraw flows.  
- Immutable audit log (who/what/when/why) for create/read/update/delete and download events.  
- Electronic archiving with retention policies; export packages for audits.  
- Controlled access; watermarks; read-and-acknowledge flows; redaction support.  
- Bulk upload and mapping to required document checklists.

**Acceptance**
- Every document action is audit-logged with user, timestamp, action, and rationale.  
- Version chain preserved; latest clearly indicated; authorized access to older versions.

---

### 5.3 Configurable Workflows (Review & Approval)
**Features**
- Visual, configurable state machine per submission type (e.g., *Intake → Scientific → Ethics → Legal → Finance → Decision*).  
- Parallel steps, conditional branches, SLAs, rework loops, delegated approvers.  
- Task queues, escalations, reminders, out-of-office reassignments.  
- E-signatures for approvals; decision letters from templates.

**Acceptance**
- Admin can define states, transitions, approvals, and SLAs without code.  
- SLA breach posts escalation and flags items in dashboards.

---

### 5.4 Operational Trial Management (CTMS/EDC-lite)
**Features**
- **Study/Site setup:** arms, visit schedules, procedures; site and investigator rosters.  
- **Subject management:** screening, enrollment, subject IDs, consent status.  
- **Visit tracking:** planned vs actual; protocol deviations; missed visits.  
- **Medications/IP tracking:** dispense/return; lot/expiry; accountability logs; basic inventory counts.  
- **Adverse events:** capture AEs/SAEs with severity, relatedness, outcome, follow-up; link to subjects and visits; attach source documents; notifications to Safety.  
- **Data capture:** CRF forms with edit checks; role-based data entry; queries; data freeze/lock by visit or study.

**Acceptance**
- Visit calendars show due/overdue; visit completion updates metrics.  
- AE entry requires subject linkage and minimum data; SAE flag triggers Safety alerts.

---

### 5.5 RFIs & Messaging
**Features**
- Threaded conversations per submission/study; @mentions; file requests with due dates.  
- “Request document” task type tied to document categories; one-click satisfy via upload.  
- Message templates; activity feed; email notifications with deep links.

**Acceptance**
- Creating an RFI generates a visible task; completing it updates the parent workflow step.

---

### 5.6 Reporting & Statistics
**Features**
- Prebuilt dashboards: submission funnel, cycle times by step, workload by role, overdue tasks; study operational metrics (enrollment, visit compliance, AE rates).  
- Ad-hoc analytics with saved reports; export to CSV/XLSX; scheduled delivery.

**Acceptance**
- Authorized users can construct tables/charts from submissions, documents, workflow events, and operations data.

---

### 5.7 Real-Time Monitoring
**Features**
- Live status boards (per committee, per portfolio); event-driven updates.  
- SLA and exception heatmaps; “My Tasks” panel with prioritization.

**Acceptance**
- Changes in submission/workflow/visit reflect on dashboards without manual refresh within seconds (target ≤5s).

---

### 5.8 Administration & Configuration
- Master data: study types, document taxonomies, visit templates, AE seriousness rules.  
- Roles/permissions; environment configuration (email, MFA policy).  
- Data retention/time-zone settings; legal hold.

---

## 6) Non-Functional Requirements
- **Security & Privacy:** RBAC; MFA; encryption in transit/at rest; field-level access for PHI; optional IP allow-listing.  
- **Auditability:** immutable logs for data/doc changes and e-signatures; time-sync across services.  
- **Availability & Performance:** target 99.9% monthly uptime; p95 page load < 2.5s for key screens; near-real-time event propagation.  
- **Scalability:** multi-study, multi-site; horizontal scaling for deadline spikes.  
- **Compliance Targets:** electronic signatures and audit trails aligned with common regulatory expectations (e.g., 21 CFR Part 11/Annex 11), ICH-GCP data integrity principles, GDPR-aligned processing and retention.  
- **Accessibility:** WCAG 2.2 AA target.  
- **Localization:** date/time/number formats; strings externalized (English in initial releases).  
- **Observability:** structured logs, metrics, tracing; admin health dashboard.  
- **Backup/DR:** daily snapshots; RPO ≤ 24h; RTO ≤ 8h.

---

## 7) Data Model (High-Level)

**Core Entities**  
Organization, User, Role, Permission; Submission, SubmissionStep, Decision, RFI, Task; Document, DocumentVersion, ChecklistItem; Study, Site, Subject, VisitPlan, Visit, Procedure; MedicationLot, Dispense, Return, Inventory; AdverseEvent, AEFollowUp, Query; AuditEvent, Signature.

**Key Relationships**  
- Submission ↔ Documents (1‑N)  
- Submission → Study (0‑1)  
- Study ↔ Site (1‑N); Study ↔ Subject (1‑N)  
- Subject ↔ Visit (1‑N); Visit ↔ Procedures (1‑N)  
- Subject ↔ AE (1‑N)  
- Document ↔ Version (1‑N)  
- All entities ↔ AuditEvent (1‑N)

---

## 8) Reference Workflow
1) *Draft* (submitter) → validation  
2) *Intake Review* → completeness check; auto-route  
3) *Scientific Review* (parallel: *Ethics*, *Legal*, *Finance* as configured)  
4) *Consolidation* → decision draft & e‑signature  
5) *Decision* (Approve / Conditionally Approve / Reject)  
6) *Activation* (if approved) → study created; operational templates applied

SLAs and escalation rules are configurable per step; rework loops return to targeted prior steps.

---

## 9) Feature → Phase Map

| Epic / Feature                                  | Phase 0 (MVP) |          Phase 1 (V1)          |    Phase 2 (V1+)     | Phase 3 (V2) |
|-------------------------------------------------|:-------------:|:------------------------------:|:--------------------:|:------------:|
| External registration & MFA                     |       ✓       |               ✓                |          ✓           |      ✓       |
| Clinical_trial submission wizard                |       ✓       |         ✓ (multi-type)         |          ✓           |      ✓       |
| Virus scan on upload                            |       ✓       |               ✓                |          ✓           |      ✓       |
| Submitter timeline & RFIs                       |       ✓       |               ✓                |     ✓ (enhanced)     |      ✓       |
| Document versioning & audit                     |       ✓       | ✓ (retention/export/redaction) |          ✓           |      ✓       |
| Linear workflow                                 |       ✓       |                                |                      |              |
| Parallel/conditional workflows                  |               |               ✓                |          ✓           |      ✓       |
| E‑signatures & decision letters                 |               |               ✓                |          ✓           |      ✓       |
| Study shell creation                            |       ✓       |               ✓                |          ✓           |      ✓       |
| Subjects, visits (basic)                        |       ✓       |               ✓                |    ✓ (deviations)    |      ✓       |
| AE capture (basic) & SAE email                  |       ✓       |               ✓                |     ✓ (expanded)     |      ✓       |
| Medication dispense log                         |       ✓       |               ✓                | ✓ (return/inventory) |      ✓       |
| CRFs with edit checks                           |               |                                |          ✓           |      ✓       |
| Data queries; freeze/lock                       |               |                                |          ✓           |      ✓       |
| Ad-hoc reporting & schedules                    |               |                                |          ✓           |      ✓       |
| Live boards (event-driven)                      |               |                                |          ✓           |      ✓       |
| Integrations (FHIR, safety, coding, registries) |               |                                |                      |      ✓       |

---

## 10) UI/UX Principles
- Task-first dashboards; clear status chips (state + SLA).  
- Consistent document cards with version badges.  
- Wizard-style submissions; inline validation; autosave.  
- Accessibility: keyboard navigation and ARIA landmarks.

---

## 11) Acceptance Tests

### Global (applies across phases)
- **End-to-end Submission:** External user completes wizard with mandatory fields/documents → Intake → reviewers record decisions → signed decision letter (from Phase 1) → study activation (on approval).  
- **Document Traceability:** Upload, revise, supersede; audit logs verified for each action; export archive includes metadata and versions (from Phase 1).  
- **Workflow Config:** Admin adds a new step with SLA; items adopt SLA and appear in the target role’s queue (from Phase 1).  
- **Operations:** Subject enrollment; visit completion; AE capture; SAE notification; medication dispense/return reconciliation (Phase 2).  
- **RFI Loop:** Reviewer requests missing document; submitter uploads; task autocloses; workflow proceeds.  
- **Dashboards & Monitoring:** “My Tasks” shows up-to-date counts; actions update the boards promptly (polling in MVP; event-driven in Phase 2).

### MVP Go/No-Go (recap)
See **Phase 0** for detailed MVP criteria; all items must pass for pilot exit.

---

## 12) Assumptions & TBDs
- English-only UI initially; additional languages considered later.  
- Hosting as cloud SaaS: single-tenant per customer or logically isolated tenants (to be selected).  
- Data residency requirements provided prior to go-live.  
- Validation and testing aligned to customer SOPs; CSV activities formally tracked from Phase 1 onward.  
- **TBDs:** specific coding dictionaries, registry endpoints, and integration partner choices for Phase 3.

---

## 13) Glossary
- **AE/SAE:** Adverse Event / Serious Adverse Event  
- **CRF:** Case Report Form  
- **CTMS/EDC:** Clinical Trial Management System / Electronic Data Capture  
- **eTMF:** Electronic Trial Master File  
- **IRB/EC:** Institutional Review Board / Ethics Committee  
- **RBAC:** Role-Based Access Control  
- **RFI:** Request for Information
