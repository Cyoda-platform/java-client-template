# Test Management System (TMS) — MVP v1.0 Specification

## 1. Project Objective
To build a lightweight, fast, and straightforward Test Management System. It covers the fundamental QA lifecycle: **"Write tests -> Group -> Execute -> Show results,"** while ensuring data integrity and professional reporting.

---

## 2. General Requirements & Modules

### Module 1: Workspace & Access Control
* **Authentication:** Restricted to predefined accounts (Admin/Tester). No open registration.
* **Data Isolation:** Projects act as isolated containers. Database queries are restricted by `Project ID`.
* **RBAC:**
    * **Admin:** Full CRUD for all entities.
    * **Tester:** Read-only for repository; execute-only for test runs.

### Module 2: Test Repository
* **Grouping:** Single-level Suites (no nested folders).
* **Structure:** Title, Description, Pre-conditions, Priority, and an Array of Steps (Action + Expected Result).
* **Attachments:** Managed via EdgeMessage API.
* **Search:** Deep search across all text fields (Titles, Steps, etc.).
* **Import/Export:** Support for CSV and XML formats.

### Module 3: Test Execution
* **Snapshots:** Creating a Run captures an immutable version of the test cases.
* **Atomic Failure:** If one step fails, the entire case is marked as "Failed."
* **Evidence:** Attach logs/screenshots to specific steps. Link external Bug URLs.
* **Locking:** Completed runs are read-only unless unlocked by an Admin.

---

## 3. Functional Requirements (FR)

### Workspace & Projects
* **FR 1.1:** Restrict access to hardcoded `admin` and `tester` accounts.
* **FR 1.2:** All operations must occur within a single workspace context.
* **FR 1.3:** Strict data isolation between projects.

### Management
* **FR 2.1:** Support for single-level Suites only.
* **FR 2.2:** Mandatory Title and Step fields (Action/Expected Result).
* **FR 2.3:** Soft Delete: Hidden from UI but preserved in historical Run snapshots.
* **FR 2.4:** Deep keyword search across all test case metadata.

### Execution & Reporting
* **FR 3.1:** Step-level status tracking (Passed, Failed, Skipped).
* **FR 3.2:** Automatic status aggregation (Atomic Failure logic).
* **FR 3.3:** Real-time metrics calculation (Total, %, Status counts).
* **FR 3.4:** Export execution reports to PDF/CSV.

---

## 4. Agile User Stories

### Epic: Workspace
* **US 1.1:** As a user, I want to log in with predefined credentials for simplicity.
* **US 1.2:** As an Admin, I want to create isolated projects to separate different products.

### Epic: Repository
* **US 2.1:** As an Admin, I want to create structured test cases with sequential steps for clarity.
* **US 2.2:** As any user, I want to use deep search to find tests by specific actions or expected results.
* **US 2.3:** As an Admin, I want to bulk import cases from CSV to speed up migration.

### Epic: Execution
* **US 3.1:** As a Tester, I want to execute runs based on a snapshot to ensure data integrity.
* **US 3.2:** As a Tester, I want to link Bug URLs to failed steps for better traceability.

---

## 5. System Architecture & Workflows

### 5.1 Entities
* **Project:** Isolated data container.
* **Suite:** Logical grouping (No nesting).
* **TestCase / TestStep:** The "Golden Master" in the repository.
* **TestRunCase / TestRunStep:** The "Snapshot" used for execution.
* **Attachment:** File metadata linked via EdgeMessage API.

### 5.2 Key Workflows

#### TestRun Lifecycle
| Transition | Actor | Rules |
| :--- | :--- | :--- |
| **Initialize Run** | Admin/Tester | Triggers `SnapshotProcessor`. |
| **Update Step** | Tester | Triggers `AtomicFailureProcessor`. |
| **Complete Run** | Admin/Tester | Locks data; triggers `MetricsAggregator`. |
| **Unlock Run** | Admin | Allows modifications to a locked run. |

#### Case Management
| Transition | Actor | Rules |
| :--- | :--- | :--- |
| **Soft Delete** | Admin | Hides entity; `ProjectIsolationCheck` required. |
| **Import Data** | Admin | Uses `DataImportExportProcessor`. |

#### Attachments
| Transition | Actor | Rules |
| :--- | :--- | :--- |
| **Upload File** | Admin/Tester | Integrated with `/message/*` API via `EdgeMessageProcessor`. |