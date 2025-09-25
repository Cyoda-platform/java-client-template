# Submission Controllers

## SubmissionController

### Endpoints

#### POST /api/submissions
Create a new submission.

**Request Example:**
```json
{
  "title": "Clinical Trial Protocol Review",
  "description": "Phase II clinical trial for new cancer treatment",
  "submitterEmail": "researcher@example.com",
  "submissionType": "CLINICAL_TRIAL",
  "priority": "HIGH"
}
```

**Response Example:**
```json
{
  "entity": {
    "title": "Clinical Trial Protocol Review",
    "description": "Phase II clinical trial for new cancer treatment",
    "submitterEmail": "researcher@example.com",
    "submissionType": "CLINICAL_TRIAL",
    "priority": "HIGH",
    "submissionDate": "2024-01-15T10:30:00Z",
    "targetDecisionDate": "2024-02-14T10:30:00Z",
    "reviewerEmail": null,
    "decisionReason": null
  },
  "meta": {
    "uuid": "456e7890-e89b-12d3-a456-426614174001",
    "state": "draft",
    "version": 1
  }
}
```

#### PUT /api/submissions/{uuid}/submit
Submit for review (transition: submit_for_review).

**Request Example:**
```json
{
  "transitionName": "submit_for_review"
}
```

#### PUT /api/submissions/{uuid}/assign-reviewer
Assign reviewer (transition: assign_reviewer).

**Request Example:**
```json
{
  "transitionName": "assign_reviewer",
  "reviewerEmail": "reviewer@example.com"
}
```

#### PUT /api/submissions/{uuid}/approve
Approve submission (transition: approve_submission).

**Request Example:**
```json
{
  "transitionName": "approve_submission",
  "decisionReason": "All requirements met, protocol approved"
}
```

#### GET /api/submissions/{uuid}
Get submission by UUID.

#### GET /api/submissions
List submissions with filtering options.
