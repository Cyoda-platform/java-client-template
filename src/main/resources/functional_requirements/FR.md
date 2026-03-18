# Functional Requirements: Test Management System (TMS)

## Module 1: Workspace, Access Control & Projects

* **FR 1.1 Authentication (Predefined):** The system shall not support open registration. Access is restricted to two hardcoded accounts: `admin` (Role: Admin) and `tester` (Role: Tester) with fixed passwords.
* **FR 1.2 Workspace Context:** All operations shall be performed within the context of a single workspace.
* **FR 1.3 Project Management:** Users with the **Admin** role shall be able to Create, Read, Update, and Delete (CRUD) isolated Projects.
* **FR 1.4 Data Isolation:** The system shall strictly restrict data visibility (suites, test cases, runs) to the scope of a single Project. Cross-project data access is prohibited.
* **FR 1.5 Role Permissions:**
    * **Admin:** Full access (CRUD) to all entities within the assigned project.
    * **Tester:** Read-only access to the Test Repository and permission to execute Test Runs. Creation or deletion of entities is prohibited.

---

## Module 2: Test Repository (Management)

* **FR 2.1 Suite Management:** Admins shall be allowed to create single-level folders (**Suites**) to group test cases. Nested folders are strictly prohibited.
* **FR 2.2 Test Case Header:** A Test Case must contain a header with: a mandatory **Title**, a **Description**, a **Pre-conditions** text field, and a **Priority** level (High, Medium, Low).
* **FR 2.3 Step Management (Array-based):** The system shall allow Admins to add multiple sequential steps to a Test Case. Each step is a distinct object containing two mandatory fields: **"Action"** and **"Expected Result"**.
* **FR 2.4 Multi-format Attachments (EdgeMessage):** The system shall support uploading various file formats (images, JSON/XML/YAML, documents) via integration with the **EdgeMessage API** (endpoints under `/message/*`). Files shall be linked to test cases or execution steps.
* **FR 2.5 Test Case Export:** Users shall be able to export selected cases or entire suites into **CSV** or **XML** formats, including all metadata and nested step data.
* **FR 2.6 Test Case Import:** Admins shall be able to bulk-import test cases from CSV/XML files. The system must parse the files and automatically generate the TestCase and Step entities within the active Project.
* **FR 2.7 Deep Search:** The system shall provide a global search bar matching keywords against Titles, Descriptions, Pre-conditions, Step Actions, and Expected Results.
* **FR 2.8 Soft Delete:** The system shall implement **"Soft Delete"** for test cases. Deleted cases shall be hidden from the repository and search results but remain preserved in existing Test Run snapshots to maintain historical integrity.

---

## Module 3: Test Execution

* **FR 3.1 Run Initialization:** Admins and Testers can create a Test Run by providing a Title, Environment (Staging/Production), and selecting suites or individual cases. The system shall create a **"snapshot"** of the test cases and their steps at the start of execution.
* **FR 3.2 Step-Level Execution:** During execution, the system shall present each step as an interactable item. Testers shall be able to set an individual status (**Passed**, **Failed**, or **Skipped**) for each specific step.
* **FR 3.3 Atomic Failure Logic:** The system shall automatically aggregate step statuses. If any single step within a Test Case is marked as **Failed**, the overall Test Case status for that run shall be set to **Failed**.
* **FR 3.4 Execution Artifacts:** Testers shall be able to upload evidence (screenshots, logs) via EdgeMessage directly linked to a specific execution step.
* **FR 3.5 Defect Linking:** The system shall provide a **"Bug URL"** field to save direct links to external bug trackers (e.g., Jira, GitHub) for failed test cases.
* **FR 3.6 Run Locking & Unlocking:**
    * **Lock:** Upon marking a run as "Completed", the system shall lock it into a **read-only** state.
    * **Unlock:** The system shall provide an **"Unlock"** function to return a completed run to an active state, allowing status updates and corrections.

---

## Module 4: Reporting & Metrics

* **FR 4.1 Real-time Metrics:** The system shall automatically calculate aggregate statuses within a run: **Total count**, **Passed**, **Failed**, and **Untested** (both in absolute numbers and percentages).
* **FR 4.2 Test Run Export:** Users shall be able to export Test Run results into **PDF** or **CSV**, including metrics and a list of failed cases with their associated Bug URLs and step-level details.