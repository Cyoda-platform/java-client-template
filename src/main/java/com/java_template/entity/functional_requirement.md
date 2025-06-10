# Final Functional Requirements for Comment Analysis Application

## API Endpoints

### 1. **POST /api/comments/analyze**
- **Purpose**: Retrieve comments data from an external API and perform analysis.
- **Request Format**:
  - **POST Body**: 
    ```json
    {
      "postId": "<integer>"
    }
    ```
- **Response Format**:
  - **200 OK**:
    ```json
    {
      "status": "success",
      "message": "Comments analyzed and report generated."
    }
    ```
  - **400 Bad Request**:
    ```json
    {
      "status": "error",
      "message": "Invalid postId."
    }
    ```

### 2. **GET /api/reports/{reportId}**
- **Purpose**: Retrieve the analysis report by report ID.
- **Response Format**:
  - **200 OK**:
    ```json
    {
      "reportId": "<string>",
      "postId": "<integer>",
      "analysisSummary": "<string>",
      "keywords": ["<string>", "<string>"],
      "sentimentScore": "<double>"
    }
    ```
  - **404 Not Found**:
    ```json
    {
      "status": "error",
      "message": "Report not found."
    }
    ```

## User-App Interaction

```mermaid
sequenceDiagram
    User->>Application: POST /api/comments/analyze (postId)
    Application->>External API: GET /comments?postId={postId}
    External API-->>Application: Comments Data
    Application->>Application: Analyze Comments
    Application->>Email Service: Send Analysis Report
    Application-->>User: 200 OK (Report generated)
```

```mermaid
journey
    title User's Journey through the Application
    section Ingest and Analyze Comments
      User: Request Comment Analysis: 5: Active
      Application: Retrieve Comments from External API: 5: Active
      Application: Analyze Comments: 5: Active
      Application: Generate and Send Report: 5: Active
    section Retrieve Report
      User: Request Report Retrieval: 5: Active
      Application: Fetch Report: 5: Active
      Application: Return Report Data: 5: Active
```