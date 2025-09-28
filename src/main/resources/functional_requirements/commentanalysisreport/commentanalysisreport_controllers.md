# CommentAnalysisReport Controllers

## CommentAnalysisReportController

### GET /api/comment-analysis/reports/{uuid}
Get analysis report details.

**Response:**
```json
{
  "entity": {
    "postId": 1,
    "totalComments": 5,
    "sentimentSummary": {
      "positive": 2,
      "negative": 1,
      "neutral": 2
    },
    "keyThemes": ["product feedback", "user experience", "suggestions"],
    "reportGeneratedAt": "2024-01-15T10:32:00",
    "emailAddress": "user@example.com"
  },
  "meta": {
    "uuid": "456e7890-e89b-12d3-a456-426614174001",
    "state": "completed",
    "createdAt": "2024-01-15T10:31:45",
    "updatedAt": "2024-01-15T10:32:00"
  }
}
```

### GET /api/comment-analysis/reports/by-post/{postId}
Get reports by post ID.

**Response:**
```json
[
  {
    "entity": {
      "postId": 1,
      "totalComments": 5,
      "sentimentSummary": {
        "positive": 2,
        "negative": 1,
        "neutral": 2
      },
      "keyThemes": ["product feedback", "user experience"],
      "reportGeneratedAt": "2024-01-15T10:32:00",
      "emailAddress": "user@example.com"
    },
    "meta": {
      "uuid": "456e7890-e89b-12d3-a456-426614174001",
      "state": "completed",
      "createdAt": "2024-01-15T10:31:45",
      "updatedAt": "2024-01-15T10:32:00"
    }
  }
]
```
