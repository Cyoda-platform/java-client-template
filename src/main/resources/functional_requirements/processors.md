# Processor Requirements

## Overview
This document defines the processors required for the comments analysis application. Each processor implements specific business logic for workflow transitions.

## CommentAnalysisJob Processors

### 1. CommentAnalysisJobStartIngestionProcessor
**Entity**: CommentAnalysisJob  
**Input**: CommentAnalysisJob in PENDING state  
**Purpose**: Start the process of ingesting comments from JSONPlaceholder API  
**Transition**: null (stays in INGESTING state)

**Pseudocode**:
```
process(job):
    1. Validate that job.postId is not null and positive
    2. Call JSONPlaceholder API: GET /posts/{postId}/comments
    3. For each comment in API response:
        a. Create new Comment entity with:
           - commentId = comment.id
           - postId = comment.postId
           - name = comment.name
           - email = comment.email
           - body = comment.body
           - jobId = job.id
           - ingestedAt = current timestamp
           - wordCount = count words in comment.body
        b. Save Comment entity (will auto-transition to INGESTED state)
    4. Update job.totalComments = number of comments created
    5. Return updated job
```

### 2. CommentAnalysisJobCompleteIngestionProcessor
**Entity**: CommentAnalysisJob  
**Input**: CommentAnalysisJob in INGESTING state  
**Purpose**: Mark ingestion as complete and move to analysis phase  
**Transition**: null (moves to ANALYZING state via criterion)

**Pseudocode**:
```
process(job):
    1. Log completion of ingestion phase
    2. Set job.ingestedAt = current timestamp
    3. Return job (criterion will check if all comments are ingested)
```

### 3. CommentAnalysisJobCompleteAnalysisProcessor
**Entity**: CommentAnalysisJob  
**Input**: CommentAnalysisJob in ANALYZING state  
**Purpose**: Mark analysis as complete and move to report generation  
**Transition**: null (moves to GENERATING_REPORT state via criterion)

**Pseudocode**:
```
process(job):
    1. Log completion of analysis phase
    2. Set job.analyzedAt = current timestamp
    3. Return job (criterion will check if all comments are analyzed)
```

### 4. CommentAnalysisJobGenerateReportProcessor
**Entity**: CommentAnalysisJob  
**Input**: CommentAnalysisJob in GENERATING_REPORT state  
**Purpose**: Generate comprehensive analysis report  
**Transition**: null (stays in SENDING_REPORT state)

**Pseudocode**:
```
process(job):
    1. Get all Comment entities for this job
    2. Calculate aggregated metrics:
        - totalComments = count of comments
        - averageWordCount = average of all comment.wordCount
        - averageSentimentScore = average of all comment.sentimentScore
        - positiveCommentsCount = count where sentimentScore > 0.1
        - negativeCommentsCount = count where sentimentScore < -0.1
        - neutralCommentsCount = count where -0.1 <= sentimentScore <= 0.1
    3. Generate topCommenters JSON (top 5 by comment count)
    4. Generate commonKeywords JSON (most frequent words, excluding stop words)
    5. Create CommentAnalysisReport entity with calculated metrics
    6. Save report (will auto-transition to GENERATED state)
    7. Return job
```

### 5. CommentAnalysisJobSendReportProcessor
**Entity**: CommentAnalysisJob  
**Input**: CommentAnalysisJob in SENDING_REPORT state  
**Purpose**: Send analysis report via email and complete the job  
**Transition**: null (stays in COMPLETED state)

**Pseudocode**:
```
process(job):
    1. Get CommentAnalysisReport for this job
    2. Format email content with:
        - Subject: "Comment Analysis Report for Post {postId}"
        - HTML body with formatted report data
        - Include charts/graphs if possible
    3. Send email to job.recipientEmail
    4. Update report.sentAt = current timestamp
    5. Update report state to SENT (transition: null)
    6. Set job.completedAt = current timestamp
    7. Return job
```

### 6. CommentAnalysisJobIngestionFailureProcessor
**Entity**: CommentAnalysisJob  
**Input**: CommentAnalysisJob in INGESTING state (when failure detected)  
**Purpose**: Handle ingestion failures  
**Transition**: null (stays in INGESTION_FAILED state)

**Pseudocode**:
```
process(job):
    1. Log the ingestion failure
    2. Set job.errorMessage = detailed error description
    3. Clean up any partially created Comment entities
    4. Return job
```

### 7. CommentAnalysisJobAnalysisFailureProcessor
**Entity**: CommentAnalysisJob  
**Input**: CommentAnalysisJob in ANALYZING state (when failure detected)  
**Purpose**: Handle analysis failures  
**Transition**: null (stays in ANALYSIS_FAILED state)

**Pseudocode**:
```
process(job):
    1. Log the analysis failure
    2. Set job.errorMessage = detailed error description
    3. Return job
```

### 8. CommentAnalysisJobReportFailureProcessor
**Entity**: CommentAnalysisJob  
**Input**: CommentAnalysisJob in GENERATING_REPORT state (when failure detected)  
**Purpose**: Handle report generation failures  
**Transition**: null (stays in REPORT_FAILED state)

**Pseudocode**:
```
process(job):
    1. Log the report generation failure
    2. Set job.errorMessage = detailed error description
    3. Clean up any partially created report
    4. Return job
```

### 9. CommentAnalysisJobEmailFailureProcessor
**Entity**: CommentAnalysisJob  
**Input**: CommentAnalysisJob in SENDING_REPORT state (when failure detected)  
**Purpose**: Handle email sending failures  
**Transition**: null (stays in EMAIL_FAILED state)

**Pseudocode**:
```
process(job):
    1. Log the email sending failure
    2. Set job.errorMessage = detailed error description
    3. Return job
```

## Comment Processors

### 1. CommentAnalyzeProcessor
**Entity**: Comment  
**Input**: Comment in INGESTED state  
**Purpose**: Analyze comment sentiment and extract additional metrics  
**Transition**: null (stays in ANALYZED state)

**Pseudocode**:
```
process(comment):
    1. Perform sentiment analysis on comment.body:
        - Use simple keyword-based sentiment scoring
        - Positive words (+1), negative words (-1), neutral (0)
        - Normalize score to range [-1, 1]
    2. Set comment.sentimentScore = calculated score
    3. Return comment
```

## CommentAnalysisReport Processors

### 1. CommentAnalysisReportSendProcessor
**Entity**: CommentAnalysisReport  
**Input**: CommentAnalysisReport in GENERATED state  
**Purpose**: Mark report as sent after email delivery  
**Transition**: null (stays in SENT state)

**Pseudocode**:
```
process(report):
    1. Log that report has been sent
    2. Set report.sentAt = current timestamp
    3. Return report
```
