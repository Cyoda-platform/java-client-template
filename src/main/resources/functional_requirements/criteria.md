# Criteria Specifications

## Overview
This document defines the criteria that implement conditional logic for workflow transitions in the book data analysis application. Criteria are used to validate data and determine whether transitions should proceed.

## Book Entity Criteria

### 1. BookDataValidationCriterion

**Entity**: Book  
**Transition**: `analyze_book_data` (extracted → analyzed)  
**Purpose**: Validates that book data extracted from API is complete and valid before analysis

**Validation Logic**:
- Ensures all required fields are present and valid
- Validates data quality and consistency
- Checks for reasonable data ranges

**Conditions**:
```
CHECK book.bookId is not null AND book.bookId > 0
CHECK book.title is not null AND book.title.length > 0
CHECK book.description is not null AND book.description.length >= 10
CHECK book.pageCount is not null AND book.pageCount > 0 AND book.pageCount <= 10000
CHECK book.excerpt is not null AND book.excerpt.length >= 5
CHECK book.publishDate is not null AND book.publishDate <= CURRENT_DATE()
CHECK book.retrievedAt is not null AND book.retrievedAt <= CURRENT_TIMESTAMP()
```

**Implementation Notes**:
- Returns `true` if all validations pass
- Returns `false` if any validation fails
- Logs specific validation failures for debugging
- Prevents analysis of incomplete or invalid book data

**Error Handling**:
- If validation fails, the book remains in "extracted" state
- Error details are logged for manual review
- Invalid books can be manually corrected and re-processed

## Report Entity Criteria

### 2. ReportReadinessCriterion

**Entity**: Report  
**Transition**: `send_report_email` (generated → email_sending)  
**Purpose**: Validates that report is complete and ready for email delivery

**Validation Logic**:
- Ensures report content is fully generated
- Validates email configuration
- Checks for minimum data requirements

**Conditions**:
```
CHECK report.reportId is not null AND report.reportId.length > 0
CHECK report.generatedAt is not null
CHECK report.totalBooksAnalyzed > 0
CHECK report.totalPageCount > 0
CHECK report.averagePageCount > 0
CHECK report.popularTitles is not null AND report.popularTitles.length > 10
CHECK report.publicationDateInsights is not null AND report.publicationDateInsights.length > 10
CHECK report.reportSummary is not null AND report.reportSummary.length >= 50
CHECK report.emailRecipients is not null AND report.emailRecipients CONTAINS "@"
CHECK VALIDATE_JSON(report.popularTitles) = true
CHECK VALIDATE_JSON(report.publicationDateInsights) = true
```

**Implementation Notes**:
- Validates JSON structure of embedded data fields
- Ensures minimum content thresholds are met
- Verifies email recipients are properly formatted
- Prevents sending incomplete or malformed reports

**Error Handling**:
- If validation fails, report remains in "generated" state
- Missing data can trigger report regeneration
- Email configuration errors are logged for admin review

## AnalyticsJob Entity Criteria

### 3. AnalyticsJobScheduleCriterion

**Entity**: AnalyticsJob  
**Transition**: `start_job_execution` (scheduled → running)  
**Purpose**: Validates that job is ready to execute and system conditions are appropriate

**Validation Logic**:
- Ensures job is properly scheduled
- Checks system readiness for execution
- Validates job configuration

**Conditions**:
```
CHECK job.jobId is not null AND job.jobId.length > 0
CHECK job.scheduledFor is not null AND job.scheduledFor <= CURRENT_TIMESTAMP()
CHECK job.configurationData is not null AND job.configurationData.length > 10
CHECK VALIDATE_JSON(job.configurationData) = true
CHECK NO_OTHER_JOBS_RUNNING() = true
CHECK EXTERNAL_API_AVAILABLE() = true
CHECK EMAIL_SERVICE_AVAILABLE() = true
```

**Implementation Notes**:
- Prevents multiple jobs from running simultaneously
- Validates external service availability before starting
- Ensures job configuration is valid JSON
- Checks that scheduled time has arrived

**System Checks**:
- **NO_OTHER_JOBS_RUNNING()**: Ensures only one analytics job runs at a time
- **EXTERNAL_API_AVAILABLE()**: Pings the Fake REST API to ensure it's accessible
- **EMAIL_SERVICE_AVAILABLE()**: Verifies email service connectivity

**Error Handling**:
- If validation fails, job remains in "scheduled" state
- System availability issues trigger automatic retry after delay
- Configuration errors require manual intervention

## Additional Validation Functions

### JSON Validation Function
```
FUNCTION VALIDATE_JSON(jsonString):
    TRY:
        parsed = JSON_PARSE(jsonString)
        RETURN parsed is not null
    CATCH JsonException:
        RETURN false
```

### External Service Availability Checks
```
FUNCTION EXTERNAL_API_AVAILABLE():
    TRY:
        response = HTTP_GET("https://fakerestapi.azurewebsites.net/api/v1/Books", timeout: 5000)
        RETURN response.status = 200
    CATCH Exception:
        RETURN false

FUNCTION EMAIL_SERVICE_AVAILABLE():
    TRY:
        emailService = GET_EMAIL_SERVICE()
        RETURN emailService.isConnected()
    CATCH Exception:
        RETURN false

FUNCTION NO_OTHER_JOBS_RUNNING():
    runningJobs = GET_JOBS_BY_STATE("running")
    RETURN runningJobs.count() = 0
```

## Criteria Usage in Workflows

### Book Workflow
- **BookDataValidationCriterion** is used in the `analyze_book_data` transition to ensure only valid book data proceeds to analysis

### Report Workflow  
- **ReportReadinessCriterion** is used in the `send_report_email` transition to ensure only complete reports are sent

### AnalyticsJob Workflow
- **AnalyticsJobScheduleCriterion** is used in the `start_job_execution` transition to ensure system readiness before job execution

## Error Recovery Strategies

### Validation Failure Recovery
1. **Book Data Issues**: 
   - Log specific validation failures
   - Allow manual data correction
   - Re-trigger extraction if needed

2. **Report Completeness Issues**:
   - Re-trigger report generation
   - Check underlying book data quality
   - Verify analytics calculations

3. **Job Execution Readiness Issues**:
   - Retry after system availability improves
   - Check external service status
   - Validate job configuration

### Monitoring and Alerting
- All criteria failures are logged with detailed error messages
- System administrators are notified of repeated validation failures
- Metrics are collected on validation success/failure rates for monitoring system health
