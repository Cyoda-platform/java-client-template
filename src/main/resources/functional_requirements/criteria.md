# Criteria Requirements

## Overview
This document defines the criteria required for the comments analysis application. Criteria are pure functions that evaluate conditions without side effects to determine workflow transitions.

## CommentAnalysisJob Criteria

### 1. CommentAnalysisJobIngestionCompleteCriterion
**Entity**: CommentAnalysisJob  
**Purpose**: Check if all comments have been successfully ingested from the API  
**Used in transition**: INGESTING → ANALYZING

**Pseudocode**:
```
check(job):
    1. Get count of Comment entities where jobId = job.id
    2. If job.totalComments is null:
        return false (ingestion not yet started)
    3. If count of comments < job.totalComments:
        return false (still ingesting)
    4. Get count of Comment entities where jobId = job.id AND state = "INGESTED"
    5. If count of ingested comments = job.totalComments:
        return true (all comments ingested)
    6. Otherwise:
        return false (some comments still processing)
```

### 2. CommentAnalysisJobAnalysisCompleteCriterion
**Entity**: CommentAnalysisJob  
**Purpose**: Check if all comments have been analyzed (sentiment analysis complete)  
**Used in transition**: ANALYZING → GENERATING_REPORT

**Pseudocode**:
```
check(job):
    1. Get count of Comment entities where jobId = job.id
    2. Get count of Comment entities where jobId = job.id AND state = "ANALYZED"
    3. If count of analyzed comments = total comments for this job:
        return true (all comments analyzed)
    4. Otherwise:
        return false (some comments still being analyzed)
```

### 3. CommentAnalysisJobIngestionFailedCriterion
**Entity**: CommentAnalysisJob  
**Purpose**: Detect if ingestion has failed (API errors, network issues, etc.)  
**Used in transition**: INGESTING → INGESTION_FAILED

**Pseudocode**:
```
check(job):
    1. Check if job.errorMessage is not null and contains ingestion-related error
    2. Check if job has been in INGESTING state for more than 10 minutes
    3. Check if API returned error status codes
    4. If any failure condition is met:
        return true (ingestion failed)
    5. Otherwise:
        return false (ingestion still in progress)
```

### 4. CommentAnalysisJobAnalysisFailedCriterion
**Entity**: CommentAnalysisJob  
**Purpose**: Detect if comment analysis has failed  
**Used in transition**: ANALYZING → ANALYSIS_FAILED

**Pseudocode**:
```
check(job):
    1. Check if job.errorMessage is not null and contains analysis-related error
    2. Check if job has been in ANALYZING state for more than 15 minutes
    3. Check if any Comment entities have failed analysis
    4. If any failure condition is met:
        return true (analysis failed)
    5. Otherwise:
        return false (analysis still in progress)
```

### 5. CommentAnalysisJobReportFailedCriterion
**Entity**: CommentAnalysisJob  
**Purpose**: Detect if report generation has failed  
**Used in transition**: GENERATING_REPORT → REPORT_FAILED

**Pseudocode**:
```
check(job):
    1. Check if job.errorMessage is not null and contains report-related error
    2. Check if job has been in GENERATING_REPORT state for more than 5 minutes
    3. Check if CommentAnalysisReport creation failed
    4. If any failure condition is met:
        return true (report generation failed)
    5. Otherwise:
        return false (report generation still in progress)
```

### 6. CommentAnalysisJobEmailFailedCriterion
**Entity**: CommentAnalysisJob  
**Purpose**: Detect if email sending has failed  
**Used in transition**: SENDING_REPORT → EMAIL_FAILED

**Pseudocode**:
```
check(job):
    1. Check if job.errorMessage is not null and contains email-related error
    2. Check if job has been in SENDING_REPORT state for more than 5 minutes
    3. Check if email service returned error status
    4. Check if recipient email is invalid format
    5. If any failure condition is met:
        return true (email sending failed)
    6. Otherwise:
        return false (email sending still in progress)
```

## Design Principles

### Pure Functions
All criteria must be pure functions that:
- Do not modify any entities
- Do not have side effects
- Only read data to evaluate conditions
- Return consistent results for the same input

### Error Detection
Failure criteria should check for:
- Explicit error messages set by processors
- Timeout conditions (jobs stuck in states too long)
- External service failures
- Data validation failures

### Performance Considerations
Criteria should be lightweight and fast:
- Use efficient database queries
- Avoid complex calculations
- Cache results when appropriate
- Minimize external service calls

### Reliability
Criteria should be robust:
- Handle null values gracefully
- Provide meaningful error detection
- Avoid false positives/negatives
- Include appropriate timeout thresholds
