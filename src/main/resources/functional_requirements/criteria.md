# Criteria Requirements

## Overview
This document defines the detailed requirements for criteria in the Comments Analysis Application. Criteria are pure functions that evaluate conditions without side effects to determine workflow transition paths.

## Criteria Definitions

### 1. CommentAnalysisReadyCriterion

**Entity**: CommentAnalysis
**Transition**: collecting → processing (begin_processing)
**Purpose**: Check if analysis has sufficient comments to proceed with processing

**Input Data**: CommentAnalysis entity in collecting state
**Expected Output**: Boolean - true if ready for processing, false otherwise

**Business Logic**:
```
CHECK analysis_entity:
    GET postId = analysis_entity.postId
    
    SEARCH count of Comment entities WHERE:
        postId = postId AND 
        state = "analyzed"
    
    IF comment_count >= 1:
        RETURN true  // At least one comment available for analysis
    ELSE:
        RETURN false // Not enough comments yet
```

**Validation Rules**:
- Must have at least 1 comment in "analyzed" state for the postId
- Comments must be properly ingested and validated
- PostId must be valid positive integer

**Notes**:
- Pure function - no side effects or entity modifications
- Only reads existing data to make decision
- Prevents analysis from starting with no data

### 2. EmailReportValidCriterion

**Entity**: EmailReport
**Transition**: prepared → sending (send_email)
**Purpose**: Validate email report is ready for delivery

**Input Data**: EmailReport entity in prepared state
**Expected Output**: Boolean - true if valid for sending, false otherwise

**Business Logic**:
```
CHECK report_entity:
    // Validate required fields
    IF report_entity.recipientEmail is null OR empty:
        RETURN false
        
    IF report_entity.subject is null OR empty:
        RETURN false
        
    IF report_entity.reportContent is null OR empty:
        RETURN false
    
    // Validate email format
    IF NOT is_valid_email_format(report_entity.recipientEmail):
        RETURN false
    
    // Check content length (not too short)
    IF length(report_entity.reportContent) < 50:
        RETURN false
        
    // Validate related analysis exists
    SEARCH CommentAnalysis WHERE analysisId = report_entity.analysisId
    IF analysis not found:
        RETURN false
        
    IF analysis.state != "completed":
        RETURN false
    
    RETURN true  // All validations passed
```

**Validation Rules**:
- Recipient email must be valid format
- Subject and content must not be empty
- Content must be at least 50 characters
- Related CommentAnalysis must exist and be completed
- No HTML injection or malicious content

**Notes**:
- Pure function - only validates data
- Prevents sending invalid or incomplete emails
- Ensures data integrity before external service calls

### 3. EmailReportRetryCriterion

**Entity**: EmailReport
**Transition**: failed → retry (retry_email)
**Purpose**: Determine if failed email should be retried based on retry limits and error types

**Input Data**: EmailReport entity in failed state
**Expected Output**: Boolean - true if retry allowed, false otherwise

**Business Logic**:
```
CHECK report_entity:
    DEFINE MAX_RETRY_ATTEMPTS = 3
    DEFINE RETRY_COOLDOWN_MINUTES = 5
    
    // Check retry limit
    IF report_entity.retryCount >= MAX_RETRY_ATTEMPTS:
        RETURN false  // Exceeded maximum retries
    
    // Check cooldown period
    IF report_entity.lastRetryAt is not null:
        GET minutes_since_last_retry = current_time - report_entity.lastRetryAt
        IF minutes_since_last_retry < RETRY_COOLDOWN_MINUTES:
            RETURN false  // Still in cooldown period
    
    // Check error type (some errors should not be retried)
    IF report_entity.errorMessage contains "invalid email":
        RETURN false  // Permanent error - don't retry
        
    IF report_entity.errorMessage contains "blocked recipient":
        RETURN false  // Permanent error - don't retry
    
    // Check if email service is available (optional)
    // This could be enhanced to check service health
    
    RETURN true  // Retry is allowed
```

**Validation Rules**:
- Maximum 3 retry attempts allowed
- Minimum 5-minute cooldown between retries
- Permanent errors (invalid email, blocked recipient) prevent retry
- Must be in failed state to retry

**Notes**:
- Pure function - no modifications to entity
- Implements exponential backoff strategy
- Prevents infinite retry loops
- Distinguishes between temporary and permanent failures

### 4. CommentValidationCriterion (Optional)

**Entity**: Comment
**Transition**: none → ingested (ingest_comment) - if validation needed
**Purpose**: Additional validation for comment data quality

**Input Data**: Comment entity with raw API data
**Expected Output**: Boolean - true if comment data is valid, false otherwise

**Business Logic**:
```
CHECK comment_entity:
    // Basic required field validation
    IF comment_entity.commentId is null OR empty:
        RETURN false
        
    IF comment_entity.postId <= 0:
        RETURN false
        
    IF comment_entity.body is null OR empty:
        RETURN false
    
    // Email format validation
    IF NOT is_valid_email_format(comment_entity.email):
        RETURN false
    
    // Content quality checks
    IF length(comment_entity.body) < 5:
        RETURN false  // Too short to be meaningful
        
    IF length(comment_entity.body) > 10000:
        RETURN false  // Suspiciously long content
    
    // Check for spam patterns (basic)
    IF comment_entity.body contains_only_special_characters():
        RETURN false
        
    IF comment_entity.body contains_excessive_urls():
        RETURN false
    
    RETURN true  // Comment is valid
```

**Validation Rules**:
- Comment ID and body must not be empty
- Post ID must be positive integer
- Email must be valid format
- Body length between 5-10000 characters
- Basic spam detection patterns

**Notes**:
- Optional criterion - can be used if data quality is a concern
- Pure function - only validates without modifications
- Helps filter out invalid or spam comments early
- Can be enhanced with more sophisticated validation rules

## Criteria Usage in Workflows

### Integration Points:
1. **CommentAnalysisReadyCriterion** - Ensures analysis only starts when data is available
2. **EmailReportValidCriterion** - Prevents sending invalid emails
3. **EmailReportRetryCriterion** - Implements smart retry logic for failed deliveries
4. **CommentValidationCriterion** - Optional data quality gate

### Error Handling:
- All criteria return boolean values only
- No exceptions should be thrown from criteria
- Invalid data should return false, not cause errors
- Logging can be added for debugging but should not affect return values

### Performance Considerations:
- Criteria should execute quickly (< 100ms)
- Database queries should be optimized with proper indexes
- Complex validations should be kept simple
- Avoid external service calls in criteria when possible

### Testing Strategy:
- Each criterion should have unit tests for all conditions
- Test both positive and negative cases
- Mock external dependencies for consistent testing
- Validate edge cases and boundary conditions
