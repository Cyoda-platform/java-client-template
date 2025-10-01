# Clinical Research Platform

A comprehensive platform for managing clinical trials and research studies built with Spring Boot and the Cyoda framework.

## Quick Start

### Prerequisites
- Java 17+
- Gradle 8.7+

### Build and Run
```bash
# Build the project
./gradlew build

# Validate workflow implementations
./gradlew validateWorkflowImplementations

# Run the application
./gradlew bootRun
```

## Architecture Overview

This platform implements a workflow-driven architecture with 8 core entities:

1. **Submission** - Clinical trial submission and review workflow
2. **Document** - Digital document management with versioning
3. **Study** - Operational study management
4. **Subject** - Study participant lifecycle
5. **Visit** - Subject visit tracking and CRF data
6. **AdverseEvent** - AE/SAE reporting and follow-up
7. **Medication** - Investigational product accountability
8. **RFI** - Request for information messaging

## API Endpoints

### Submissions
- `POST /ui/submissions` - Create submission
- `GET /ui/submissions/{id}` - Get by technical ID
- `GET /ui/submissions/business/{submissionId}` - Get by business ID
- `PUT /ui/submissions/{id}?transition=submit_for_review` - Submit for review
- `POST /ui/submissions/search` - Search submissions

### Studies
- `POST /ui/studies` - Create study
- `GET /ui/studies/{id}` - Get by technical ID
- `POST /ui/studies/{id}/activate` - Activate study

### Subjects
- `POST /ui/subjects` - Create subject
- `GET /ui/subjects/study/{studyId}` - Get subjects by study
- `POST /ui/subjects/{id}/enroll` - Enroll subject

### Adverse Events
- `POST /ui/adverse-events` - Report adverse event
- `GET /ui/adverse-events/sae` - Get all SAEs
- `GET /ui/adverse-events/subject/{subjectId}` - Get AEs by subject

## Key Features

- **Workflow-driven**: All business logic flows through configurable workflows
- **SAE Alerts**: Automatic detection and logging of serious adverse events
- **Document Management**: Versioning, audit trails, controlled access
- **Medication Tracking**: Dispense/return logging with inventory management
- **RFI System**: Threaded messaging for information requests
- **Search & Filter**: Complex search capabilities across all entities

## Implementation Details

- **Entities**: 8 core domain entities implementing CyodaEntity interface
- **Processors**: 20 business logic processors for workflow transitions
- **Criteria**: 2 conditional logic components for automated decisions
- **Controllers**: 4 REST controllers providing thin API proxies
- **Workflows**: 8 JSON workflow definitions with proper state management

## Validation

All implementations are validated:
- ✅ Compilation successful
- ✅ All tests pass
- ✅ Workflow validation: 20 processors, 2 criteria
- ✅ Architecture compliance

## Documentation

See `CLINICAL_RESEARCH_PLATFORM_SUMMARY.md` for comprehensive implementation details.

## Development

### Project Structure
```
src/main/java/com/java_template/
├── Application.java                    # Main Spring Boot application
├── common/                            # Framework code - DO NOT MODIFY
└── application/                       # Business logic
    ├── controller/                    # REST endpoints
    ├── entity/                        # Domain entities
    ├── processor/                     # Workflow processors
    └── criterion/                     # Workflow criteria

src/main/resources/
└── workflow/                          # Workflow JSON definitions
```

### Adding New Features
1. Create entity implementing CyodaEntity
2. Define workflow JSON with states and transitions
3. Implement processors for business logic
4. Add criteria for conditional logic
5. Create controller for REST API
6. Validate with `./gradlew validateWorkflowImplementations`

## License

This project is part of the Cyoda platform implementation.
