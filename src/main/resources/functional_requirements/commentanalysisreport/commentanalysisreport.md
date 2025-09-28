# CommentAnalysisReport Entity

## Overview
Represents the analysis results of comments including sentiment analysis and key themes.

## Attributes
- **postId** (Integer): The post ID that was analyzed
- **totalComments** (Integer): Total number of comments analyzed
- **sentimentSummary** (Map<String, Integer>): Count of positive, negative, neutral sentiments
- **keyThemes** (List<String>): List of identified themes from comment analysis
- **reportGeneratedAt** (LocalDateTime): Timestamp when report was generated
- **emailAddress** (String): Email address where report was sent

## Relationships
- Created by a **CommentAnalysisRequest** entity

## Notes
- Entity state transitions from initial → generating → completed/failed
- Sentiment summary contains counts for "positive", "negative", "neutral" categories
- Key themes are extracted from comment content analysis
