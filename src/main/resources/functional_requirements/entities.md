# Entity Requirements

## Overview
This document defines the entities required for the London Houses Data Analysis application. The system downloads CSV data, analyzes it, and sends reports to subscribers.

## Entities

### 1. DataSource
**Purpose**: Represents a data source configuration for downloading and processing data.

**Attributes**:
- `id` (String): Unique identifier for the data source (e.g., "london_houses")
- `name` (String): Human-readable name (e.g., "London Houses Dataset")
- `url` (String): URL to download the CSV data from
- `description` (String): Description of the data source
- `isActive` (Boolean): Whether this data source is currently active
- `lastDownloadTime` (LocalDateTime): Timestamp of the last successful download
- `downloadIntervalHours` (Integer): How often to check for updates (in hours)
- `fileFormat` (String): Expected file format (e.g., "CSV")
- `expectedColumns` (List<String>): List of expected column names for validation

**Relationships**:
- One-to-Many with AnalysisJob (one data source can have multiple analysis jobs)

**Notes**:
- Entity state represents the current status of the data source (e.g., "active", "downloading", "error", "inactive")
- The `status` field is managed by the system via entity.meta.state

### 2. AnalysisJob
**Purpose**: Represents a data analysis job that processes data from a data source.

**Attributes**:
- `id` (String): Unique identifier for the analysis job
- `dataSourceId` (String): Reference to the data source
- `jobName` (String): Name of the analysis job
- `analysisType` (String): Type of analysis to perform (e.g., "statistical_summary", "price_trends")
- `parameters` (Map<String, Object>): Analysis parameters and configuration
- `startTime` (LocalDateTime): When the job started
- `endTime` (LocalDateTime): When the job completed
- `errorMessage` (String): Error message if job failed
- `resultData` (String): JSON string containing analysis results
- `dataRowsProcessed` (Integer): Number of data rows processed

**Relationships**:
- Many-to-One with DataSource (multiple jobs can use the same data source)
- One-to-Many with Report (one analysis job can generate multiple reports)

**Notes**:
- Entity state represents the job status (e.g., "pending", "running", "completed", "failed")
- The system manages state transitions automatically

### 3. Report
**Purpose**: Represents a generated report based on analysis results.

**Attributes**:
- `id` (String): Unique identifier for the report
- `analysisJobId` (String): Reference to the analysis job that generated this report
- `reportTitle` (String): Title of the report
- `reportType` (String): Type of report (e.g., "summary", "detailed", "chart")
- `content` (String): Report content (HTML, JSON, or text format)
- `generatedTime` (LocalDateTime): When the report was generated
- `format` (String): Report format (e.g., "HTML", "PDF", "JSON")
- `summary` (String): Brief summary of the report findings
- `attachments` (List<String>): List of attachment file paths or URLs

**Relationships**:
- Many-to-One with AnalysisJob (multiple reports can be generated from one analysis)
- Many-to-Many with Subscriber (reports can be sent to multiple subscribers)

**Notes**:
- Entity state represents the report status (e.g., "generated", "sent", "failed")
- Reports are generated after successful analysis completion

### 4. Subscriber
**Purpose**: Represents users who subscribe to receive reports via email.

**Attributes**:
- `id` (String): Unique identifier for the subscriber
- `email` (String): Email address of the subscriber
- `firstName` (String): First name of the subscriber
- `lastName` (String): Last name of the subscriber
- `subscriptionDate` (LocalDateTime): When the user subscribed
- `isActive` (Boolean): Whether the subscription is active
- `preferences` (Map<String, Object>): Subscriber preferences (report types, frequency, etc.)
- `lastEmailSent` (LocalDateTime): Timestamp of the last email sent
- `emailDeliveryFailures` (Integer): Count of consecutive email delivery failures

**Relationships**:
- Many-to-Many with Report (subscribers can receive multiple reports)

**Notes**:
- Entity state represents the subscription status (e.g., "active", "inactive", "bounced")
- Email delivery status is tracked for reliability

## Entity Relationships Summary

```
DataSource (1) -----> (N) AnalysisJob (1) -----> (N) Report (N) <-----> (N) Subscriber
```

## State Management Notes

All entities use the system-managed `entity.meta.state` for workflow state tracking:
- **DataSource**: States like "active", "downloading", "error", "inactive"
- **AnalysisJob**: States like "pending", "running", "completed", "failed"  
- **Report**: States like "generated", "sending", "sent", "failed"
- **Subscriber**: States like "active", "inactive", "bounced"

These states are automatically managed by the workflow system and should not be included as entity attributes.
