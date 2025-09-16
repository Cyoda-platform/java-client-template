# Entity Requirements

## Overview
This document defines the detailed requirements for entities in the Comments Analysis Application. The application ingests comments data from JSONPlaceholder API, analyzes them, and sends reports via email.

## Entity Definitions

### 1. Comment Entity

**Purpose**: Represents individual comments ingested from the JSONPlaceholder API.

**Package**: `com.java_template.application.entity.comment.version_1`

**Attributes**:
- `commentId` (String, required): Business identifier - the "id" field from the API
- `postId` (Integer, required): The post ID this comment belongs to
- `name` (String, required): Comment title/name from API
- `email` (String, required): Email address of the commenter
- `body` (String, required): The actual comment text content
- `ingestedAt` (LocalDateTime, optional): Timestamp when comment was ingested
- `wordCount` (Integer, optional): Number of words in the comment body
- `characterCount` (Integer, optional): Number of characters in the comment body

**Relationships**:
- One Comment can have one CommentAnalysis (1:1)
- Multiple Comments belong to the same postId (N:1 by postId)

**Validation Rules**:
- `commentId` must not be null or empty
- `postId` must be positive integer
- `email` must be valid email format
- `body` must not be null or empty

**Notes**:
- Entity state will be managed by the workflow system (not included in schema)
- The API "id" field maps to our `commentId` business identifier
- Comments are immutable once ingested (no updates to original data)

### 2. CommentAnalysis Entity

**Purpose**: Represents the analysis results for a batch of comments from a specific post.

**Package**: `com.java_template.application.entity.comment_analysis.version_1`

**Attributes**:
- `analysisId` (String, required): Business identifier - generated UUID as string
- `postId` (Integer, required): The post ID that was analyzed
- `totalComments` (Integer, required): Total number of comments analyzed
- `averageWordCount` (Double, optional): Average words per comment
- `averageCharacterCount` (Double, optional): Average characters per comment
- `mostActiveCommenter` (String, optional): Email of user with most comments
- `longestComment` (CommentSummary, optional): Summary of the longest comment
- `shortestComment` (CommentSummary, optional): Summary of the shortest comment
- `uniqueCommenters` (Integer, optional): Number of unique email addresses
- `analysisCompletedAt` (LocalDateTime, optional): When analysis was completed
- `emailSent` (Boolean, optional): Whether email report was sent successfully
- `emailSentAt` (LocalDateTime, optional): When email was sent

**Nested Classes**:
```java
@Data
public static class CommentSummary {
    private String commentId;
    private String email;
    private Integer wordCount;
    private Integer characterCount;
    private String bodyPreview; // First 100 characters
}
```

**Relationships**:
- One CommentAnalysis relates to multiple Comments by postId (1:N)
- One CommentAnalysis can have one EmailReport (1:1)

**Validation Rules**:
- `analysisId` must not be null or empty
- `postId` must be positive integer
- `totalComments` must be non-negative

**Notes**:
- Entity state managed by workflow system
- Analysis is performed on all comments for a given postId
- Immutable once analysis is complete

### 3. EmailReport Entity

**Purpose**: Represents email reports sent containing comment analysis results.

**Package**: `com.java_template.application.entity.email_report.version_1`

**Attributes**:
- `reportId` (String, required): Business identifier - generated UUID as string
- `analysisId` (String, required): Reference to the CommentAnalysis
- `postId` (Integer, required): The post ID that was reported on
- `recipientEmail` (String, required): Email address where report was sent
- `subject` (String, required): Email subject line
- `reportContent` (String, required): The email body content
- `sentAt` (LocalDateTime, optional): When email was successfully sent
- `deliveryStatus` (String, optional): Email delivery status (PENDING, SENT, FAILED)
- `errorMessage` (String, optional): Error details if delivery failed
- `retryCount` (Integer, optional): Number of retry attempts
- `lastRetryAt` (LocalDateTime, optional): Last retry timestamp

**Relationships**:
- One EmailReport belongs to one CommentAnalysis (N:1)
- EmailReport references Comments indirectly through CommentAnalysis

**Validation Rules**:
- `reportId` must not be null or empty
- `analysisId` must not be null or empty
- `postId` must be positive integer
- `recipientEmail` must be valid email format
- `subject` must not be null or empty
- `reportContent` must not be null or empty

**Notes**:
- Entity state managed by workflow system
- Supports retry mechanism for failed email deliveries
- Immutable once successfully sent

## Entity State Management

**Important**: All entities use the workflow system's built-in state management:
- Entity state is accessed via `entity.meta.state` in processors
- State transitions are managed automatically by the workflow
- No state/status fields are included in entity schemas
- States represent the current stage in the processing workflow

## Entity Relationships Summary

```
Comment (N) -> CommentAnalysis (1) -> EmailReport (1)
     |              |                      |
   postId         postId                 postId
```

- Comments are grouped by `postId` for analysis
- Each `postId` gets one CommentAnalysis containing aggregated data
- Each CommentAnalysis generates one EmailReport
- All entities maintain `postId` for traceability
