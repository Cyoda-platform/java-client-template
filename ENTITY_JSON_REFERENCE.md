# Test Management System - Entity JSON Reference

## Overview
This document describes the 8 concrete entity JSON model files created for the Test Management System (TMS) MVP v1.0. All files are located under `src/main/resources/entity/<entity_name>/version_1/`.

## File Locations

```
src/main/resources/entity/
├── project/version_1/Project.json
├── suite/version_1/Suite.json
├── testcase/version_1/TestCase.json
├── teststep/version_1/TestStep.json
├── attachmentmetadata/version_1/AttachmentMetadata.json
├── testrun/version_1/TestRun.json
├── testruncas/version_1/TestRunCase.json
└── testrunstep/version_1/TestRunStep.json
```

## Entity Descriptions

### 1. Project
**File:** `src/main/resources/entity/project/version_1/Project.json`

Represents an isolated project workspace containing all test data.

**Fields:**
- `id` (UUID): Unique project identifier
- `name` (string): Project name (unique per workspace)
- `description` (string): Project description
- `createdBy` (string): User who created the project
- `createdAt` (ISO 8601 timestamp): Creation timestamp

**Example ID:** `550e8400-e29b-41d4-a716-446655440000`

---

### 2. Suite
**File:** `src/main/resources/entity/suite/version_1/Suite.json`

Single-level folder for grouping test cases within a project.

**Fields:**
- `id` (UUID): Unique suite identifier
- `projectId` (UUID): Foreign key to Project
- `title` (string): Suite title
- `description` (string): Suite description
- `createdAt` (ISO 8601 timestamp): Creation timestamp

**Example ID:** `660e8400-e29b-41d4-a716-446655440001`
**References:** Project `550e8400-e29b-41d4-a716-446655440000`

---

### 3. TestCase
**File:** `src/main/resources/entity/testcase/version_1/TestCase.json`

Core repository entity containing test instructions and metadata.

**Fields:**
- `id` (UUID): Unique test case identifier
- `suiteId` (UUID): Foreign key to Suite
- `projectId` (UUID): Foreign key to Project (for quick scoping)
- `title` (string): Test case title (required)
- `description` (string): Detailed description
- `preConditions` (string): Prerequisites for test execution
- `priority` (enum): HIGH | MEDIUM | LOW
- `steps` (array[TestStep]): Ordered array of test steps
- `attachments` (array[AttachmentMetadata]): Reference files
- `softDeleted` (boolean): Soft delete flag (default: false)
- `createdBy` (string): Creator username
- `createdAt` (ISO 8601 timestamp): Creation timestamp
- `updatedAt` (ISO 8601 timestamp): Last update timestamp

**Example ID:** `990e8400-e29b-41d4-a716-446655440004`
**References:** Suite `660e8400-e29b-41d4-a716-446655440001`, Project `550e8400-e29b-41d4-a716-446655440000`

---

### 4. TestStep
**File:** `src/main/resources/entity/teststep/version_1/TestStep.json`

Individual step within a test case with action and expected result.

**Fields:**
- `id` (UUID): Unique step identifier
- `index` (integer): 1-based step order
- `action` (string): Step action description (required)
- `expectedResult` (string): Expected outcome (required)
- `attachments` (array[AttachmentMetadata]): Step-level attachments

**Example ID:** `880e8400-e29b-41d4-a716-446655440003`
**Note:** Embedded within TestCase.steps array

---

### 5. AttachmentMetadata
**File:** `src/main/resources/entity/attachmentmetadata/version_1/AttachmentMetadata.json`

Metadata for files uploaded via EdgeMessage API.

**Fields:**
- `id` (UUID): Unique attachment identifier
- `filename` (string): Original filename
- `contentType` (string): MIME type (e.g., image/png, application/pdf)
- `edgeMessageId` (string): Reference to EdgeMessage API storage
- `uploadedBy` (string): Username of uploader
- `uploadedAt` (ISO 8601 timestamp): Upload timestamp

**Example ID:** `770e8400-e29b-41d4-a716-446655440002`
**Note:** Referenced by TestStep and TestRunStep attachments arrays

---

### 6. TestRun
**File:** `src/main/resources/entity/testrun/version_1/TestRun.json`

Execution instance for a set of tests with snapshot semantics.

**Fields:**
- `id` (UUID): Unique test run identifier
- `projectId` (UUID): Foreign key to Project
- `title` (string): Run title/name
- `environment` (enum): STAGING | PRODUCTION
- `createdBy` (string): User who initiated the run
- `createdAt` (ISO 8601 timestamp): Run creation timestamp
- `snapshotReference` (string): Pointer to snapshot bundle
- `status` (enum): ACTIVE | COMPLETED | LOCKED
- `metrics` (object): Aggregated run metrics
  - `total` (integer): Total test cases
  - `passed` (integer): Passed count
  - `failed` (integer): Failed count
  - `untested` (integer): Untested count
  - `skipped` (integer): Skipped count
  - `passPercentage` (float): Pass percentage
  - `failPercentage` (float): Fail percentage
  - `untestedPercentage` (float): Untested percentage

**Example ID:** `aa0e8400-e29b-41d4-a716-446655440006`
**References:** Project `550e8400-e29b-41d4-a716-446655440000`

---

### 7. TestRunCase
**File:** `src/main/resources/entity/testruncas/version_1/TestRunCase.json`

Immutable snapshot copy of a test case at run start.

**Fields:**
- `id` (UUID): Unique test run case identifier
- `testRunId` (UUID): Foreign key to TestRun
- `testCaseId` (UUID): Reference to original TestCase
- `title` (string): Snapshot of test case title
- `description` (string): Snapshot of test case description
- `preConditions` (string): Snapshot of preconditions
- `priority` (enum): HIGH | MEDIUM | LOW
- `status` (enum): UNTESTED | PASSED | FAILED | SKIPPED
- `bugUrl` (string|null): URL to linked bug/defect
- `steps` (array[TestRunStep]): Snapshot of test steps
- `attachments` (array[AttachmentMetadata]): Snapshot of attachments
- `createdAt` (ISO 8601 timestamp): Snapshot creation timestamp
- `updatedAt` (ISO 8601 timestamp): Last update timestamp

**Example ID:** `bb0e8400-e29b-41d4-a716-446655440007`
**References:** TestRun `aa0e8400-e29b-41d4-a716-446655440006`, TestCase `990e8400-e29b-41d4-a716-446655440004`

---

### 8. TestRunStep
**File:** `src/main/resources/entity/testrunstep/version_1/TestRunStep.json`

Immutable snapshot copy of a test step with execution status.

**Fields:**
- `id` (UUID): Unique test run step identifier
- `testRunCaseId` (UUID): Foreign key to TestRunCase
- `testStepId` (UUID): Reference to original TestStep
- `index` (integer): 1-based step order
- `action` (string): Snapshot of step action
- `expectedResult` (string): Snapshot of expected result
- `status` (enum): UNTESTED | PASSED | FAILED | SKIPPED
- `executedBy` (string): User who executed the step
- `executedAt` (ISO 8601 timestamp): Execution timestamp
- `attachments` (array[AttachmentMetadata]): Evidence attachments

**Example ID:** `dd0e8400-e29b-41d4-a716-446655440010`
**References:** TestRunCase `bb0e8400-e29b-41d4-a716-446655440007`, TestStep `880e8400-e29b-41d4-a716-446655440003`

---

## Entity Relationships Diagram

```
Project (550e8400-e29b-41d4-a716-446655440000)
│
├─── Suite (660e8400-e29b-41d4-a716-446655440001)
│    │
│    └─── TestCase (990e8400-e29b-41d4-a716-446655440004)
│         │
│         ├─── TestStep (880e8400-e29b-41d4-a716-446655440003)
│         │    └─── AttachmentMetadata (770e8400-e29b-41d4-a716-446655440002)
│         │
│         └─── AttachmentMetadata (770e8400-e29b-41d4-a716-446655440002)
│
└─── TestRun (aa0e8400-e29b-41d4-a716-446655440006)
     │
     └─── TestRunCase (bb0e8400-e29b-41d4-a716-446655440007)
          │
          └─── TestRunStep (dd0e8400-e29b-41d4-a716-446655440010)
               └─── AttachmentMetadata (770e8400-e29b-41d4-a716-446655440002)
```

## Key Design Patterns

### 1. Snapshot Semantics
- **TestRunCase** and **TestRunStep** are immutable copies created at run start
- Changes to source TestCase/TestStep do not affect existing run snapshots
- Enables historical tracking and prevents test modification during execution

### 2. Attachment Integration
- All attachments reference EdgeMessage API via `edgeMessageId`
- Attachments can be associated with TestCase, TestStep, TestRunCase, or TestRunStep
- Supports multiple file types via `contentType` field

### 3. Project Isolation
- Every entity includes or references `projectId`
- Ensures strict data isolation between projects
- Enables efficient scoping in queries

### 4. Soft Delete
- TestCase includes `softDeleted` boolean flag
- Soft-deleted cases remain in run snapshots but disappear from repository view
- Preserves historical data while maintaining clean UI

### 5. Metrics Aggregation
- TestRun includes comprehensive metrics object
- Tracks total, passed, failed, untested, and skipped counts
- Includes percentage calculations for reporting

## UUID Allocation Strategy

All UUIDs follow a consistent pattern for easy reference:

| Entity | UUID Prefix | Example |
|--------|------------|---------|
| Project | 550e8400 | 550e8400-e29b-41d4-a716-446655440000 |
| Suite | 660e8400 | 660e8400-e29b-41d4-a716-446655440001 |
| TestCase | 990e8400 | 990e8400-e29b-41d4-a716-446655440004 |
| TestStep | 880e8400 | 880e8400-e29b-41d4-a716-446655440003 |
| AttachmentMetadata | 770e8400 | 770e8400-e29b-41d4-a716-446655440002 |
| TestRun | aa0e8400 | aa0e8400-e29b-41d4-a716-446655440006 |
| TestRunCase | bb0e8400 | bb0e8400-e29b-41d4-a716-446655440007 |
| TestRunStep | dd0e8400 | dd0e8400-e29b-41d4-a716-446655440010 |

## Validation Rules

All JSON files have been validated for:
- ✓ Valid JSON syntax
- ✓ Consistent UUID references
- ✓ Proper enum values
- ✓ ISO 8601 timestamp format
- ✓ Required field presence
- ✓ Array structure correctness

## Usage Notes

1. **For Testing:** Use these files as fixtures in unit and integration tests
2. **For Documentation:** Reference these files in API documentation
3. **For Development:** Use as templates when creating new entities
4. **For Validation:** Cross-reference IDs to ensure referential integrity

---

**Generated:** 2024-01-16
**Version:** 1.0
**Status:** Ready for Implementation
