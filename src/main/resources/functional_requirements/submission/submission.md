# Submission Entity

## Overview
Represents research or clinical trial submissions that go through a review and decision-making workflow.

## Attributes
- **title**: String - Submission title
- **description**: String - Detailed submission description
- **submitterEmail**: String - Email of the submitting user
- **submissionType**: String - Type of submission (RESEARCH_PROPOSAL, CLINICAL_TRIAL, ETHICS_REVIEW)
- **priority**: String - Submission priority (LOW, MEDIUM, HIGH, URGENT)
- **submissionDate**: LocalDateTime - When submission was created
- **targetDecisionDate**: LocalDateTime - Expected decision deadline
- **reviewerEmail**: String - Assigned reviewer's email (nullable)
- **decisionReason**: String - Reason for final decision (nullable)

## Relationships
- One Submission belongs to one User (submitter)
- One Submission can be assigned to one User (reviewer)
- One Submission can have many Documents
- Submission state managed internally via `entity.meta.state`

## Business Rules
- Submitter cannot be the same as reviewer
- Only REVIEWER or ADMIN roles can be assigned as reviewers
- Target decision date must be in the future when created
