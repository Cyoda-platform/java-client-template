# [cite_start]TMS Architecture (MVP v1.0) for Cyoda AI 

## [cite_start]1. Entities [cite: 2]

* [cite_start]**User**: System user. [cite: 3] [cite_start]Registration is disabled; access is restricted to predefined admin and tester accounts with fixed passwords. [cite: 3]
* [cite_start]**Project**: Isolated container where all data is strictly bound to a Project ID to ensure no cross-project leakage. [cite: 4]
* [cite_start]**Suite**: Single-level folder used to group test cases within a project; nested folders are prohibited. [cite: 5]
* [cite_start]**TestCase**: Core repository entity containing a Title, Description, Pre-conditions, and Priority (High, Medium, Low). [cite: 6]
* [cite_start]**TestStep**: A specific, sequential step within a TestCase consisting of an Action and an Expected Result. [cite: 7]
* [cite_start]**Attachment**: Record of an uploaded file (image, log, etc.) containing the URL generated via the EdgeMessage API. [cite: 8]
* [cite_start]**TestRun**: Execution instance for a set of tests, tracking the Title, Environment, and aggregated real-time metrics. [cite: 9]
* [cite_start]**TestRunCase**: A "snapshot" copy of a test case at the start of a run, including a Bug URL field. [cite: 10]
* [cite_start]**TestRunStep**: A "snapshot" copy of a step for execution, tracking individual statuses (Passed, Failed, Skipped, Untested). [cite: 11]

---

## [cite_start]2. Integrated Workflows [cite: 12]

### [cite_start]Workflow: Project [cite: 13]
* [cite_start]**States**: Active, Deleted. [cite: 14]
* **Transitions**: 
    * [cite_start]Create Project (Initializes workspace) [cite: 17][cite_start], Update Project [cite: 18][cite_start], Delete Project[cite: 20].
    * [cite_start]Import Test Data (Bulk-generates entities from CSV/XML). [cite: 21]
* **Rules**: 
    * [cite_start]**Criteria**: RequireAdmin Role. [cite: 23]
    * [cite_start]**Processor**: DataImport Export Processor. [cite: 25]

### [cite_start]Workflow: Suite [cite: 26]
* [cite_start]**States**: Active, Deleted. [cite: 27]
* [cite_start]**Transitions**: Create Suite, Update Suite, Delete Suite. [cite: 28]
* **Rules**: 
    * [cite_start]**Criteria**: RequireAdmin Role, ProjectIsolationCheck, PreventNestedSuites. [cite: 31]

### [cite_start]Workflow: TestCase & TestStep [cite: 32]
* [cite_start]**States**: Active, Deleted (Soft Delete). [cite: 33]
* [cite_start]**Transitions**: Create, Update, Soft Delete. [cite: 34]
* **Rules**: 
    * [cite_start]**Criteria**: RequireAdmin Role, ProjectIsolationCheck. [cite: 36]

### [cite_start]Workflow: TestRun [cite: 37]
* [cite_start]**States**: Active, Completed (Locked). [cite: 38]
* [cite_start]**Transitions**: Initialize Run [cite: 41][cite_start], Complete Run [cite: 42][cite_start], Unlock Run. [cite: 43]
* **Rules**: 
    * [cite_start]**Criteria**: Require Tester OrAdminRole (for Initialize/Complete), RequireAdmin Role (for Unlock), ProjectIsolationCheck. [cite: 45]
    * [cite_start]**Processors**: Snapshot Processor, MetricsAggregator Processor. [cite: 46]

### [cite_start]Workflow: TestRunStep [cite: 47]
* [cite_start]**States**: Untested, Passed, Failed, Skipped. [cite: 48]
* [cite_start]**Transitions**: Update Status. [cite: 49]
* **Rules**: 
    * [cite_start]**Criteria**: Require Tester OrAdmin Role, RunIsNotLocked. [cite: 52]
    * [cite_start]**Processor**: Atomic Failure Processor (fails case if any step fails). [cite: 53]

### [cite_start]Workflow: TestRunCase [cite: 54]
* [cite_start]**States**: Untested, Passed, Failed, Skipped. [cite: 55]
* [cite_start]**Transitions**: Update Case Status (Automatic) [cite: 57][cite_start], Link Bug Defect. [cite: 58]
* **Rules**: 
    * [cite_start]**Criteria**: SystemActionOnly (for Status), Require TesterOrAdmin Role (for Link Bug), Runis NotLocked. [cite: 60]

### [cite_start]Workflow: Attachment [cite: 61]
* [cite_start]**States**: Active, Deleted. [cite: 62]
* [cite_start]**Transitions**: Upload File, Delete File. [cite: 63]
* **Rules**: 
    * [cite_start]**Criteria**: Require Tester OrAdmin Role, RunIsNotLocked. [cite: 66]
    * [cite_start]**Processor**: EdgeMessage Processor (/message/* API). [cite: 67]

---

## 3. Detailed Workflow Tables

### [cite_start]1. Workflow: Project [cite: 68, 69]
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Create Project** | [cite_start]Manual [cite: 70] | [cite_start]Initializes a new isolated project workspace. [cite: 70] | [cite_start]Criteria: RequireAdmin Role. [cite: 70] |
| **Update Project** | [cite_start]Manual [cite: 70] | [cite_start]Modifies project name or description. [cite: 70] | [cite_start]Criteria: RequireAdmin Role. [cite: 70] |
| **Delete Project** | [cite_start]Manual [cite: 70] | [cite_start]Removes the project container from active view. [cite: 70] | [cite_start]Criteria: RequireAdmin Role. [cite: 70] |
| **Import Test Data**| [cite_start]Manual [cite: 70] | [cite_start]Triggers the bulk generation of TestCases and Steps from external files. [cite: 70] | [cite_start]Processor: DataImportExportProcessor. [cite: 70] |

### [cite_start]2. Workflow: Suite [cite: 71, 72]
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Create Suite** | [cite_start]Manual [cite: 73] | [cite_start]Creates a new folder within a project. [cite: 73] | [cite_start]Criteria: RequireAdmin Role, ProjectIsolationCheck, Prevent Nested Suites. [cite: 73] |
| **Update Suite** | [cite_start]Manual [cite: 73] | [cite_start]Renames or modifies suite details. [cite: 73] | [cite_start]Criteria: RequireAdmin Role, ProjectIsolationCheck. [cite: 73] |
| **Delete Suite** | [cite_start]Manual [cite: 73] | [cite_start]Removes the suite. [cite: 73] | [cite_start]Criteria: RequireAdminRole, ProjectIsolationCheck. [cite: 73] |

### [cite_start]3. Workflow: TestCase & TestStep [cite: 74, 75]
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Create** | [cite_start]Manual [cite: 76] | [cite_start]Defines a new case with header data and sequential steps. [cite: 76] | [cite_start]Criteria: RequireAdminRole, ProjectIsolationCheck. [cite: 76] |
| **Update** | [cite_start]Manual [cite: 76] | [cite_start]Modifies the case metadata or step content. [cite: 76] | [cite_start]Criteria: Require Admin Role, ProjectIsolationCheck. [cite: 76] |
| **Soft Delete** | [cite_start]Manual [cite: 76] | [cite_start]Hides case while keeping history in run snapshots. [cite: 76] | [cite_start]Criteria: RequireAdminRole, ProjectIsolationCheck. [cite: 76] |

### [cite_start]4. Workflow: TestRun [cite: 77, 78]
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Initialize Run** | [cite_start]Manual [cite: 79] | [cite_start]Starts a run and captures a repository snapshot. [cite: 79] | Criteria: Require Tester OrAdminRole, ProjectIsolationCheck. [cite_start]Processor: Snapshot Processor. [cite: 79] |
| **Complete Run** | [cite_start]Manual [cite: 79] | [cite_start]Marks execution as finished and locks data. [cite: 79] | Criteria: Require Tester OrAdminRole. [cite_start]Processor: MetricsAggregator Processor. [cite: 79] |
| **Unlock Run** | [cite_start]Manual [cite: 79] | [cite_start]Returns a locked run to an active state for edits. [cite: 79] | [cite_start]Criteria: RequireAdminRole. [cite: 79] |

### [cite_start]5. Workflow: TestRunStep [cite: 80, 81]
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Update Status** | [cite_start]Manual [cite: 82] | [cite_start]Testers set step to Passed, Failed, or Skipped. [cite: 82] | Criteria: Require Tester OrAdminRole, RunIsNotLocked. [cite_start]Processor: Atomic Failure Processor. [cite: 82] |

### [cite_start]6. Workflow: TestRunCase [cite: 83, 84]
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Update Case Status** | [cite_start]Automatic [cite: 85] | [cite_start]Changes status based on aggregated step results. [cite: 85] | [cite_start]Criteria: SystemActionOnly. [cite: 85] |
| **Link Bug Defect** | [cite_start]Manual [cite: 85] | [cite_start]Adds a URL to an external bug tracker. [cite: 85] | [cite_start]Criteria: Require TesterOrAdminRole, Runis NotLocked. [cite: 85] |

### [cite_start]7. Workflow: Attachment [cite: 86, 87]
| Transition | Type | Description | Rules (Criteria & Processors) |
| :--- | :--- | :--- | :--- |
| **Upload File** | [cite_start]Manual [cite: 88] | [cite_start]Uploads and links a file via EdgeMessage. [cite: 88] | Criteria: Require Tester OrAdminRole, RunlsNotLocked. [cite_start]Processor: Edge Message Processor. [cite: 88] |
| **Delete File** | [cite_start]Manual [cite: 88] | [cite_start]Removes an attachment record. [cite: 88] | [cite_start]Criteria: Require Tester OrAdminRole, RunIsNotLocked. [cite: 88] |

---

## [cite_start]4. User Story Coverage Mapping [cite: 89, 90]

| User Story | Status | [cite_start]Cyoda Architecture Coverage [cite: 89, 90] |
| :--- | :--- | :--- |
| **US 1.1: Predefined Access** | Covered | Entity: User. [cite_start]Seed Data handles accounts. [cite: 89] |
| **US 1.2: Roles & Permissions**| Covered | [cite_start]Criteria: RequireAdmin and Require TesterOrAdmin roles. [cite: 89] |
| **US 1.3: Create Isolated Project**| Covered | Entity: Project. [cite_start]Criteria: ProjectIsolationCheck. [cite: 89] |
| **US 2.1: Single-Level Suites** | Covered | Entity: Suite. [cite_start]Criteria: Prevent NestedSuites. [cite: 89] |
| **US 2.2: Structured Test Cases**| Covered | Entities: TestCase, TestStep. [cite_start]Workflows: Create, Update. [cite: 89] |
| **US 2.3: Attachments** | Covered | Entity: Attachment. [cite_start]Processor: Edge Message Processor. [cite: 89] |
| **US 2.4: Deep Search** | Covered | [cite_start]Handled by Read API (Queries) with project context. [cite: 89] |
| **US 2.5: Bulk Import/Export** | Covered | Workflow: Import Test Data. [cite_start]Processor: Datalmport Export Processor. [cite: 89] |
| **US 3.1: Initialize Targeted Run**| Covered | Entity: TestRun. [cite_start]Processor: SnapshotProcessor. [cite: 89] |
| **US 3.2: Step-Level Validation** | Covered | Workflows: Update Status (step/case). [cite_start]Processor: Atomic Failure Processor. [cite: 89] |
| **US 3.3: Execution Evidence** | Covered | [cite_start]Workflows: Upload File, Link Bug Defect. [cite: 89] |
| **US 3.4: Lock and Unlock** | Covered | [cite_start]Workflows: Complete Run (locks), Unlock Run. [cite: 89] |
| **US 4.1: Real-time Metrics** | Covered | [cite_start]Processor: MetricsAggregator Processor. [cite: 89] |
| **US 4.2: Exportable Reports** | Covered | [cite_start]Dynamic generation via Read API requests. [cite: 90] |
