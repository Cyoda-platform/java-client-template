# Entity Requirements

## Overview
This document defines the entities required for the comments analysis application that ingests comments data from JSONPlaceholder API, analyzes them, and sends reports via email.

## Entities

### 1. CommentAnalysisJob
**Purpose**: Represents a job to analyze comments for a specific post ID.

**Attributes**:
- `postId` (Long): The ID of the post for which comments should be analyzed
- `recipientEmail` (String): Email address where the analysis report should be sent
- `requestedAt` (LocalDateTime): Timestamp when the analysis was requested
- `completedAt` (LocalDateTime): Timestamp when the analysis was completed (nullable)
- `totalComments` (Integer): Total number of comments analyzed (nullable, set after ingestion)
- `errorMessage` (String): Error message if the job failed (nullable)

**Relationships**: 
- One-to-many with Comment entities (one job can have multiple comments)
- One-to-one with CommentAnalysisReport (one job produces one report)

**Notes**: 
- Entity state will be managed by the workflow system (not included in schema)
- The state represents the current stage of the analysis job (e.g., PENDING, INGESTING, ANALYZING, SENDING_REPORT, COMPLETED, FAILED)

### 2. Comment
**Purpose**: Represents an individual comment ingested from the JSONPlaceholder API.

**Attributes**:
- `commentId` (Long): The original comment ID from the API
- `postId` (Long): The post ID this comment belongs to
- `name` (String): The name/title of the comment
- `email` (String): Email address of the comment author
- `body` (String): The actual comment text content
- `jobId` (String): Reference to the CommentAnalysisJob that ingested this comment
- `ingestedAt` (LocalDateTime): Timestamp when this comment was ingested
- `wordCount` (Integer): Number of words in the comment body (calculated during ingestion)
- `sentimentScore` (Double): Sentiment analysis score (nullable, calculated during analysis)

**Relationships**:
- Many-to-one with CommentAnalysisJob (many comments belong to one job)

**Notes**:
- Entity state will be managed by the workflow system
- The state represents the processing stage of the comment (e.g., INGESTED, ANALYZED)

### 3. CommentAnalysisReport
**Purpose**: Represents the final analysis report containing aggregated insights about comments.

**Attributes**:
- `jobId` (String): Reference to the CommentAnalysisJob this report belongs to
- `postId` (Long): The post ID that was analyzed
- `totalComments` (Integer): Total number of comments analyzed
- `averageWordCount` (Double): Average number of words per comment
- `averageSentimentScore` (Double): Average sentiment score across all comments
- `positiveCommentsCount` (Integer): Number of comments with positive sentiment
- `negativeCommentsCount` (Integer): Number of comments with negative sentiment
- `neutralCommentsCount` (Integer): Number of comments with neutral sentiment
- `topCommenters` (String): JSON string containing top 5 most active commenters
- `commonKeywords` (String): JSON string containing most frequently used keywords
- `generatedAt` (LocalDateTime): Timestamp when the report was generated
- `sentAt` (LocalDateTime): Timestamp when the report was sent via email (nullable)

**Relationships**:
- One-to-one with CommentAnalysisJob (one report belongs to one job)

**Notes**:
- Entity state will be managed by the workflow system
- The state represents the report stage (e.g., GENERATED, SENT)

## Entity State Management

**Important**: All entities use the internal `entity.meta.state` for workflow state management. The following semantic states are used:

### CommentAnalysisJob States:
- `INITIAL` → `PENDING` → `INGESTING` → `ANALYZING` → `GENERATING_REPORT` → `SENDING_REPORT` → `COMPLETED`
- Error states: `INGESTION_FAILED`, `ANALYSIS_FAILED`, `REPORT_FAILED`, `EMAIL_FAILED`

### Comment States:
- `INITIAL` → `INGESTED` → `ANALYZED`

### CommentAnalysisReport States:
- `INITIAL` → `GENERATED` → `SENT`

## Data Flow
1. User creates a CommentAnalysisJob with postId and recipientEmail
2. System ingests comments from API and creates Comment entities
3. System analyzes comments and updates sentiment scores
4. System generates CommentAnalysisReport with aggregated data
5. System sends report via email and marks job as completed
