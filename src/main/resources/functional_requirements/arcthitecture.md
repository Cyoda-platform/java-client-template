# TMS Architecture (MVP v1.0) for Cyoda AI

---

## 1. Entities

* **User**: System user. Registration is disabled; access is restricted to predefined admin and tester accounts with fixed passwords.
* **Project**: Isolated container where all data is strictly bound to a Project ID to ensure no cross-project leakage.
* **Suite**: Single-level folder used to group test cases within a project; nested folders are prohibited.
* **TestCase**: Core repository entity containing a Title, Description, Pre-conditions, and Priority (High, Medium, Low).
* **TestStep**: A specific, sequential step within a TestCase consisting of an Action and an Expected Result.
* **Attachment**: Record of an uploaded file (image, log, etc.) containing the URL generated via the EdgeMessage API.
* **TestRun**: Execution instance for a set of tests, tracking the Title, Environment, and aggregated real-time metrics.
* **TestRunCase**: A "snapshot" copy of a test case at the start of a run, including a Bug URL field.
* **TestRunStep**: A "snapshot" copy of a step for execution, tracking individual statuses (Passed, Failed, Skipped, Untested).

---

## 2. Integrated Workflows

### Workflow: Project
* **States**: Active, Deleted.
* **Transitions**: Create Project, Update Project, Delete Project, Import Test Data.
* **Rules**:
    * **Criteria**: RequireAdminRole.
    * **Processor**: DataImportExportProcessor (for Import Test Data).

### Workflow: Suite
* **States**: Active, Deleted.
* **Transitions**: Create Suite, Update Suite, Delete Suite.
* **Rules**:
    * **Criteria**: RequireAdminRole, ProjectIsolationCheck, PreventNestedSuites.

### Workflow: TestCase & TestStep
* **States**: Active, Deleted (Soft Delete).
* **Transitions**: Create, Update, Soft Delete (hides cases from results while preserving history in existing runs).
* **Rules**:
    * **Criteria**: RequireAdminRole, ProjectIsolationCheck.

### Workflow: TestRun
* **States**: Active, Completed (Locked).
* **Transitions**: Initialize Run, Complete Run, Unlock Run.
* **Rules**:
    * **Criteria**: RequireTesterOrAdminRole (for Initialize/Complete), RequireAdminRole (for Unlock), ProjectIsolationCheck.
    * **Processors**: SnapshotProcessor, MetricsAggregatorProcessor.

### Workflow: TestRunStep
* **States**: Untested, Passed, Failed, Skipped.
* **Transitions**: Update Status (allows testers to set granular step results).
* **Rules**:
    * **Criteria**: RequireTesterOrAdminRole, RunIsNotLocked.
    * **Processor**: AtomicFailureProcessor.

### Workflow: TestRunCase
* **States**: Untested, Passed, Failed, Skipped.
* **Transitions**: Update Case Status (Automatic), Link Bug Defect.
* **Rules**:
    * **Criteria**: SystemActionOnly (for Update Case Status), RequireTesterOrAdminRole (for Link Bug), RunIsNotLocked.

### Workflow: Attachment
* **States**: Active, Deleted.
* **Transitions**: Upload File, Delete File.
* **Rules**:
    * **Criteria**: RequireTesterOrAdminRole, RunIsNotLocked.
    * **Processor**: EdgeMessageProcessor.

---

## 3. Workflow Details Tables

### 1. Workflow: Project
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Create Project** | Manual | Initializes a new isolated project workspace. | Criteria: RequireAdminRole. |
| **Update Project** | Manual | Modifies project name or description. | Criteria: RequireAdminRole. |
| **Delete Project** | Manual | Removes the project container from active view. | Criteria: RequireAdminRole. |
| **Import Test Data** | Manual | Triggers the bulk generation of TestCases and Steps from external files. | Criteria: RequireAdminRole. Processor: DataImportExportProcessor. |

### 2. Workflow: Suite
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Create Suite** | Manual | Creates a new folder within a project. | Criteria: RequireAdminRole, ProjectIsolationCheck, PreventNestedSuites. |
| **Update Suite** | Manual | Renames or modifies suite details. | Criteria: RequireAdminRole, ProjectIsolationCheck. |
| **Delete Suite** | Manual | Removes the suite. | Criteria: RequireAdminRole, ProjectIsolationCheck. |

### 3. Workflow: TestCase & TestStep
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Create** | Manual | Defines a new case with header data and sequential steps. | Criteria: RequireAdminRole, ProjectIsolationCheck. |
| **Update** | Manual | Modifies the case metadata or step content. | Criteria: RequireAdminRole, ProjectIsolationCheck. |
| **Soft Delete** | Manual | Hides the case while keeping it in existing run snapshots. | Criteria: RequireAdminRole, ProjectIsolationCheck. |

### 4. Workflow: TestRun
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Initialize Run** | Manual | Starts a new run and captures a repository snapshot. | Criteria: RequireTesterOrAdminRole, ProjectIsolationCheck. Processor: SnapshotProcessor. |
| **Complete Run** | Manual | Marks execution as finished and locks the data. | Criteria: RequireTesterOrAdminRole. Processor: MetricsAggregatorProcessor. |
| **Unlock Run** | Manual | Returns a locked run to an active state for edits. | Criteria: RequireAdminRole. |

### 5. Workflow: TestRunStep
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Update Status** | Manual | Testers set the step to Passed, Failed, or Skipped. | Criteria: RequireTesterOrAdminRole, RunIsNotLocked. Processor: AtomicFailureProcessor. |

### 6. Workflow: TestRunCase
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Update Case Status** | Automatic | Changes status based on aggregated step results. | Criteria: SystemActionOnly. |
| **Link Bug Defect** | Manual | Adds a URL to an external bug tracker. | Criteria: RequireTesterOrAdminRole, RunIsNotLocked. |

### 7. Workflow: Attachment
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Upload File** | Manual | Uploads and links a file via EdgeMessage. | Criteria: RequireTesterOrAdminRole, RunIsNotLocked. Processor: EdgeMessageProcessor. |
| **Delete File** | Manual | Removes an attachment record. | Criteria: RequireTesterOrAdminRole, RunIsNotLocked. |

---

## 4. User Story Coverage

| User Story | Status | Cyoda Architecture Coverage (Entity / Workflow / Criteria / Processor) |
| :--- | :--- | :--- |
| **US 1.1: Predefined Access (No Registration)** | ✅ Covered | Entity: User. Seed Data creates accounts upon deployment. |
| **US 1.2: User Roles & Permissions** | ✅ Covered | Criteria: RequireAdminRole and RequireTesterOrAdminRole on transitions. |
| **US 1.3: Create Isolated Project (Data isolation)** | ✅ Covered | Entity: Project. Workflow: Create Project. Criteria: ProjectIsolationCheck. |
| **US 2.1: Single-Level Suites** | ✅ Covered | Entity: Suite. Workflow: Create Suite. Criteria: PreventNestedSuites. |
| **US 2.2: Structured Test Cases & Sequential Steps** | ✅ Covered | Entities: TestCase, TestStep. Workflows: Create, Update. |
| **US 2.3: Attachments via EdgeMessage** | ✅ Covered | Entity: Attachment. Workflow: Upload File. Processor: EdgeMessageProcessor. |
| **US 2.4: Deep Search** | ✅ Covered | Handled by Read API (Queries), filtered by project context. |
| **US 2.5: Bulk Import/Export** | ✅ Covered | Workflow: Import Test Data. Processor: DataImportExportProcessor. |
| **US 3.1: Initialize Targeted Run** | ✅ Covered | Entity: TestRun. Workflow: Initialize Run. Processor: SnapshotProcessor. |
| **US 3.2: Step-Level Validation (Atomic Failure)** | ✅ Covered | Workflows: Update Status (step), Update Case Status (case). Processor: AtomicFailureProcessor. |
| **US 3.3: Execution Evidence & Bug Linking** | ✅ Covered | Workflows: Upload File, Link Bug Defect. Criteria: RunIsNotLocked. |
| **US 3.4: Lock and Unlock Test Runs** | ✅ Covered | Workflows: Complete Run, Unlock Run. Criteria: RequireAdminRole for unlocking. |
| **US 4.1: Real-time Progress Metrics** | ✅ Covered | Processor: MetricsAggregatorProcessor. |
| **US 4.2: Exportable Execution Reports** | ✅ Covered | Dynamic PDF/CSV generation via Read API requests. |