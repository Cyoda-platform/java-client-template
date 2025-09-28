# CommentAnalysisRequest Entity

## Overview
Represents a request to analyze comments from JSONPlaceholder API for a specific post ID.

## Attributes
- **postId** (Integer): The post ID to fetch comments for from JSONPlaceholder API
- **emailAddress** (String): Email address to send the analysis report to
- **requestedAt** (LocalDateTime): Timestamp when the analysis was requested

## Relationships
- Creates a **CommentAnalysisReport** entity upon completion of analysis

## Notes
- Entity state transitions from initial → pending → processing → completed/failed
- The postId must be valid (positive integer)
- Email address must be in valid format
