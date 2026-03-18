# Simplified Requirements for TMS (MVP v1.0)

## Objective
To build a lightweight, fast, and straightforward **Test Management System**. It covers the fundamental QA lifecycle: *"Write tests -> Group -> Execute -> Show results,"* while ensuring data integrity and professional reporting.

---

## Module 1: Workspace, Project Management & Access Control

### 1.1 Authentication & Isolation
* **Authentication:** The system uses predefined accounts instead of open registration. Access is restricted to hardcoded credentials for specific roles.
* **Data Isolation:** Multiple projects are supported. Each project acts as an isolated container.
    > **Note:** Every database query must be automatically restricted to a specific `Project ID` to prevent cross-project data leakage.

### 1.2 Role-Based Access Control (RBAC)
| Role | Permissions |
| :--- | :--- |
| **Admin** | Full CRUD (Create, Read, Update, Delete) for projects, suites, test cases, and test runs. |
| **Tester** | Read-only for the repository. Execute-only for test runs (status updates, attaching evidence, adding bug links). |

---

## Module 2: Test Repository (Management)

### 2.1 Structure & Organization
* **Grouping (Suites):** Test cases are organized into **single-level folders (Suites)**. No nested folder trees are allowed to maintain simplicity.
* **Deep Search:** A global search bar allows instant filtering by keywords across:
    * Titles & Descriptions
    * Pre-conditions
    * Step Actions & Expected Results

### 2.2 Test Case Structure
* **Header (Metadata):** Title, Description, Pre-conditions, and Priority (High, Medium, Low).
* **Steps (Array):** A list of sequential objects. Each step must contain:
    * `Action` (Mandatory)
    * `Expected Result` (Mandatory)
* **Attachments:** Integration with **EdgeMessage API** for uploading/retrieving files (images, JSON/XML, logs) at the case or step level.

### 2.3 Import & Export
* **Export:** Selected cases or suites can be exported to **CSV** or **XML**.
* **Import:** Admins can bulk-import test cases via **CSV** or **XML**, automatically generating cases and their nested steps.

---

## Module 3: Test Execution (Test Runs)

### 3.1 Run Creation & State
* **Snapshot Mechanism:** When a Test Run is created (Title, Environment, Case selection), the system creates a **snapshot** of the cases to preserve their state at the time of execution.

### 3.2 Granular Execution
* **Step-by-Step:** Testers mark each step as `Passed`, `Failed`, or `Skipped`.
* **Atomic Failure:** If any single step is marked as **Failed**, the entire Test Case automatically inherits the **Failed** status.

### 3.3 Failure Artifacts
* **Evidence:** Attach screenshots or logs directly to a specific step via EdgeMessage.
* **Bug Tracking:** A "Bug URL" field to link failed cases to external trackers (e.g., Jira).

### 3.4 Lifecycle Management
* **Lock:** Marking a run as "Completed" locks it into a **read-only** state for historical integrity.
* **Unlock:** Admins can "Unlock" a completed run to allow corrections or status updates.

---

## Module 4: Reporting & Metrics

### 4.1 Real-time Statistics
Inside each Test Run, the system tracks:
* **Total Count:** Number of all cases in the run.
* **Status Breakdown:** Passed / Failed / Untested (displayed in absolute numbers and percentages).

### 4.2 Report Export
Users can download results as **PDF** or **CSV**.
* **Included Data:** Execution metrics, failed case details, and associated Bug URLs.