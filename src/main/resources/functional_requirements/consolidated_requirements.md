# Detailed Functional Requirements — Test Management System (TMS) — MVP v1.0

Version: 1.0
Generated from: FR.md, arcthitecture.md, scope.md, userstories.md
Branch: 27539a19-2e7b-4184-96af-d93da7ea397e

1. Purpose

This document consolidates and expands the previously provided functional requirements into a single, detailed specification for the TMS MVP. It is intended as the single source of truth for design, implementation, QA, and acceptance.

2. Overview & Objectives

- Build a lightweight Test Management System covering: Write tests → Group → Execute → Report.
- Ensure strict project isolation and simple RBAC for two predefined roles: admin and tester.
- Support Suite-based organization (single-level), TestCase with ordered Steps, Test Runs as snapshots, attachments via EdgeMessage API, import/export utilities, and reporting/metrics.

3. Scope (In-scope / Out-of-scope)

In-scope:
- Single workspace with multiple Projects (isolated by Project ID).
- Authentication using two hardcoded accounts: admin and tester.
- Suite (single-level) and TestCase management with ordered TestSteps.
- Test Run creation with snapshot semantics and step-level execution.
- Attachments via EdgeMessage API integrated endpoints (/message/*).
- Import/Export: CSV and XML for cases and suites; Run export to PDF/CSV.
- Soft delete semantics for test cases.

Out-of-scope for MVP:
- Nested suite hierarchies.
- Open user registration, SSO, or multi-tenant authentication beyond hardcoded accounts.
- Complex RBAC beyond Admin and Tester roles.
- Third-party defect creation (only Bug URL linking supported).

4. Actors & Roles

- Admin
  - Privileges: Full CRUD for Projects, Suites, TestCases, TestRuns, Imports/Exports, Unlocking runs.
  - Typical actions: Create projects, import/export data, create suites/cases, soft-delete cases, unlock runs.

- Tester
  - Privileges: Read repository, execute runs (step-level updates), attach evidence, add Bug URLs during execution.
  - Typical actions: Run test executions, mark step statuses, upload evidence.

- System / Processors
  - SnapshotProcessor — creates immutable snapshots for runs.
  - AtomicFailureProcessor — aggregates step statuses into overall case status.
  - MetricsAggregator — computes run-level metrics on completion.
  - DataImportExportProcessor — handles import/export parsing and creation of entities.

5. Data Model (Entities)

Note: Concrete JSON instances will be created in the repo; below are the expected schemas.

- Project
  - id: UUID
  - name: string (unique per workspace)
  - description: string
  - createdBy: string
  - createdAt: timestamp

- Suite
  - id: UUID
  - projectId: UUID (FK)
  - title: string
  - description: string
  - createdAt: timestamp

- TestCase
  - id: UUID
  - suiteId: UUID (FK)
  - projectId: UUID (redundant for quick scoping)
  - title: string (required)
  - description: string
  - preConditions: string
  - priority: enum {HIGH, MEDIUM, LOW}
  - steps: array[TestStep] (ordered)
  - attachments: array[AttachmentMetadata]
  - softDeleted: boolean (default: false)
  - createdBy, createdAt, updatedAt

- TestStep
  - id: UUID
  - index: integer (1-based)
  - action: string (required)
  - expectedResult: string (required)
  - attachments: array[AttachmentMetadata]

- AttachmentMetadata
  - id: UUID
  - filename: string
  - contentType: string
  - edgeMessageId: string (link/reference to EdgeMessage API)
  - uploadedBy: string
  - uploadedAt: timestamp

- TestRun
  - id: UUID
  - projectId: UUID
  - title: string
  - environment: enum {STAGING, PRODUCTION}
  - createdBy: string
  - createdAt: timestamp
  - snapshotReference: string (pointer to snapshot bundle)
  - status: enum {ACTIVE, COMPLETED, LOCKED}
  - metrics: { total, passed, failed, untested, percentages }

- TestRunCase
  - id: UUID
  - runId: UUID
  - originalTestCaseId: UUID
  - title, steps[] (copied snapshot of TestCase at run start)
  - status: enum {PASSED, FAILED, UNTESTED}

- TestRunStep
  - id: UUID
  - runCaseId: UUID
  - originalStepId: UUID
  - index: integer
  - action: string
  - expectedResult: string
  - status: enum {PASSED, FAILED, SKIPPED, UNTESTED}
  - evidence: array[AttachmentMetadata]
  - bugUrl: string | null

6. Functional Requirements (Detailed)

Module 1 — Workspace, Projects & Access Control

FR 1.1 Authentication & Access
- FR 1.1.1: The system shall only permit login using two hardcoded user accounts:
  - admin (role: Admin)
  - tester (role: Tester)
- FR 1.1.2: Sessions shall be ephemeral (configurable TTL). Login attempts shall be rate-limited.
- FR 1.1.3: All API calls must include a token (session cookie or Authorization header) mapped to the hardcoded accounts.

FR 1.2 Project Isolation
- FR 1.2.1: The system shall support multiple Projects; every query must be scoped by Project ID.
- FR 1.2.2: Data from Project A must not be visible to Project B via UI or API responses.
- FR 1.2.3: Users may only operate in Projects to which they have been assigned (for MVP, assume both accounts can access any project created in workspace).

FR 1.3 Role-Based Permissions
- FR 1.3.1: Admins shall have full CRUD on Projects, Suites, TestCases, and TestRuns.
- FR 1.3.2: Testers shall have read-only access to repository entities and may create and modify TestRunStep execution statuses and attachments.
- FR 1.3.3: Attempted unauthorized operations shall return HTTP 403 with a clear error message.

Module 2 — Test Repository Management

FR 2.1 Suites
- FR 2.1.1: The system shall allow Admins to create Suites (single-level only).
- FR 2.1.2: Suites shall include title and description metadata.

FR 2.2 TestCases and Steps
- FR 2.2.1: TestCase header shall include Title (required), Description, Pre-conditions, and Priority (High/Medium/Low).
- FR 2.2.2: Each TestCase must contain an ordered array of TestSteps. Each TestStep requires Action and Expected Result.
- FR 2.2.3: Admins may create, update, soft-delete, and restore TestCases.

FR 2.3 Attachments (EdgeMessage)
- FR 2.3.1: The system shall support uploading attachments to TestCases and TestSteps via the EdgeMessage API endpoints located under /message/*.
- FR 2.3.2: Attachment metadata shall be persisted and linked to the relevant entity; the binary content resides in the EdgeMessage store referenced by edgeMessageId.
- FR 2.3.3: File types supported: images (png, jpg), documents (pdf, docx), structured data (json, xml, yaml), and log files.

FR 2.4 Import/Export
- FR 2.4.1: Admins shall be able to import TestCases (and their Steps) from CSV and XML files; parser errors shall be returned with line/row references.
- FR 2.4.2: Exports shall allow selected TestCases or entire Suites to be exported to CSV or XML, including all fields and nested steps.
- FR 2.4.3: Import operations shall perform validation and either create entities in a transaction or return a detailed error report; partial imports must be avoided unless explicitly requested.

FR 2.5 Search
- FR 2.5.1: The system shall provide a global search bar that matches keywords across Title, Description, Pre-conditions, Step Action, and Expected Result.
- FR 2.5.2: Search shall be case-insensitive and support partial matches.

FR 2.6 Soft Delete
- FR 2.6.1: Soft-deleted TestCases shall be hidden from repository listings and search results but retained in the database and preserved in any existing TestRun snapshots.
- FR 2.6.2: Admins shall be able to restore soft-deleted TestCases.

Module 3 — Test Execution

FR 3.1 Run Initialization & Snapshot
- FR 3.1.1: Admins and Testers shall be able to create TestRuns by selecting Suites or explicit TestCases and specifying a Title and Environment.
- FR 3.1.2: Creating a TestRun shall trigger SnapshotProcessor which copies the selected TestCases and associated Steps into the TestRunCase/TestRunStep structures (immutable snapshot).
- FR 3.1.3: Snapshot must include attachments references present at run start; subsequent changes to the source TestCase must not affect the snapshot.

FR 3.2 Step-Level Execution
- FR 3.2.1: During a TestRun, Testers shall be able to mark each TestRunStep as Passed, Failed, or Skipped.
- FR 3.2.2: Each step update must be auditable (who, when, previous status).

FR 3.3 Atomic Failure Logic
- FR 3.3.1: The system shall automatically set the TestRunCase status to Failed if any of its TestRunSteps are marked Failed.
- FR 3.3.2: If all steps are Passed, the TestRunCase is Passed; if any steps are Unexecuted or Skipped, follow defined business rules (e.g., presence of any Unexecuted counts towards Untested until explicitly set).

FR 3.4 Evidence & Bug Linking
- FR 3.4.1: Testers shall be able to upload evidence (screenshots, logs) via EdgeMessage and attach them to a specific TestRunStep.
- FR 3.4.2: Testers may associate an external Bug URL with a TestRunCase or TestRunStep; the system shall store the URL and display it in run reports.

FR 3.5 Run Locking & Unlocking
- FR 3.5.1: Marking a run as Completed shall lock it (becomes read-only), preventing further status updates unless unlocked.
- FR 3.5.2: Admins shall be able to Unlock a completed run to permit edits; unlocking shall be auditable and optionally require a reason.

Module 4 — Reporting & Metrics

FR 4.1 Real-time Metrics
- FR 4.1.1: The system shall compute and present the following for each TestRun:
  - Total number of TestRunCases
  - Number and percentage of Passed cases
  - Number and percentage of Failed cases
  - Number and percentage of Untested cases
- FR 4.1.2: Metrics shall update in near real-time as step statuses change.

FR 4.2 Run Export
- FR 4.2.1: Users shall be able to export TestRun results to PDF and CSV, including metrics, list of failed cases, step-level details, associated Bug URLs, and evidence references.

7. APIs & Endpoints (High-level)

Authentication
- POST /api/login — payload { username, password } returns session token

Projects
- POST /api/projects — create project (Admin)
- GET /api/projects — list projects
- GET /api/projects/{projectId}
- PUT /api/projects/{projectId}
- DELETE /api/projects/{projectId}

Suites
- POST /api/projects/{projectId}/suites
- GET /api/projects/{projectId}/suites
- GET /api/projects/{projectId}/suites/{suiteId}
- PUT /api/projects/{projectId}/suites/{suiteId}
- DELETE /api/projects/{projectId}/suites/{suiteId}

Test Cases
- POST /api/projects/{projectId}/suites/{suiteId}/cases
- GET /api/projects/{projectId}/suites/{suiteId}/cases
- GET /api/projects/{projectId}/cases/{caseId}
- PUT /api/projects/{projectId}/cases/{caseId}
- DELETE /api/projects/{projectId}/cases/{caseId} (soft-delete)
- POST /api/projects/{projectId}/cases/import — multipart/form-data (CSV/XML)
- GET /api/projects/{projectId}/cases/export?format=csv|xml&ids=...

Test Runs
- POST /api/projects/{projectId}/runs — create run (snapshot)
- GET /api/projects/{projectId}/runs
- GET /api/projects/{projectId}/runs/{runId}
- POST /api/projects/{projectId}/runs/{runId}/cases/{runCaseId}/steps/{runStepId}/status — {status, comment}
- POST /api/projects/{projectId}/runs/{runId}/complete — mark complete (locks)
- POST /api/projects/{projectId}/runs/{runId}/unlock — unlock run (Admin)
- GET /api/projects/{projectId}/runs/{runId}/export?format=pdf|csv

Attachments / EdgeMessage integration
- POST /api/projects/{projectId}/message/upload — proxy to EdgeMessage /message/upload
- GET /api/projects/{projectId}/message/{edgeMessageId}

Search
- GET /api/projects/{projectId}/search?q=...&type=cases|suites

8. Validation, Error Handling & Acceptance Criteria

- Input validation shall return 400 with detailed messages on missing/invalid fields.
- Unauthorized actions shall return 401/403 as appropriate.
- Importers must return detailed error reports with row/line numbers for CSV/XML failures.
- Snapshot creation must be atomic; partial snapshot failures roll back run creation.

Acceptance Criteria Samples
- AC: Creating a run for 3 test cases results in a snapshot containing exact copies of the cases and each case is editable only within the run.
- AC: Marking a single step as Failed must result in the parent case in that run being marked Failed.
- AC: Soft-deleted test cases must not appear in search results but must be present and visible in any existing run snapshots where they were included before deletion.

9. Non-Functional Requirements

Performance
- R-NF 1: The system shall support 500 concurrent users for read-heavy operations with <200ms median response time for read endpoints under typical load.
- R-NF 2: Snapshot creation for a run with 1000 cases should complete within 30 seconds in nominal conditions.

Reliability & Availability
- R-NF 3: The system should be resilient to transient errors when calling the EdgeMessage service; retries with backoff should be implemented for uploads.

Security
- R-NF 4: Sensitive fields (e.g., session tokens) must never be logged. All API traffic must be TLS-encrypted.
- R-NF 5: Input sanitization must be applied to prevent injection via CSV/XML imports or uploaded content.

Observability
- R-NF 6: The system shall emit structured logs for key actions (login, run creation, import/export, lock/unlock) and expose basic metrics (API latency, error rates, snapshot durations).

10. Implementation Constraints & Notes

- Use the EdgeMessage API for all attachments; keep binary storage outside the TMS DB.
- Use transactions for import and snapshot operations to prevent partial state.
- Keep Suites single-level only (no nested suites).
- For MVP assume both hardcoded users may access projects; future enhancements can add user-to-project assignment.

11. Acceptance Test Plan (high-level)

- Create Project, Suite, and TestCases with steps → Verify creation and retrieval.
- Import a CSV with 10 cases → Validate parsing and created entities match CSV data.
- Create Run from selected cases → Verify snapshot is created and source case edits do not affect run contents.
- Execute run steps marking one as Failed → Verify case becomes Failed and metrics update.
- Attach evidence to step via EdgeMessage → Verify metadata stored and file retrievable via EdgeMessage endpoint.
- Soft-delete a TestCase included in an existing run → Verify it disappears from repository view but remains visible inside run snapshot.

12. Appendix: Example Validation Rules for Import

- CSV columns required: suiteTitle, caseTitle, caseDescription, preConditions, priority, step1Action, step1Expected, step2Action, step2Expected, ...
- Invalid priority values: reject import line with error.
- Duplicate caseTitle within a suite: treat as validation error unless explicit deduplication flag provided.

---

End of detailed functional requirements (detailed_requirements.md)
