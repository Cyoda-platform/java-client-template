# Entity Requirements

## Overview
This document defines the entities required for the Product Performance Analysis and Reporting System that integrates with the Pet Store API to analyze product performance metrics and generate automated reports.

## Entity Definitions

### 1. Product Entity

**Purpose**: Represents a product from the Pet Store API with performance tracking capabilities.

**Attributes**:
- `petId` (Long): Unique identifier from Pet Store API
- `name` (String): Product name (required)
- `category` (String): Product category name
- `categoryId` (Long): Category identifier
- `photoUrls` (List<String>): Product image URLs
- `tags` (List<String>): Product tags for categorization
- `price` (BigDecimal): Product price (derived/calculated)
- `stockLevel` (Integer): Current inventory level
- `salesVolume` (Integer): Total units sold
- `revenue` (BigDecimal): Total revenue generated
- `lastSaleDate` (LocalDateTime): Date of last sale
- `inventoryTurnoverRate` (Double): Calculated turnover rate
- `performanceScore` (Double): Calculated performance metric
- `isUnderperforming` (Boolean): Flag for underperforming products
- `needsRestocking` (Boolean): Flag for low inventory
- `extractionDate` (LocalDateTime): When data was last extracted
- `analysisDate` (LocalDateTime): When analysis was last performed

**Relationships**:
- One-to-Many with DataExtraction (one product can be extracted multiple times)
- Many-to-Many with Report (products can appear in multiple reports)

**Business Rules**:
- Product name is required and cannot be empty
- Stock level cannot be negative
- Sales volume cannot be negative
- Revenue cannot be negative
- Performance score is calculated based on sales volume, revenue, and inventory turnover

**Note**: The `status` field from Pet Store API (available/pending/sold) will be managed as entity state, not as a business attribute.

### 2. DataExtraction Entity

**Purpose**: Represents a data extraction session from the Pet Store API.

**Attributes**:
- `extractionId` (String): Unique identifier for extraction session
- `scheduledTime` (LocalDateTime): When extraction was scheduled
- `startTime` (LocalDateTime): When extraction started
- `endTime` (LocalDateTime): When extraction completed
- `extractionType` (String): Type of extraction (SCHEDULED, MANUAL, RETRY)
- `totalProductsExtracted` (Integer): Number of products extracted
- `totalInventoryRecords` (Integer): Number of inventory records extracted
- `extractionFormat` (String): Data format used (JSON, XML)
- `apiEndpoint` (String): Pet Store API endpoint used
- `errorMessage` (String): Error details if extraction failed
- `retryCount` (Integer): Number of retry attempts
- `dataQualityScore` (Double): Quality assessment of extracted data

**Relationships**:
- One-to-Many with Product (one extraction can extract multiple products)
- One-to-One with Report (each successful extraction can generate one report)

**Business Rules**:
- Extraction ID must be unique
- Start time cannot be after end time
- Retry count cannot be negative
- Data quality score must be between 0.0 and 1.0

### 3. Report Entity

**Purpose**: Represents a generated performance analysis report.

**Attributes**:
- `reportId` (String): Unique identifier for the report
- `reportType` (String): Type of report (WEEKLY_SUMMARY, PERFORMANCE_ANALYSIS)
- `generationDate` (LocalDateTime): When report was generated
- `reportPeriodStart` (LocalDateTime): Start of analysis period
- `reportPeriodEnd` (LocalDateTime): End of analysis period
- `totalProductsAnalyzed` (Integer): Number of products in analysis
- `topPerformingProductCount` (Integer): Number of top performing products
- `underperformingProductCount` (Integer): Number of underperforming products
- `restockingRequiredCount` (Integer): Number of products needing restock
- `totalRevenue` (BigDecimal): Total revenue for period
- `averageInventoryTurnover` (Double): Average turnover rate
- `reportFilePath` (String): Path to generated report file
- `reportFormat` (String): Format of report (PDF, HTML, JSON)
- `emailSent` (Boolean): Whether report was emailed
- `emailSentDate` (LocalDateTime): When email was sent
- `recipientEmail` (String): Email recipient address
- `reportSummary` (String): Brief summary of key findings

**Relationships**:
- One-to-One with DataExtraction (each report is based on one extraction)
- Many-to-Many with Product (reports can include multiple products)

**Business Rules**:
- Report ID must be unique
- Report period start cannot be after end date
- Product counts cannot be negative
- Total revenue cannot be negative
- Recipient email must be valid format
- Report file path must be accessible

### 4. EmailNotification Entity

**Purpose**: Tracks email notifications sent for reports.

**Attributes**:
- `notificationId` (String): Unique identifier for notification
- `recipientEmail` (String): Email address of recipient
- `subject` (String): Email subject line
- `sentDate` (LocalDateTime): When email was sent
- `deliveryStatus` (String): Delivery status (SENT, DELIVERED, FAILED, BOUNCED)
- `attachmentPath` (String): Path to attached report file
- `emailBody` (String): Email content body
- `retryCount` (Integer): Number of send attempts
- `errorMessage` (String): Error details if sending failed

**Relationships**:
- One-to-One with Report (each notification is for one report)

**Business Rules**:
- Notification ID must be unique
- Recipient email must be valid format
- Retry count cannot be negative
- Sent date is required for successful sends

## Entity State Management

### Product Entity States
- `INITIAL` → `EXTRACTED` → `ANALYZED` → `REPORTED`
- Products move through states as they are processed in the workflow

### DataExtraction Entity States  
- `INITIAL` → `SCHEDULED` → `IN_PROGRESS` → `COMPLETED` / `FAILED`
- Failed extractions can retry from `FAILED` → `SCHEDULED`

### Report Entity States
- `INITIAL` → `GENERATING` → `GENERATED` → `EMAILED` / `EMAIL_FAILED`
- Email failures can retry from `EMAIL_FAILED` → `GENERATING`

### EmailNotification Entity States
- `INITIAL` → `SENDING` → `SENT` → `DELIVERED` / `FAILED`
- Failed notifications can retry from `FAILED` → `SENDING`

## Data Integration Notes

### Pet Store API Mapping
- Pet Store `Pet.id` → Product `petId`
- Pet Store `Pet.name` → Product `name`  
- Pet Store `Pet.category.name` → Product `category`
- Pet Store `Pet.category.id` → Product `categoryId`
- Pet Store `Pet.photoUrls` → Product `photoUrls`
- Pet Store `Pet.tags[].name` → Product `tags`
- Pet Store `Pet.status` → Managed as entity state (not business attribute)
- Pet Store `/store/inventory` → Product `stockLevel`

### Calculated Fields
- `price`: Derived from business logic or external pricing data
- `salesVolume`: Calculated from order history or mock data
- `revenue`: Calculated as price × salesVolume
- `inventoryTurnoverRate`: Calculated as salesVolume / average stock level
- `performanceScore`: Weighted calculation of sales, revenue, and turnover
- `isUnderperforming`: Boolean flag based on performance thresholds
- `needsRestocking`: Boolean flag based on stock level thresholds
