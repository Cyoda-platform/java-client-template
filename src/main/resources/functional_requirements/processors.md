# Processors

## CommentAnalysisRequest Processors

### 1. CommentAnalysisRequestInitializeProcessor

**Entity**: CommentAnalysisRequest  
**Input**: CommentAnalysisRequest with postId and recipientEmail  
**Output**: CommentAnalysisRequest with requestedAt timestamp and generated requestId  
**Transition**: null (no state change needed)

**Pseudocode**:
```
1. Generate unique requestId using UUID
2. Set requestedAt to current timestamp
3. Validate postId is positive integer
4. Validate recipientEmail format
5. Return updated entity
```

### 2. CommentAnalysisRequestStartFetchingProcessor

**Entity**: CommentAnalysisRequest  
**Input**: CommentAnalysisRequest in pending state  
**Output**: CommentAnalysisRequest, creates Comment entities  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Call JSONPlaceholder API: GET /posts/{postId}/comments
2. For each comment in API response:
   a. Create Comment entity with commentId, postId, name, email, body
   b. Set requestId reference
   c. Set fetchedAt timestamp
   d. Save Comment entity with transition "fetch_comment"
3. Log total comments fetched
4. Return original entity
```

### 3. CommentAnalysisRequestStartAnalysisProcessor

**Entity**: CommentAnalysisRequest  
**Input**: CommentAnalysisRequest in fetching state  
**Output**: CommentAnalysisRequest, creates CommentAnalysis entity  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Create new CommentAnalysis entity
2. Set requestId reference
3. Generate unique analysisId
4. Save CommentAnalysis entity with transition "start_analysis"
5. Return original entity
```

### 4. CommentAnalysisRequestStartReportProcessor

**Entity**: CommentAnalysisRequest  
**Input**: CommentAnalysisRequest in analyzing state  
**Output**: CommentAnalysisRequest, creates EmailReport entity  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Create new EmailReport entity
2. Set requestId reference
3. Set recipientEmail from request
4. Generate unique reportId
5. Save EmailReport entity with transition "prepare_email"
6. Return original entity
```

### 5. CommentAnalysisRequestStartEmailProcessor

**Entity**: CommentAnalysisRequest  
**Input**: CommentAnalysisRequest in generating_report state  
**Output**: CommentAnalysisRequest, updates EmailReport entity  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Get EmailReport entity by requestId
2. Update EmailReport entity with transition "send_email"
3. Return original entity
```

### 6. CommentAnalysisRequestCompleteProcessor

**Entity**: CommentAnalysisRequest  
**Input**: CommentAnalysisRequest in sending_email state  
**Output**: CommentAnalysisRequest with completion timestamp  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Set completedAt timestamp
2. Log successful completion
3. Return updated entity
```

### 7. CommentAnalysisRequestFailProcessor

**Entity**: CommentAnalysisRequest  
**Input**: CommentAnalysisRequest in any state  
**Output**: CommentAnalysisRequest with failure information  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Set failedAt timestamp
2. Set failureReason from context
3. Log failure details
4. Return updated entity
```

---

## Comment Processors

### 1. CommentFetchProcessor

**Entity**: Comment  
**Input**: Comment entity with basic data from API  
**Output**: Comment entity with fetchedAt timestamp  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Set fetchedAt to current timestamp
2. Validate comment data completeness
3. Return updated entity
```

### 2. CommentProcessProcessor

**Entity**: Comment  
**Input**: Comment entity in fetched state  
**Output**: Comment entity marked as processed  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Mark comment as available for analysis
2. Set processedAt timestamp
3. Return updated entity
```

---

## CommentAnalysis Processors

### 1. CommentAnalysisStartProcessor

**Entity**: CommentAnalysis  
**Input**: CommentAnalysis entity with requestId  
**Output**: CommentAnalysis entity with analysis start timestamp  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Set analysisStartedAt timestamp
2. Initialize analysis metrics to zero
3. Return updated entity
```

### 2. CommentAnalysisCompleteProcessor

**Entity**: CommentAnalysis  
**Input**: CommentAnalysis entity in analyzing state  
**Output**: CommentAnalysis entity with completed analysis results  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Get all Comment entities by requestId
2. Calculate totalComments = count of comments
3. Calculate averageCommentLength = average of body lengths
4. Calculate uniqueAuthors = count of distinct email addresses
5. Extract and count keywords from comment bodies:
   a. Split bodies into words
   b. Remove common stop words
   c. Count frequency of remaining words
   d. Take top 10 keywords
6. Perform basic sentiment analysis:
   a. Count positive words (good, great, excellent, etc.)
   b. Count negative words (bad, terrible, awful, etc.)
   c. Categorize as positive/negative/neutral
7. Set topKeywords as JSON string
8. Set sentimentSummary as formatted string
9. Set analysisCompletedAt timestamp
10. Return updated entity
```

### 3. CommentAnalysisFailProcessor

**Entity**: CommentAnalysis  
**Input**: CommentAnalysis entity in analyzing state  
**Output**: CommentAnalysis entity with failure information  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Set analysisFailedAt timestamp
2. Set failureReason from context
3. Log analysis failure
4. Return updated entity
```

---

## EmailReport Processors

### 1. EmailReportPrepareProcessor

**Entity**: EmailReport  
**Input**: EmailReport entity with requestId and recipientEmail  
**Output**: EmailReport entity with prepared email content  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Get CommentAnalysis entity by requestId
2. Generate email subject: "Comment Analysis Report for Post {postId}"
3. Create HTML email content:
   a. Header with analysis summary
   b. Table with metrics (total comments, average length, unique authors)
   c. Top keywords section
   d. Sentiment analysis section
   e. Footer with timestamp
4. Create plain text version of email content
5. Set htmlContent and textContent
6. Set subject
7. Return updated entity
```

### 2. EmailReportSendProcessor

**Entity**: EmailReport  
**Input**: EmailReport entity with prepared content  
**Output**: EmailReport entity with sending status  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Use email service to send email:
   a. To: recipientEmail
   b. Subject: subject
   c. HTML body: htmlContent
   d. Text body: textContent
2. Set emailStatus to "SENDING"
3. Set sendingStartedAt timestamp
4. Return updated entity
```

### 3. EmailReportConfirmProcessor

**Entity**: EmailReport  
**Input**: EmailReport entity in sending state  
**Output**: EmailReport entity with confirmed sent status  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Check email service delivery status
2. Set emailStatus to "SENT"
3. Set sentAt timestamp
4. Log successful email delivery
5. Return updated entity
```

### 4. EmailReportFailProcessor

**Entity**: EmailReport  
**Input**: EmailReport entity in preparing or sending state  
**Output**: EmailReport entity with failure status  
**Transition**: null (state managed by workflow)

**Pseudocode**:
```
1. Set emailStatus to "FAILED"
2. Set failedAt timestamp
3. Set failureReason from context
4. Log email failure
5. Return updated entity
```
