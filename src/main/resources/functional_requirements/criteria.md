# Criteria

## CommentAnalysisRequest Criteria

### 1. CommentAnalysisRequestFetchCompleteCriterion

**Entity**: CommentAnalysisRequest  
**Purpose**: Check if all comments have been successfully fetched from the API  
**Used in**: `fetching` → `analyzing` transition

**Logic**:
```
1. Get all Comment entities by requestId
2. Check if at least one comment exists
3. Check if all comments have fetchedAt timestamp set
4. Return true if comments exist and all are fetched, false otherwise
```

### 2. CommentAnalysisRequestAnalysisCompleteCriterion

**Entity**: CommentAnalysisRequest  
**Purpose**: Check if comment analysis has been completed successfully  
**Used in**: `analyzing` → `generating_report` transition

**Logic**:
```
1. Get CommentAnalysis entity by requestId
2. Check if entity exists
3. Check if entity state is "completed"
4. Check if analysisCompletedAt timestamp is set
5. Check if all required analysis fields are populated:
   - totalComments > 0
   - averageCommentLength is set
   - uniqueAuthors > 0
   - topKeywords is not empty
   - sentimentSummary is not empty
6. Return true if all conditions are met, false otherwise
```

### 3. CommentAnalysisRequestReportCompleteCriterion

**Entity**: CommentAnalysisRequest  
**Purpose**: Check if email report has been prepared successfully  
**Used in**: `generating_report` → `sending_email` transition

**Logic**:
```
1. Get EmailReport entity by requestId
2. Check if entity exists
3. Check if entity state is "sending"
4. Check if email content is prepared:
   - subject is not empty
   - htmlContent is not empty
   - textContent is not empty
   - recipientEmail is valid format
5. Return true if all conditions are met, false otherwise
```

### 4. CommentAnalysisRequestEmailSentCriterion

**Entity**: CommentAnalysisRequest  
**Purpose**: Check if email has been successfully sent  
**Used in**: `sending_email` → `completed` transition

**Logic**:
```
1. Get EmailReport entity by requestId
2. Check if entity exists
3. Check if entity state is "sent"
4. Check if emailStatus equals "SENT"
5. Check if sentAt timestamp is set
6. Return true if all conditions are met, false otherwise
```

---

## CommentAnalysis Criteria

### 1. CommentAnalysisValidCriterion

**Entity**: CommentAnalysis  
**Purpose**: Validate that the analysis results are complete and valid  
**Used in**: `analyzing` → `completed` transition

**Logic**:
```
1. Check if totalComments is greater than 0
2. Check if averageCommentLength is a positive number
3. Check if uniqueAuthors is greater than 0
4. Check if topKeywords is valid JSON and not empty
5. Check if sentimentSummary is not empty
6. Check if analysisCompletedAt timestamp is set
7. Validate that totalComments matches actual count of Comment entities
8. Return true if all validations pass, false otherwise
```

---

## EmailReport Criteria

### 1. EmailReportContentReadyCriterion

**Entity**: EmailReport  
**Purpose**: Check if email content has been properly prepared  
**Used in**: `preparing` → `sending` transition

**Logic**:
```
1. Check if subject is not null and not empty
2. Check if htmlContent is not null and not empty
3. Check if textContent is not null and not empty
4. Check if recipientEmail is valid email format
5. Validate HTML content contains expected sections:
   - Analysis summary
   - Metrics table
   - Keywords section
   - Sentiment section
6. Validate text content is properly formatted
7. Return true if all validations pass, false otherwise
```

### 2. EmailReportSentCriterion

**Entity**: EmailReport  
**Purpose**: Check if email has been successfully sent by the email service  
**Used in**: `sending` → `sent` transition

**Logic**:
```
1. Check if emailStatus equals "SENT"
2. Check if sentAt timestamp is set and not in the future
3. Verify email service delivery confirmation (if available)
4. Check if sendingStartedAt timestamp exists
5. Validate that sentAt is after sendingStartedAt
6. Return true if email was successfully delivered, false otherwise
```

---

## General Validation Criteria

### Email Format Validation
**Purpose**: Validate email address format across all entities

**Logic**:
```
1. Check if email is not null and not empty
2. Check if email matches standard email regex pattern
3. Check if email contains @ symbol
4. Check if domain part is valid
5. Return true if email format is valid, false otherwise
```

### Timestamp Validation
**Purpose**: Validate timestamp fields across all entities

**Logic**:
```
1. Check if timestamp is not null
2. Check if timestamp is not in the future (for completed actions)
3. Check if timestamp is reasonable (not too far in the past)
4. Return true if timestamp is valid, false otherwise
```

### JSON Content Validation
**Purpose**: Validate JSON string fields like topKeywords

**Logic**:
```
1. Check if JSON string is not null and not empty
2. Try to parse JSON string
3. Check if parsed JSON has expected structure
4. Return true if JSON is valid and well-formed, false otherwise
```

---

## Notes

- All criteria should implement proper error handling and logging
- Criteria should be lightweight and fast-executing
- Complex business logic should be moved to processors
- Criteria should focus on validation and state checking
- Use entity.meta.state to check current entity state
- Criteria should not modify entity data, only validate it
