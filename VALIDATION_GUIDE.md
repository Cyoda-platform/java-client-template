# Validation Guide for Integrated Digital Platform

## Quick Validation Steps

### 1. Build Validation
```bash
# Ensure clean build
./gradlew clean build

# Expected: BUILD SUCCESSFUL
```

### 2. Application Startup
```bash
# Start the application
./gradlew bootRun

# Expected: Application starts on port 8080
# Look for: "Started Application in X.XXX seconds"
```

### 3. API Endpoint Validation

#### Test Submission Workflow
```bash
# 1. Create a submission
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
    "principalInvestigator": "Dr. Jane Roe",
    "riskCategory": "moderate"
  }'

# Expected: 200 OK with EntityWithMetadata response containing technical UUID

# 2. Get submission by business ID
curl http://localhost:8080/ui/submissions/business/SUB-001

# Expected: 200 OK with submission data

# 3. Submit for review (replace {uuid} with actual UUID from step 1)
curl -X POST http://localhost:8080/ui/submissions/{uuid}/submit

# Expected: 200 OK with state changed to "submitted"
```

#### Test Document Management
```bash
# 1. Create a document
curl -X POST http://localhost:8080/ui/documents \
  -H "Content-Type: application/json" \
  -d '{
    "documentId": "DOC-001",
    "name": "Protocol Document",
    "type": "protocol",
    "versionLabel": "v1.0",
    "status": "draft",
    "submissionId": "SUB-001"
  }'

# Expected: 200 OK with document entity

# 2. Finalize document (replace {uuid} with actual UUID)
curl -X POST http://localhost:8080/ui/documents/{uuid}/finalize

# Expected: 200 OK with state changed to "final"
```

#### Test Study Management
```bash
# 1. Create a study
curl -X POST http://localhost:8080/ui/studies \
  -H "Content-Type: application/json" \
  -d '{
    "studyId": "STU-001",
    "title": "Phase II Study of XYZ in ABC",
    "protocolId": "PROT-2025-013",
    "phase": "II",
    "therapeuticArea": "Oncology",
    "principalInvestigator": "Dr. Jane Roe",
    "targetEnrollment": 100,
    "sourceSubmissionId": "SUB-001"
  }'

# Expected: 200 OK with study entity

# 2. Activate study (replace {uuid} with actual UUID)
curl -X POST http://localhost:8080/ui/studies/{uuid}/activate

# Expected: 200 OK with state changed to "active"
```

#### Test Subject Management
```bash
# 1. Create a subject
curl -X POST http://localhost:8080/ui/subjects \
  -H "Content-Type: application/json" \
  -d '{
    "subjectId": "S-001",
    "studyId": "STU-001",
    "screeningId": "SCR-001",
    "consentStatus": "consented",
    "demographics": {
      "age": 45,
      "sexAtBirth": "female"
    }
  }'

# Expected: 200 OK with subject entity

# 2. Enroll subject (replace {uuid} with actual UUID)
curl -X POST http://localhost:8080/ui/subjects/{uuid}/enroll

# Expected: 200 OK with state changed to "enrolled"
```

### 4. Workflow Validation

#### Check Entity States
```bash
# Get all submissions and check states
curl http://localhost:8080/ui/submissions

# Get all studies and check states
curl http://localhost:8080/ui/studies

# Get all subjects and check states
curl http://localhost:8080/ui/subjects
```

#### Search Functionality
```bash
# Search submissions by study type
curl "http://localhost:8080/ui/submissions/search?studyType=clinical_trial"

# Search studies by status
curl "http://localhost:8080/ui/studies/search?status=active"

# Search subjects by study
curl http://localhost:8080/ui/subjects/study/STU-001
```

### 5. Advanced Search Validation
```bash
# Advanced submission search
curl -X POST http://localhost:8080/ui/submissions/search/advanced \
  -H "Content-Type: application/json" \
  -d '{
    "studyType": "clinical_trial",
    "phase": "II",
    "therapeuticArea": "Oncology"
  }'

# Advanced study search
curl -X POST http://localhost:8080/ui/studies/search/advanced \
  -H "Content-Type: application/json" \
  -d '{
    "status": "active",
    "phase": "II"
  }'
```

## Expected Validation Results

### Successful Build Output
```
BUILD SUCCESSFUL in Xs
21 actionable tasks: X executed, X up-to-date
```

### Successful API Responses
All API calls should return:
- **Status Code**: 200 OK (for successful operations)
- **Content-Type**: application/json
- **Response Format**: EntityWithMetadata<T> with entity and metadata fields

### Entity State Transitions
- **Submission**: initial → draft → submitted → intake → scientific_review → approved → activated
- **Document**: initial → draft → final
- **Study**: initial → setup → active
- **Subject**: initial → screening → enrolled

### Metadata Validation
Each response should include:
```json
{
  "entity": { /* business entity data */ },
  "metadata": {
    "id": "uuid-string",
    "state": "current-workflow-state",
    "version": 1,
    /* other metadata fields */
  }
}
```

## Troubleshooting

### Common Issues

1. **Build Failures**
   - Ensure Java 17+ is installed
   - Run `./gradlew clean` before build
   - Check for compilation errors in entity/processor files

2. **Application Startup Issues**
   - Check port 8080 is available
   - Verify all required dependencies are resolved
   - Check application logs for startup errors

3. **API Call Failures**
   - Verify application is running
   - Check request JSON format
   - Ensure Content-Type header is set for POST requests
   - Replace {uuid} placeholders with actual UUIDs from responses

4. **Workflow Transition Errors**
   - Verify entity is in correct state for transition
   - Check workflow JSON definitions match processor names
   - Ensure required fields are populated

## Success Criteria

✅ **Build Success**: `./gradlew build` completes without errors
✅ **Application Startup**: Application starts and listens on port 8080
✅ **Entity Creation**: All entity types can be created via API
✅ **Workflow Transitions**: Manual transitions work correctly
✅ **Search Functionality**: Basic and advanced search return results
✅ **Data Persistence**: Entities can be retrieved after creation
✅ **State Management**: Entity states change correctly through workflows

## Performance Validation

### Response Time Expectations
- **Entity Creation**: < 500ms
- **Entity Retrieval by UUID**: < 100ms
- **Entity Retrieval by Business ID**: < 200ms
- **Search Operations**: < 1000ms
- **Workflow Transitions**: < 1000ms

### Concurrent Operations
The application should handle multiple concurrent API calls without errors or data corruption.

This validation guide ensures the implemented Integrated Digital Platform meets the functional requirements and operates correctly.
