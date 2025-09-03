# Entities Requirements

## Overview
This document defines the entities required for the Product Performance Analysis and Reporting System. The system integrates with the Pet Store API to extract product data, analyze performance metrics, generate reports, and send email notifications.

## Entity Definitions

### 1. Product Entity

**Purpose**: Represents a product from the Pet Store API with performance tracking capabilities.

**Attributes**:
- `id` (Long): Unique identifier for the product (from Pet Store API)
- `name` (String): Product name (required)
- `category` (String): Product category name
- `categoryId` (Long): Category identifier
- `photoUrls` (List<String>): Product image URLs
- `tags` (List<String>): Product tags for categorization
- `price` (BigDecimal): Product price
- `stockQuantity` (Integer): Current stock level
- `salesVolume` (Integer): Total units sold
- `revenue` (BigDecimal): Total revenue generated
- `lastSaleDate` (LocalDateTime): Date of last sale
- `createdAt` (LocalDateTime): Product creation timestamp
- `updatedAt` (LocalDateTime): Last update timestamp

**Entity State**: Uses `status` field semantically as state
- Available products can be analyzed
- Pending products are being processed
- Sold products are archived

**Relationships**:
- One-to-Many with PerformanceMetric (one product has multiple performance metrics)
- Many-to-Many with Report (products can appear in multiple reports)

### 2. PerformanceMetric Entity

**Purpose**: Stores calculated performance metrics for products over specific time periods.

**Attributes**:
- `id` (Long): Unique identifier
- `productId` (Long): Reference to Product entity
- `metricType` (String): Type of metric (SALES_VOLUME, REVENUE, INVENTORY_TURNOVER, TREND_ANALYSIS)
- `metricValue` (BigDecimal): Calculated metric value
- `calculationPeriod` (String): Time period for calculation (DAILY, WEEKLY, MONTHLY)
- `periodStart` (LocalDate): Start date of calculation period
- `periodEnd` (LocalDate): End date of calculation period
- `calculatedAt` (LocalDateTime): When metric was calculated
- `isOutlier` (Boolean): Whether metric represents an outlier/anomaly

**Entity State**: Internal workflow state for metric processing
- Metrics start in initial state and move through calculation phases
- No user-visible status field needed

**Relationships**:
- Many-to-One with Product (multiple metrics belong to one product)
- Many-to-Many with Report (metrics can be included in multiple reports)

### 3. Report Entity

**Purpose**: Represents generated performance analysis reports.

**Attributes**:
- `id` (Long): Unique identifier
- `reportName` (String): Human-readable report name
- `reportType` (String): Type of report (WEEKLY_SUMMARY, MONTHLY_ANALYSIS, CUSTOM)
- `generationDate` (LocalDateTime): When report was generated
- `reportPeriodStart` (LocalDate): Start date of reporting period
- `reportPeriodEnd` (LocalDate): End date of reporting period
- `filePath` (String): Path to generated report file
- `fileFormat` (String): Report format (PDF, HTML, CSV)
- `summary` (String): Brief report summary for email body
- `totalProducts` (Integer): Number of products analyzed
- `topPerformingProducts` (List<String>): List of best performing product names
- `underperformingProducts` (List<String>): List of products needing attention
- `keyInsights` (List<String>): Main insights from analysis

**Entity State**: Internal workflow state for report generation
- Reports progress through generation, review, and distribution phases
- No user-visible status field needed

**Relationships**:
- Many-to-Many with Product (reports can include multiple products)
- Many-to-Many with PerformanceMetric (reports can include multiple metrics)
- One-to-Many with EmailNotification (one report can trigger multiple email notifications)

### 4. EmailNotification Entity

**Purpose**: Manages email notifications for report distribution.

**Attributes**:
- `id` (Long): Unique identifier
- `reportId` (Long): Reference to Report entity
- `recipientEmail` (String): Email address of recipient
- `subject` (String): Email subject line
- `bodyContent` (String): Email body content
- `attachmentPath` (String): Path to report attachment
- `scheduledSendTime` (LocalDateTime): When email should be sent
- `actualSendTime` (LocalDateTime): When email was actually sent
- `deliveryStatus` (String): Email delivery status (PENDING, SENT, FAILED, DELIVERED)
- `errorMessage` (String): Error details if delivery failed
- `retryCount` (Integer): Number of retry attempts
- `maxRetries` (Integer): Maximum retry attempts allowed

**Entity State**: Uses `deliveryStatus` field semantically as state
- Notifications progress through scheduling, sending, and delivery confirmation
- Failed notifications can be retried

**Relationships**:
- Many-to-One with Report (multiple notifications can reference one report)

### 5. DataExtractionJob Entity

**Purpose**: Tracks automated data extraction jobs from Pet Store API.

**Attributes**:
- `id` (Long): Unique identifier
- `jobName` (String): Name of extraction job
- `scheduledTime` (LocalDateTime): When job was scheduled to run
- `startTime` (LocalDateTime): When job actually started
- `endTime` (LocalDateTime): When job completed
- `extractionType` (String): Type of data extracted (PRODUCTS, INVENTORY, ORDERS)
- `apiEndpoint` (String): Pet Store API endpoint used
- `recordsExtracted` (Integer): Number of records successfully extracted
- `recordsProcessed` (Integer): Number of records processed
- `recordsFailed` (Integer): Number of records that failed processing
- `errorLog` (String): Details of any errors encountered
- `nextScheduledRun` (LocalDateTime): When next job should run

**Entity State**: Internal workflow state for job execution
- Jobs progress through scheduling, execution, and completion phases
- No user-visible status field needed

**Relationships**:
- One-to-Many with Product (one job can extract multiple products)
- One-to-Many with PerformanceMetric (one job can trigger multiple metric calculations)

## Entity Relationships Summary

```
DataExtractionJob (1) -----> (Many) Product
Product (1) -----> (Many) PerformanceMetric
Product (Many) <-----> (Many) Report
PerformanceMetric (Many) <-----> (Many) Report
Report (1) -----> (Many) EmailNotification
```

## Notes

1. **Entity States**: All entities use internal workflow states managed by the system. Where semantically similar fields exist (like `status` in Product or `deliveryStatus` in EmailNotification), these are used as the entity state.

2. **Business IDs**: Each entity should implement a business identifier method for easy lookup:
   - Product: uses Pet Store API `id`
   - PerformanceMetric: combination of `productId` + `metricType` + `periodStart`
   - Report: uses `reportName` + `generationDate`
   - EmailNotification: uses `reportId` + `recipientEmail`
   - DataExtractionJob: uses `jobName` + `scheduledTime`

3. **Audit Fields**: All entities include creation and update timestamps for audit purposes.

4. **Data Types**: Using appropriate Java types for precision (BigDecimal for monetary values, LocalDateTime for timestamps, etc.).
