# Processor Requirements

## Overview
This document defines the detailed requirements for processors in the Comments Analysis Application. Each processor handles specific business logic during workflow transitions.

## Comment Entity Processors

### 1. CommentIngestProcessor

**Entity**: Comment
**Transition**: none → ingested
**Purpose**: Validate and enrich comment data from JSONPlaceholder API

**Input Data**: Raw Comment entity with basic API data
**Expected Output**: Enriched Comment entity with calculated fields

**Business Logic Pseudocode**:
```
PROCESS comment_entity:
    VALIDATE comment_entity.commentId is not null
    VALIDATE comment_entity.postId is positive integer
    VALIDATE comment_entity.email is valid email format
    VALIDATE comment_entity.body is not null or empty
    
    SET comment_entity.ingestedAt = current_timestamp
    SET comment_entity.wordCount = count_words(comment_entity.body)
    SET comment_entity.characterCount = length(comment_entity.body)
    
    LOG "Comment ingested: " + comment_entity.commentId
    RETURN comment_entity
```

**Other Entity Updates**: None
**Transition Name for Updates**: N/A

### 2. CommentAnalysisProcessor

**Entity**: Comment  
**Transition**: ingested → analyzed
**Purpose**: Mark comment as part of analysis batch and trigger analysis if needed

**Input Data**: Comment entity in ingested state
**Expected Output**: Comment entity marked for analysis

**Business Logic Pseudocode**:
```
PROCESS comment_entity:
    GET postId = comment_entity.postId
    
    SEARCH for existing CommentAnalysis WHERE postId = postId
    IF no CommentAnalysis exists:
        CREATE new CommentAnalysis entity:
            SET analysisId = generate_uuid()
            SET postId = postId
            SET totalComments = 0
            // This will trigger CommentAnalysis workflow: none → collecting
        
    LOG "Comment marked for analysis: " + comment_entity.commentId
    RETURN comment_entity
```

**Other Entity Updates**: 
- Creates CommentAnalysis entity if not exists
**Transition Name for Updates**: null (new entity creation)

## CommentAnalysis Entity Processors

### 3. CommentAnalysisStartProcessor

**Entity**: CommentAnalysis
**Transition**: none → collecting  
**Purpose**: Initialize analysis for a postId

**Input Data**: New CommentAnalysis entity
**Expected Output**: CommentAnalysis ready to collect comments

**Business Logic Pseudocode**:
```
PROCESS analysis_entity:
    SET analysis_entity.analysisCompletedAt = null
    SET analysis_entity.emailSent = false
    
    LOG "Started analysis for postId: " + analysis_entity.postId
    RETURN analysis_entity
```

**Other Entity Updates**: None
**Transition Name for Updates**: N/A

### 4. CommentAnalysisCalculationProcessor

**Entity**: CommentAnalysis
**Transition**: collecting → processing
**Purpose**: Perform statistical analysis on collected comments

**Input Data**: CommentAnalysis entity ready for processing
**Expected Output**: CommentAnalysis with calculated statistics

**Business Logic Pseudocode**:
```
PROCESS analysis_entity:
    GET postId = analysis_entity.postId
    
    SEARCH all Comment entities WHERE postId = postId AND state = "analyzed"
    SET comments_list = search_results
    
    SET analysis_entity.totalComments = count(comments_list)
    
    IF totalComments > 0:
        CALCULATE total_words = sum(comment.wordCount for comment in comments_list)
        CALCULATE total_chars = sum(comment.characterCount for comment in comments_list)
        
        SET analysis_entity.averageWordCount = total_words / totalComments
        SET analysis_entity.averageCharacterCount = total_chars / totalComments
        
        GROUP comments_list BY email
        SET most_active = email_group with maximum count
        SET analysis_entity.mostActiveCommenter = most_active.email
        SET analysis_entity.uniqueCommenters = count(distinct_emails)
        
        SORT comments_list BY wordCount DESC
        SET longest = comments_list[0]
        SET analysis_entity.longestComment = create_comment_summary(longest)
        
        SORT comments_list BY wordCount ASC  
        SET shortest = comments_list[0]
        SET analysis_entity.shortestComment = create_comment_summary(shortest)
    
    LOG "Analysis calculated for postId: " + postId
    RETURN analysis_entity

FUNCTION create_comment_summary(comment):
    RETURN CommentSummary:
        commentId = comment.commentId
        email = comment.email
        wordCount = comment.wordCount
        characterCount = comment.characterCount
        bodyPreview = substring(comment.body, 0, 100)
```

**Other Entity Updates**: None
**Transition Name for Updates**: N/A

### 5. CommentAnalysisCompleteProcessor

**Entity**: CommentAnalysis
**Transition**: processing → completed
**Purpose**: Finalize analysis results

**Input Data**: CommentAnalysis with calculated statistics
**Expected Output**: Completed CommentAnalysis ready for reporting

**Business Logic Pseudocode**:
```
PROCESS analysis_entity:
    SET analysis_entity.analysisCompletedAt = current_timestamp
    
    LOG "Analysis completed for postId: " + analysis_entity.postId
    RETURN analysis_entity
```

**Other Entity Updates**: None
**Transition Name for Updates**: N/A

### 6. CommentAnalysisReportProcessor

**Entity**: CommentAnalysis
**Transition**: completed → reported
**Purpose**: Create email report entity

**Input Data**: Completed CommentAnalysis
**Expected Output**: CommentAnalysis marked as reported

**Business Logic Pseudocode**:
```
PROCESS analysis_entity:
    CREATE new EmailReport entity:
        SET reportId = generate_uuid()
        SET analysisId = analysis_entity.analysisId
        SET postId = analysis_entity.postId
        SET recipientEmail = "admin@example.com" // configurable
        SET subject = "Comment Analysis Report for Post " + postId
        SET reportContent = generate_report_content(analysis_entity)
        SET deliveryStatus = "PENDING"
        SET retryCount = 0
        // This will trigger EmailReport workflow: none → prepared
    
    SET analysis_entity.emailSent = false // will be updated when email sent
    
    LOG "Email report created for analysis: " + analysis_entity.analysisId
    RETURN analysis_entity

FUNCTION generate_report_content(analysis):
    RETURN formatted_html_email_content with:
        - Post ID
        - Total comments analyzed
        - Average word/character counts
        - Most active commenter
        - Longest/shortest comment summaries
        - Unique commenters count
        - Analysis completion timestamp
```

**Other Entity Updates**: 
- Creates EmailReport entity
**Transition Name for Updates**: null (new entity creation)

## EmailReport Entity Processors

### 7. EmailReportPrepareProcessor

**Entity**: EmailReport
**Transition**: none → prepared
**Purpose**: Generate email content from analysis data

**Input Data**: New EmailReport entity
**Expected Output**: EmailReport with prepared content

**Business Logic Pseudocode**:
```
PROCESS report_entity:
    VALIDATE report_entity.reportContent is not null
    VALIDATE report_entity.recipientEmail is valid email
    VALIDATE report_entity.subject is not null
    
    LOG "Email report prepared: " + report_entity.reportId
    RETURN report_entity
```

**Other Entity Updates**: None
**Transition Name for Updates**: N/A

### 8. EmailReportSendProcessor

**Entity**: EmailReport
**Transition**: prepared → sending OR retry → sending
**Purpose**: Send email via email service

**Input Data**: EmailReport ready to send
**Expected Output**: EmailReport with sending status

**Business Logic Pseudocode**:
```
PROCESS report_entity:
    SET report_entity.deliveryStatus = "SENDING"
    
    TRY:
        CALL external_email_service.send_email(
            to: report_entity.recipientEmail,
            subject: report_entity.subject,
            body: report_entity.reportContent
        )
        // Success will trigger: sending → sent
        // Failure will trigger: sending → failed
        
    CATCH email_exception:
        SET report_entity.errorMessage = email_exception.message
        SET report_entity.deliveryStatus = "FAILED"
        
    LOG "Email sending attempted: " + report_entity.reportId
    RETURN report_entity
```

**Other Entity Updates**: None
**Transition Name for Updates**: N/A

### 9. EmailReportDeliveredProcessor

**Entity**: EmailReport
**Transition**: sending → sent
**Purpose**: Mark email as successfully sent

**Input Data**: EmailReport that was successfully sent
**Expected Output**: EmailReport marked as delivered

**Business Logic Pseudocode**:
```
PROCESS report_entity:
    SET report_entity.deliveryStatus = "SENT"
    SET report_entity.sentAt = current_timestamp
    SET report_entity.errorMessage = null
    
    // Update related CommentAnalysis
    SEARCH CommentAnalysis WHERE analysisId = report_entity.analysisId
    UPDATE found_analysis:
        SET emailSent = true
        SET emailSentAt = current_timestamp
        TRANSITION: null (no state change needed)
    
    LOG "Email delivered successfully: " + report_entity.reportId
    RETURN report_entity
```

**Other Entity Updates**: 
- Updates CommentAnalysis.emailSent = true
**Transition Name for Updates**: null (no transition)

### 10. EmailReportFailedProcessor

**Entity**: EmailReport
**Transition**: sending → failed
**Purpose**: Handle email delivery failure

**Input Data**: EmailReport that failed to send
**Expected Output**: EmailReport marked as failed

**Business Logic Pseudocode**:
```
PROCESS report_entity:
    SET report_entity.deliveryStatus = "FAILED"
    INCREMENT report_entity.retryCount
    
    LOG "Email delivery failed: " + report_entity.reportId + ", Error: " + report_entity.errorMessage
    RETURN report_entity
```

**Other Entity Updates**: None
**Transition Name for Updates**: N/A

### 11. EmailReportRetryProcessor

**Entity**: EmailReport
**Transition**: failed → retry
**Purpose**: Prepare failed email for retry

**Input Data**: Failed EmailReport
**Expected Output**: EmailReport ready for retry

**Business Logic Pseudocode**:
```
PROCESS report_entity:
    SET report_entity.deliveryStatus = "RETRY"
    SET report_entity.lastRetryAt = current_timestamp
    SET report_entity.errorMessage = null
    
    LOG "Email prepared for retry: " + report_entity.reportId + ", Attempt: " + report_entity.retryCount
    RETURN report_entity
```

**Other Entity Updates**: None
**Transition Name for Updates**: N/A
