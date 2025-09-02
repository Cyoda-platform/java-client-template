# Entities

## 1. CommentAnalysisRequest

**Description**: Represents a request to analyze comments for a specific post ID from the JSONPlaceholder API.

**Attributes**:
- `postId` (Long) - The ID of the post to fetch comments for
- `recipientEmail` (String) - Email address to send the analysis report to
- `requestedAt` (LocalDateTime) - Timestamp when the analysis was requested
- `requestId` (String) - Unique identifier for the request (business ID)

**Relationships**:
- One-to-Many with `Comment` (a request can fetch multiple comments)
- One-to-One with `CommentAnalysis` (each request produces one analysis)
- One-to-One with `EmailReport` (each request results in one email report)

**Entity State**: 
The system will manage the state automatically through workflow transitions. States include:
- `none` (initial state)
- `pending` (request created, waiting to fetch comments)
- `fetching` (fetching comments from API)
- `analyzing` (analyzing fetched comments)
- `generating_report` (generating analysis report)
- `sending_email` (sending email with report)
- `completed` (process completed successfully)
- `failed` (process failed at any stage)

---

## 2. Comment

**Description**: Represents a comment fetched from the JSONPlaceholder API.

**Attributes**:
- `commentId` (Long) - The original comment ID from the API
- `postId` (Long) - The post ID this comment belongs to
- `name` (String) - The comment title/name
- `email` (String) - Email of the comment author
- `body` (String) - The comment content/body
- `requestId` (String) - Reference to the CommentAnalysisRequest that fetched this comment
- `fetchedAt` (LocalDateTime) - Timestamp when the comment was fetched

**Relationships**:
- Many-to-One with `CommentAnalysisRequest` (multiple comments belong to one request)
- One-to-Many with `CommentAnalysis` (a comment can be referenced in analysis metrics)

**Entity State**:
The system will manage the state automatically. States include:
- `none` (initial state)
- `fetched` (comment successfully fetched from API)
- `processed` (comment has been included in analysis)

---

## 3. CommentAnalysis

**Description**: Represents the analysis results of comments for a specific request.

**Attributes**:
- `requestId` (String) - Reference to the CommentAnalysisRequest
- `totalComments` (Integer) - Total number of comments analyzed
- `averageCommentLength` (Double) - Average length of comment bodies
- `uniqueAuthors` (Integer) - Number of unique email addresses
- `topKeywords` (String) - JSON string containing top keywords and their frequencies
- `sentimentSummary` (String) - Summary of sentiment analysis (positive/negative/neutral counts)
- `analysisCompletedAt` (LocalDateTime) - Timestamp when analysis was completed
- `analysisId` (String) - Unique identifier for the analysis (business ID)

**Relationships**:
- One-to-One with `CommentAnalysisRequest` (each request has one analysis)
- One-to-One with `EmailReport` (each analysis generates one email report)

**Entity State**:
The system will manage the state automatically. States include:
- `none` (initial state)
- `analyzing` (analysis in progress)
- `completed` (analysis completed successfully)
- `failed` (analysis failed)

---

## 4. EmailReport

**Description**: Represents an email report containing the comment analysis results.

**Attributes**:
- `requestId` (String) - Reference to the CommentAnalysisRequest
- `recipientEmail` (String) - Email address where the report was sent
- `subject` (String) - Email subject line
- `htmlContent` (String) - HTML content of the email report
- `textContent` (String) - Plain text version of the email content
- `sentAt` (LocalDateTime) - Timestamp when email was sent
- `emailStatus` (String) - Status of email delivery (SENT, FAILED, PENDING)
- `reportId` (String) - Unique identifier for the email report (business ID)

**Relationships**:
- One-to-One with `CommentAnalysisRequest` (each request generates one email report)
- One-to-One with `CommentAnalysis` (each analysis results in one email report)

**Entity State**:
The system will manage the state automatically. States include:
- `none` (initial state)
- `preparing` (preparing email content)
- `sending` (email being sent)
- `sent` (email successfully sent)
- `failed` (email sending failed)

---

## Entity Relationships Summary

```
CommentAnalysisRequest (1) -----> (Many) Comment
CommentAnalysisRequest (1) -----> (1) CommentAnalysis  
CommentAnalysisRequest (1) -----> (1) EmailReport
CommentAnalysis (1) -----> (1) EmailReport
```

## Notes

- All entities use `String` business IDs for external references
- Entity states are managed automatically by the workflow system
- Timestamps are used for tracking processing progress
- The `requestId` serves as the primary correlation ID across all entities
- Email content is stored in both HTML and text formats for compatibility
- Comment analysis includes basic metrics and can be extended with more sophisticated analysis
