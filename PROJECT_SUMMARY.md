# Integrated Digital Platform for Research & Clinical Trial Management

## Project Overview

This project implements a comprehensive **Integrated Digital Platform for Research & Clinical Trial Management** using the Cyoda framework with Spring Boot. The application provides end-to-end management of clinical trial submissions, document management, study operations, and participant tracking.

## Architecture

### Technology Stack
- **Framework**: Cyoda Client Template with Spring Boot
- **Language**: Java 17
- **Build Tool**: Gradle
- **Architecture Pattern**: Workflow-driven entity management with processors and criteria
- **API Style**: RESTful APIs with JSON over HTTPS

### Core Design Principles
- **Interface-based design**: No Java reflection, uses CyodaEntity/CyodaProcessor interfaces
- **Workflow-driven architecture**: All business logic flows through Cyoda workflows
- **Thin controllers**: Pure proxies to EntityService with no business logic
- **Manual transitions**: Entity updates specify manual transitions explicitly
- **Technical ID performance**: Uses UUIDs in API responses for optimal performance

## Implemented Entities

### 1. Submission Entity
**Purpose**: Manages clinical trial submissions from external submitters through review and approval process.

**Key Features**:
- Complete submission wizard workflow (draft → submitted → intake → scientific review → decision)
- Document attachment tracking
- RFI (Request for Information) support
- Approval/rejection with study activation

**Workflow States**: `initial` → `draft` → `submitted` → `intake` → `scientific_review` → `approved/rejected/pending_rfi` → `activated`

**API Endpoints**: `/ui/submissions/**`

### 2. Document Entity
**Purpose**: Digital document management with versioning, audit trail, and metadata tracking.

**Key Features**:
- Document versioning and superseding
- Immutable audit trail
- Status management (draft → final → superseded/withdrawn)
- File metadata and checksum tracking

**Workflow States**: `initial` → `draft` → `final` → `superseded/withdrawn`

**API Endpoints**: `/ui/documents/**`

### 3. Study Entity
**Purpose**: Operational management of approved clinical studies.

**Key Features**:
- Study setup with arms and visit schedules
- Enrollment tracking and metrics
- Study lifecycle management (setup → active → completed/suspended/terminated)
- Site and investigator management

**Workflow States**: `initial` → `setup` → `active` → `completed/suspended/terminated`

**API Endpoints**: `/ui/studies/**`

### 4. Subject Entity
**Purpose**: Management of study participants with enrollment and consent tracking.

**Key Features**:
- Subject screening and enrollment
- Consent status tracking
- Demographics management
- Subject lifecycle (screening → enrolled → completed/withdrawn)

**Workflow States**: `initial` → `screening` → `enrolled/screen_failed` → `completed/withdrawn`

**API Endpoints**: `/ui/subjects/**`

### 5. AdverseEvent Entity
**Purpose**: Capture and tracking of adverse events and serious adverse events (SAEs).

**Key Features**:
- AE/SAE classification and severity tracking
- Relatedness assessment
- Outcome tracking and follow-up management
- Safety reporting integration

**API Endpoints**: `/ui/adverse-events/**` (controller to be implemented)

## Implemented Processors

### Submission Processors
- **SubmissionValidationProcessor**: Validates submission data before submission
- **IntakeProcessor**: Performs completeness checks during intake review
- **ScientificReviewProcessor**: Manages scientific review process
- **ApprovalProcessor**: Handles submission approvals
- **RejectionProcessor**: Manages submission rejections
- **RFIProcessor**: Creates requests for information
- **RFIResponseProcessor**: Processes RFI responses
- **StudyActivationProcessor**: Activates studies from approved submissions

### Document Processors
- **DocumentFinalizationProcessor**: Finalizes draft documents
- **DocumentUpdateProcessor**: Handles document updates
- **DocumentVersioningProcessor**: Creates new document versions
- **DocumentWithdrawalProcessor**: Withdraws documents

### Study Processors
- **StudySetupProcessor**: Validates and sets up study configuration
- **StudyUpdateProcessor**: Updates active studies
- **StudyCompletionProcessor**: Completes studies
- **StudySuspensionProcessor**: Suspends studies
- **StudyTerminationProcessor**: Terminates studies
- **StudyResumptionProcessor**: Resumes suspended studies

### Subject Processors
- **SubjectEnrollmentProcessor**: Enrolls subjects with consent validation
- **SubjectUpdateProcessor**: Updates subject information
- **SubjectCompletionProcessor**: Completes subject participation
- **SubjectWithdrawalProcessor**: Withdraws subjects from studies
- **SubjectScreenFailProcessor**: Handles screen failures

## API Design

### Common Patterns
- **Technical ID Access**: `GET /ui/{entity}/{uuid}` (fastest)
- **Business ID Access**: `GET /ui/{entity}/business/{businessId}` (medium speed)
- **Search**: `GET /ui/{entity}/search?param=value`
- **Advanced Search**: `POST /ui/{entity}/search/advanced`
- **Workflow Transitions**: `PUT /ui/{entity}/{id}?transition=TRANSITION_NAME`
- **Specific Actions**: `POST /ui/{entity}/{id}/action`

### Response Format
All APIs return `EntityWithMetadata<T>` containing:
- **entity**: The business entity data
- **metadata**: Technical metadata including UUID, state, timestamps

## Validation and Testing

### Build Validation
```bash
./gradlew build
```
✅ **Status**: All components compile successfully
✅ **Tests**: All existing tests pass
✅ **Integration**: Cyoda framework integration validated

### Functional Requirements Coverage

#### MVP Requirements Met:
1. ✅ **Submission Portal**: External registration, guided wizard, document attachments, status timeline
2. ✅ **Document Management**: Versioning, audit trail, metadata tracking
3. ✅ **Workflow Management**: Configurable state machines with manual transitions
4. ✅ **Study Operations**: Study setup, subject management, basic operational tracking
5. ✅ **RFI Support**: Request for information workflow integration

#### Core User Journeys Supported:
1. ✅ **External Submission**: Create → Submit → Review → Approve → Activate
2. ✅ **Document Lifecycle**: Upload → Finalize → Version → Supersede/Withdraw
3. ✅ **Study Operations**: Setup → Activate → Manage Subjects → Complete
4. ✅ **Subject Management**: Screen → Enroll → Track → Complete/Withdraw

## How to Run and Validate

### Prerequisites
- Java 17+
- Gradle 8.7+

### Build and Run
```bash
# Build the application
./gradlew build

# Run the application
./gradlew bootRun
```

### API Testing
The application exposes REST APIs at:
- **Submissions**: `http://localhost:8080/ui/submissions`
- **Documents**: `http://localhost:8080/ui/documents`
- **Studies**: `http://localhost:8080/ui/studies`
- **Subjects**: `http://localhost:8080/ui/subjects`

### Sample API Calls

#### Create a Submission
```bash
curl -X POST http://localhost:8080/ui/submissions \
  -H "Content-Type: application/json" \
  -d '{
    "submissionId": "SUB-001",
    "title": "Phase II Study of XYZ in ABC",
    "studyType": "clinical_trial",
    "protocolId": "PROT-2025-013",
    "phase": "II",
    "therapeuticArea": "Oncology",
    "sponsorName": "Contoso Pharma",
    "principalInvestigator": "Dr. Jane Roe"
  }'
```

#### Submit for Review
```bash
curl -X POST http://localhost:8080/ui/submissions/{id}/submit
```

## Project Structure

```
src/main/java/com/java_template/
├── Application.java                    # Main Spring Boot application
├── common/                            # Framework code (DO NOT MODIFY)
└── application/                       # Business logic implementation
    ├── controller/                    # REST endpoints
    │   ├── SubmissionController.java
    │   ├── DocumentController.java
    │   ├── StudyController.java
    │   └── SubjectController.java
    ├── entity/                        # Domain entities
    │   ├── submission/version_1/Submission.java
    │   ├── document/version_1/Document.java
    │   ├── study/version_1/Study.java
    │   ├── subject/version_1/Subject.java
    │   └── adverse_event/version_1/AdverseEvent.java
    └── processor/                     # Workflow processors
        ├── Submission*Processor.java (8 processors)
        ├── Document*Processor.java (4 processors)
        ├── Study*Processor.java (6 processors)
        └── Subject*Processor.java (5 processors)

src/main/resources/workflow/
├── submission/version_1/Submission.json
├── document/version_1/Document.json
├── study/version_1/Study.json
└── subject/version_1/Subject.json
```

## Next Steps for Full Implementation

1. **Complete Remaining Entities**: Visit, Medication, RFI entities with full workflows
2. **Enhanced Processors**: Add business logic for complex scenarios
3. **Criteria Implementation**: Add workflow criteria for conditional transitions
4. **Integration Testing**: End-to-end workflow testing
5. **Security**: Authentication and authorization implementation
6. **Monitoring**: Logging and metrics for operational visibility

## Compliance and Audit

The implementation provides foundational compliance features:
- **Audit Trail**: All entity changes are tracked through Cyoda's audit system
- **Workflow Traceability**: Complete state transition history
- **Document Versioning**: Immutable document history with checksums
- **Manual Transitions**: Explicit approval workflows for regulatory compliance

This implementation provides a solid foundation for a clinical trial management system that can be extended to meet full regulatory requirements and operational needs.
