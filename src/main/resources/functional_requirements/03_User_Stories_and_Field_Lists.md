# User Stories & Field Lists (v0.1)
*Status:* Draft  
*Scope:* Converts PRD epics into actionable user stories with acceptance criteria and detailed field lists.

---

## Legend
- **Priority:** P0 (must), P1 (should), P2 (could)  
- **AC:** Acceptance Criteria using Given/When/Then  
- **IDs:** US-<Epic>-NN

---

## Epic: Submission Portal (Studies, Clinical Trials, Research Projects)

### User Roles
- External Submitter (Sponsor, CRO, Investigator)
- Intake/Registry Admin
- Reviewer (Scientific, Ethics, Legal, Finance)
- System Admin

### User Stories
1. **US-SP-01 — Register & Sign In (External) [P0]**  
   As an *External Submitter*, I want to create an account and sign in with MFA so that I can submit studies securely.  
   **AC**  
   - Given I complete required registration fields, when I verify my email, then my account becomes active.  
   - Given I sign in with correct credentials, when prompted for MFA, then I can access my dashboard upon success.

2. **US-SP-02 — Guided Submission Wizard [P0]**  
   As an *External Submitter*, I want a guided wizard based on study type so that required fields and documents are clear.  
   **AC**  
   - Given I choose a study type, when I advance through steps, then only relevant fields/documents are requested.  
   - Given I attempt to submit with missing mandatory items, when validation runs, then I see a list of missing items and cannot submit.

3. **US-SP-03 — Document Attachments with Virus Scan [P0]**  
   As an *External Submitter*, I want to attach required documents so that my submission is complete.  
   **AC**  
   - Given I upload a file, when scanning completes, then the file appears with type, version, and checksum.  
   - Given scanning fails, when I retry, then the system logs attempts and blocks unsafe files.

4. **US-SP-04 — Submission Status & Timeline [P0]**  
   As an *External Submitter*, I want to see a status timeline and outstanding tasks so that I always know next steps.  
   **AC**  
   - Given my submission is under review, when I open its details, then I see current step, SLA due date, and pending RFIs.

5. **US-SP-05 — Intake Screening [P0]**  
   As an *Intake Admin*, I want to check completeness and route to reviewers so that reviews start promptly.  
   **AC**  
   - Given a new submission, when screening is complete, then routing follows the configured workflow template.

6. **US-SP-06 — Decision Letter Generation [P1]**  
   As a *Reviewer Chair*, I want decision letters from templates with e-signature so that outcomes are communicated consistently.  
   **AC**  
   - Given all approvals collected, when I generate a letter, then a signed PDF is stored and shared with the submitter.

### Field Lists (Submission Wizard)
**Submission**
- `submission_id` (UUID, readonly)  
- `title` (string, 5..200)  
- `study_type` (enum: "clinical_trial", "observational", "lab_research", "other")  
- `protocol_id` (string, unique within organization)  
- `phase` (enum: "I","II","III","IV","N/A")  
- `therapeutic_area` (string)  
- `sponsor_name` (string)  
- `principal_investigator` (string or reference to User/Investigator)  
- `sites` ([SiteRef])  
- `start_date` (date), `end_date` (date, >= start)  
- `funding_source` (string)  
- `risk_category` (enum: "low","moderate","high")  
- `keywords` ([string])  
- `declarations` (object: conflicts_of_interest:boolean, attestations:[string])  
- `attachments_required` ([DocumentType])  
- `attachments_provided` ([DocumentRef])  
- `status` (enum: "draft","submitted","intake","review","decision","closed") — readonly workflow state  
- `created_at`, `updated_at` (datetime, RFC3339)

**Document Metadata (common)**
- `document_id` (UUID)  
- `name` (string)  
- `type` (enum: "protocol","IB","ICF","CV","budget","insurance","ethics_approval","other")  
- `version_label` (string, e.g., "v1.0")  
- `status` (enum: "draft","final","superseded","withdrawn")  
- `effective_date`, `expiry_date` (date, optional)  
- `checksum_sha256` (string)  
- `file_size_bytes` (integer)  
- `content_type` (string, RFC 6838)  
- `classifications` ([enum: "PHI","PII","CONFIDENTIAL","PUBLIC"])  
- `retention_category` (enum)  
- `uploaded_by` (UserRef), `uploaded_at` (datetime)

---

## Epic: Digital Document Management (eTMF-style)

### User Stories
1. **US-DM-01 — Versioning & Supersede [P0]**  
   As a *Reviewer or Coordinator*, I want version chains so that history is preserved.  
   **AC**  
   - Given a doc has v1.0, when I upload v1.1 as a new version, then v1.0 remains viewable and v1.1 is the latest.

2. **US-DM-02 — Immutable Audit Log [P0]**  
   As a *Compliance Officer*, I want every action logged so that audits are supported.  
   **AC**  
   - Given any create/read/update/delete/download, when queried, then the audit trail shows who/what/when/why.

3. **US-DM-03 — Controlled Access & Watermark [P1]**  
   As a *System Admin*, I want access policies and optional watermarks so that sensitive docs are protected.

### Field Lists (Document Service)
- `document` (see above)  
- `document_version`  
  - `version_id` (UUID), `version_label` (string), `created_at` (datetime), `created_by` (UserRef), `change_note` (string)  
  - `file_ref` (opaque storage URI)  
- `audit_event`  
  - `event_id` (UUID), `entity_type` (string), `entity_id` (UUID), `action` (enum: "create","read","update","delete","download","version_create"), `actor` (UserRef), `timestamp`, `reason` (string)

---

## Epic: Configurable Workflows (Review & Approval)

### User Stories
1. **US-WF-01 — Template Designer [P0]**  
   As a *System Admin*, I want to define states, transitions, approvals, and SLAs so that workflows match our processes.  
   **AC**  
   - Given I add a state and transition, when I publish the template, then new submissions can adopt it.

2. **US-WF-02 — Parallel & Conditional Steps [P0]**  
   As a *Workflow Owner*, I want parallel reviews and conditional routing so that complex cases are handled.

3. **US-WF-03 — Escalations & OOO Reassignment [P1]**  
   As a *Reviewer Manager*, I want SLA escalations and out-of-office reassignment so that items do not stall.

### Field Lists (Workflow)
- `workflow_template`  
  - `template_id` (UUID), `name` (string), `version` (int)  
  - `states` ([{ `key`, `label`, `role_responsible`, `sla_hours`, `entry_checks` }])  
  - `transitions` ([{ `from_state`, `to_state`, `label`, `conditions`, `required_approvals` }])  
- `workflow_instance`  
  - `instance_id` (UUID), `template_id`, `entity_type` ("submission","study"), `entity_id` (UUID), `current_state`, `history` ([TransitionEvent])  
- `task`  
  - `task_id` (UUID), `type` (enum: "review","approval","rfi","document"), `assignee` (UserRef|RoleRef), `due_at`, `priority`, `status` (enum: "open","in_progress","blocked","done")

---

## Epic: Operational Trial Management (CTMS/EDC Lite)

### User Stories
1. **US-OP-01 — Study & Site Setup [P0]**  
   As a *Study Coordinator*, I want to define arms, visits, and procedures so that site operations are consistent.

2. **US-OP-02 — Subject Lifecycle [P0]**  
   As a *Site User*, I want to screen and enroll subjects so that I can track their progress through visits.

3. **US-OP-03 — Visit Tracking & Deviations [P0]**  
   As a *Coordinator*, I want planned vs actual visits and deviations so that compliance is visible.

4. **US-OP-04 — IP/Medication Accountability [P0]**  
   As a *Pharmacy User*, I want dispense/return and lot tracking so that inventory is accurate.

5. **US-OP-05 — AE/SAE Capture & Alerts [P0]**  
   As a *Safety Officer*, I want structured AE data and SAE alerts so that follow-up is timely.

6. **US-OP-06 — CRF Data Entry & Lock [P1]**  
   As a *Data Manager*, I want edit checks, queries, and data locks so that data quality is maintained.

### Field Lists
**Study**  
- `study_id` (UUID), `title` (string), `protocol_id` (string), `phase` (enum), `therapeutic_area` (string)  
- `arms` ([{ `arm_id` (UUID), `name` (string), `description` (string) }])  
- `visit_schedule` ([{ `visit_code` (string), `name` (string), `window_minus_days` (int), `window_plus_days` (int), `procedures` ([ProcedureRef]) }])  
- `sites` ([SiteRef])

**Site**  
- `site_id` (UUID), `name`, `address`, `principal_investigator` (UserRef), `timezone` (string)

**Subject**  
- `subject_id` (UUID), `study_id` (UUID), `screening_id` (string), `enrollment_date` (date), `status` (enum: "screening","enrolled","completed","withdrawn")  
- `demographics` (object: `age` (int), `sex_at_birth` (enum: "female","male","intersex","unknown"), `notes` (string))  
- `consent_status` (enum: "not_consented","consented","withdrawn"), `consent_date` (date, optional)

**Visit**  
- `visit_id` (UUID), `subject_id` (UUID), `visit_code` (string), `planned_date` (date), `actual_date` (date), `status` (enum: "planned","completed","missed")  
- `deviations` ([{ `code` (string), `description` (string), `severity` (enum: "minor","major") }])  
- `crf_data` (object JSON by CRF schema), `locked` (boolean)

**Medication / IP**  
- `lot_id` (UUID), `study_id` (UUID), `product_name` (string), `lot_number` (string), `expiry_date` (date), `quantity_on_hand` (int), `storage_conditions` (string)  
- `dispense` ([{ `dispense_id`, `subject_id`, `lot_id`, `date`, `quantity`, `performed_by` }])  
- `return` ([{ `return_id`, `subject_id`, `lot_id`, `date`, `quantity`, `reason` }])

**Adverse Event**  
- `ae_id` (UUID), `subject_id` (UUID), `onset_date` (date), `seriousness` (enum: "non_serious","serious"), `severity` (enum: "mild","moderate","severe")  
- `relatedness` (enum: "not_related","unlikely","possible","probable","definite","unknown")  
- `outcome` (enum: "recovered","recovering","not_recovered","fatal","unknown")  
- `action_taken` (string), `narrative` (string), `is_SAE` (boolean), `follow_up_due_date` (date)

**Data Query**  
- `query_id` (UUID), `entity_type` (enum: "subject","visit","crf","ae"), `entity_id` (UUID), `status` (enum: "open","answered","closed"), `question` (string), `answer` (string), `created_by`, `created_at`, `closed_at`

---

## Epic: Requests for Information (RFI)

### User Stories
1. **US-RFI-01 — Create RFI on Submission or Study [P0]**  
   As a *Reviewer*, I want to request clarifications/documents with due dates so that reviews proceed.  
2. **US-RFI-02 — Assignee Tasking & @Mentions [P1]**  
   As a *Coordinator*, I want to tag individuals and assign tasks so that responsibility is clear.

### Field Lists (RFI)
- `rfi_id` (UUID), `parent_type` (enum: "submission","study"), `parent_id` (UUID)  
- `title` (string), `message` (string, markdown supported)  
- `requested_documents` ([DocumentType]), `due_at` (datetime), `status` (enum: "open","answered","closed")  
- `participants` ([UserRef]), `thread` ([Message])  
- `created_by`, `created_at`, `updated_at`

**Message**  
- `message_id` (UUID), `rfi_id` (UUID), `author` (UserRef), `body` (string), `attachments` ([DocumentRef]), `created_at`

---

## Epic: Reporting & Statistics

### User Stories
1. **US-RP-01 — Prebuilt Dashboards [P0]**  
   As a *Manager*, I want cycle-time, workload, and compliance dashboards so that bottlenecks are visible.
2. **US-RP-02 — Ad-hoc Reports & Export [P1]**  
   As an *Analyst*, I want to build queries and schedule report deliveries so that insights are shared.

### Field Lists (Reporting)
- `report_definition`  
  - `report_id` (UUID), `name` (string), `description` (string), `dataset` (enum: "submissions","documents","workflow","operations")  
  - `metrics` ([MetricExpr]), `dimensions` ([string]), `filters` (object), `visualization` (enum: "table","chart")  
- `report_schedule`  
  - `schedule_id` (UUID), `report_id` (UUID), `cron` (string), `recipients` ([Email]), `format` (enum: "csv","xlsx","pdf")

---

## Epic: Real-Time Monitoring

### User Stories
1. **US-RT-01 — Live Status Boards [P1]**  
   As a *Reviewer Chair*, I want live boards that update automatically so that I see current workload.  
2. **US-RT-02 — My Tasks Panel [P0]**  
   As a *User*, I want a prioritized task list that updates in near real-time so that I stay on track.

### Field Lists (Events)
- `event`  
  - `event_id` (UUID), `type` (enum: "StatusChanged","TaskCreated","TaskUpdated","AEFlagged"), `entity_type`, `entity_id`, `occurred_at` (datetime), `payload` (object)

---

## Epic: Administration & Configuration

### User Stories
1. **US-AD-01 — RBAC & Permissions [P0]**  
   As a *System Admin*, I want roles and permissions so that users have least-privilege access.
2. **US-AD-02 — Master Data [P0]**  
   As an *Admin*, I want to manage study types, document taxonomies, and visit templates so that configuration is centralized.
3. **US-AD-03 — Retention & Legal Hold [P1]**  
   As a *Compliance Officer*, I want data retention settings and legal holds so that records are preserved appropriately.

### Field Lists (Admin)
- `role` (name, description, permissions[])  
- `permission` (key, description)  
- `user` (id, name, email, mfa_enabled, roles[])  
- `taxonomy` (id, type, values[])  
- `retention_policy` (id, category, duration_days, purge_action)  
- `workflow_template` (see Workflow section)

---

## Global Acceptance Tests (Representative)
- End-to-end submission to decision with required documents and SLA adherence.  
- Document version lifecycle with audit trail verification.  
- Workflow template change and application to new submission.  
- Subject enrollment, visit completion, AE creation (SAE alert), medication dispense/return reconciliation.  
- RFI issuance and closure through document upload.  
- Report creation and export; live board updates on status change.
