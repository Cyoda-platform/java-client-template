# Agile User Stories (Final MVP Version)

## Epic 1: Workspace & Security

* **US 1.1: Predefined Access (No Registration)**
    * **As a** system user,
    * **I want to** log in using one of the two predefined accounts (admin or tester),
    * **So that** the system remains simple and secure without the overhead of public registration.
* **US 1.2: User Roles & Permissions**
    * **As a** logged-in user,
    * **I want** the system to enforce my role-based permissions (Admin: full CRUD; Tester: read/execute only),
    * **So that** the test repository is protected from unauthorized changes by non-admin users.
* **US 1.3: Create Isolated Project**
    * **As an** Admin,
    * **I want to** create a new isolated project workspace,
    * **So that** I can keep test data for different products strictly separated.
    * **Acceptance Criteria:** Data from Project A is never visible to users working in Project B.

---

## Epic 2: The Test Repository

* **US 2.1: Single-Level Suites**
    * **As an** Admin,
    * **I want to** group test cases into simple, non-nested folders (Suites),
    * **So that** the team can navigate the repository without complex hierarchies.
* **US 2.2: Structured Test Cases & Sequential Steps**
    * **As an** Admin,
    * **I want to** create test cases with a header (Title, Description, Pre-conditions, Priority) and a list of sequential steps (Action and Expected Result),
    * **So that** testers have clear, granular instructions for execution.
* **US 2.3: Attachments via EdgeMessage**
    * **As an** Admin,
    * **I want to** upload reference files (images, logs, configs) to a Test Case using the EdgeMessage API,
    * **So that** all necessary materials are stored securely in the cloud and accessible during execution.
* **US 2.4: Deep Search**
    * **As** any user,
    * **I want to** search for test cases using keywords that match titles, descriptions, pre-conditions, or any text within steps,
    * **So that** I can find relevant tests even if I only remember a specific action or expected result.
* **US 2.5: Bulk Import/Export**
    * **As an** Admin,
    * **I want to** import and export test cases via CSV/XML (including all steps),
    * **So that** I can quickly migrate data or create backups.

---

## Epic 3: Test Execution (The Player)

* **US 3.1: Initialize Targeted Run**
    * **As a** Tester or Admin,
    * **I want to** create a Test Run by selecting specific suites or cases and defining the environment,
    * **So that** I can start a focused QA cycle based on a "snapshot" of the current test repository.
* **US 3.2: Step-Level Validation (Atomic Failure)**
    * **As a** Tester,
    * **I want to** mark individual steps as Passed, Failed, or Skipped,
    * **So that** the system can automatically fail the entire test case if any single step fails.
* **US 3.3: Execution Evidence & Bug Linking**
    * **As a** Tester,
    * **I want to** attach screenshots/logs to a specific step and link a Bug URL to a failed case,
    * **So that** developers have all the context needed to fix the issue.
* **US 3.4: Lock and Unlock Test Runs**
    * **As an** Admin or Tester,
    * **I want to** mark a run as "Completed" to lock it, but also have the ability to "Unlock" it if corrections are needed,
    * **So that** we maintain a balance between data integrity and flexibility.

---

## Epic 4: Reporting

* **US 4.1: Real-time Progress Metrics**
    * **As a** user,
    * **I want to** see an instant summary (Total, Passed, Failed, Untested) in units and percentages within a run,
    * **So that** I can quickly assess the current quality status of the release.
* **US 4.2: Exportable Execution Reports**
    * **As a** user,
    * **I want to** download a PDF or CSV report of the Test Run results,
    * **So that** I can share the final quality sign-off with stakeholders.