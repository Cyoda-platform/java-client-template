# Processor Requirements

## Overview
This document defines the detailed requirements for all processors in the Product Performance Analysis and Reporting System. Each processor implements specific business logic for workflow transitions.

## Product Entity Processors

### 1. ProductExtractionProcessor
**Entity**: Product  
**Transition**: none → extracted  
**Purpose**: Extracts product data from Pet Store API and populates product entity

**Input Data**: 
- Pet Store API endpoint URLs
- Authentication credentials
- Data format preferences (JSON/XML)

**Process Logic**:
```
BEGIN ProductExtractionProcessor
  1. Authenticate with Pet Store API using configured credentials
  2. Fetch all pets from /pet/findByStatus endpoint with status=["available","pending","sold"]
  3. Fetch inventory data from /store/inventory endpoint
  4. FOR each pet in response:
     a. Map pet data to Product entity fields
     b. Set petId = pet.id
     c. Set name = pet.name
     d. Set category = pet.category.name if exists
     e. Set categoryId = pet.category.id if exists
     f. Set photoUrls = pet.photoUrls
     g. Set tags = extract names from pet.tags array
     h. Set stockLevel = inventory[pet.status] or 0
     i. Set extractionDate = current timestamp
     j. Initialize performance fields to default values
  5. Save or update Product entity in database
  6. Log extraction statistics (total products, success/failure counts)
END
```

**Expected Output**: 
- Updated Product entity with extracted data
- Entity state changed to "extracted"
- Extraction metadata populated

### 2. ProductAnalysisProcessor
**Entity**: Product  
**Transition**: extracted → analyzed, reported → analyzed  
**Purpose**: Calculates performance metrics and analysis flags

**Input Data**:
- Product entity with extracted data
- Historical sales data (mock or external)
- Performance thresholds configuration

**Process Logic**:
```
BEGIN ProductAnalysisProcessor
  1. Retrieve product entity from input
  2. Generate mock sales data based on product attributes:
     a. Calculate salesVolume based on category popularity and stock level
     b. Generate price based on category and product attributes
     c. Calculate revenue = price * salesVolume
     d. Set lastSaleDate = random date within last 30 days
  3. Calculate performance metrics:
     a. inventoryTurnoverRate = salesVolume / max(stockLevel, 1)
     b. performanceScore = weighted calculation:
        - 40% sales volume (normalized)
        - 30% revenue (normalized)  
        - 30% inventory turnover rate (normalized)
  4. Apply business rules:
     a. isUnderperforming = performanceScore < 0.3
     b. needsRestocking = stockLevel < 10
  5. Set analysisDate = current timestamp
  6. Update Product entity with calculated values
END
```

**Expected Output**:
- Product entity with calculated performance metrics
- Entity state changed to "analyzed"
- Performance flags set based on thresholds

### 3. ProductReportingProcessor
**Entity**: Product  
**Transition**: analyzed → reported  
**Purpose**: Prepares product data for inclusion in reports

**Input Data**:
- Analyzed Product entity
- Report formatting preferences

**Process Logic**:
```
BEGIN ProductReportingProcessor
  1. Retrieve analyzed product entity
  2. Format product data for reporting:
     a. Round numerical values to appropriate precision
     b. Format currency values with proper symbols
     c. Convert dates to readable format
     d. Categorize product performance (High/Medium/Low)
  3. Generate product summary text
  4. Create reporting metadata
  5. Mark product as ready for report inclusion
END
```

**Expected Output**:
- Product entity formatted for reporting
- Entity state changed to "reported"

## DataExtraction Entity Processors

### 4. ExtractionSchedulingProcessor
**Entity**: DataExtraction  
**Transition**: none → scheduled  
**Purpose**: Schedules data extraction for specified time

**Input Data**:
- Scheduling configuration (every Monday)
- Current system time

**Process Logic**:
```
BEGIN ExtractionSchedulingProcessor
  1. Create new DataExtraction entity
  2. Generate unique extractionId
  3. Calculate next Monday at configured time
  4. Set scheduledTime = calculated Monday time
  5. Set extractionType = "SCHEDULED"
  6. Set extractionFormat = "JSON" (default)
  7. Set apiEndpoint = Pet Store API base URL
  8. Initialize counters to zero
  9. Save DataExtraction entity
END
```

**Expected Output**:
- New DataExtraction entity created
- Entity state changed to "scheduled"

### 5. DataExtractionProcessor
**Entity**: DataExtraction  
**Transition**: scheduled → in_progress  
**Purpose**: Begins data extraction from Pet Store API

**Input Data**:
- Scheduled DataExtraction entity
- API configuration

**Process Logic**:
```
BEGIN DataExtractionProcessor
  1. Set startTime = current timestamp
  2. Set extractionType = current extraction type
  3. Initialize progress tracking variables
  4. Begin API data extraction:
     a. Call Pet Store API endpoints
     b. Track number of products extracted
     c. Track number of inventory records
     d. Handle API rate limiting
     e. Monitor for errors or timeouts
  5. For each extracted product:
     a. Trigger ProductExtractionProcessor
     b. Update progress counters
  6. Calculate dataQualityScore based on:
     a. Completeness of data fields
     b. Data validation results
     c. API response consistency
END
```

**Expected Output**:
- DataExtraction entity with progress tracking
- Entity state changed to "in_progress"
- Product extraction processes triggered

### 6. ExtractionCompletionProcessor
**Entity**: DataExtraction  
**Transition**: in_progress → completed  
**Purpose**: Finalizes successful extraction and triggers analysis

**Input Data**:
- In-progress DataExtraction entity
- Extraction results

**Process Logic**:
```
BEGIN ExtractionCompletionProcessor
  1. Set endTime = current timestamp
  2. Finalize extraction statistics
  3. Calculate final dataQualityScore
  4. Log extraction success metrics
  5. Trigger report generation workflow
  6. Clean up temporary resources
  7. Schedule next extraction if recurring
END
```

**Expected Output**:
- Completed DataExtraction entity
- Entity state changed to "completed"
- Report generation triggered

### 7. ExtractionFailureProcessor
**Entity**: DataExtraction  
**Transition**: in_progress → failed  
**Purpose**: Handles extraction failures and logs errors

**Input Data**:
- Failed DataExtraction entity
- Error information

**Process Logic**:
```
BEGIN ExtractionFailureProcessor
  1. Set endTime = current timestamp
  2. Capture error details in errorMessage
  3. Increment retryCount
  4. Log failure details for monitoring
  5. Determine if retry is eligible:
     a. Check retry count limits
     b. Check error type (temporary vs permanent)
  6. Send failure notification to administrators
  7. Clean up partial extraction data if needed
END
```

**Expected Output**:
- Failed DataExtraction entity with error details
- Entity state changed to "failed"
- Administrator notification sent

## Report Entity Processors

### 8. ReportInitializationProcessor
**Entity**: Report  
**Transition**: none → generating  
**Purpose**: Initializes report generation process

**Input Data**:
- Completed DataExtraction entity
- Report configuration

**Process Logic**:
```
BEGIN ReportInitializationProcessor
  1. Create new Report entity
  2. Generate unique reportId
  3. Set reportType = "WEEKLY_SUMMARY"
  4. Set generationDate = current timestamp
  5. Calculate report period (last 7 days)
  6. Set recipientEmail = "victoria.sagdieva@cyoda.com"
  7. Initialize counters and metrics
  8. Prepare report template
END
```

**Expected Output**:
- New Report entity created
- Entity state changed to "generating"

### 9. ReportGenerationProcessor
**Entity**: Report  
**Transition**: generating → generated  
**Purpose**: Generates PDF/HTML report with analysis results

**Input Data**:
- Report entity in generating state
- Analyzed Product entities
- Report templates

**Process Logic**:
```
BEGIN ReportGenerationProcessor
  1. Gather all analyzed products for report period
  2. Calculate aggregate statistics:
     a. totalProductsAnalyzed
     b. topPerformingProductCount (top 20%)
     c. underperformingProductCount
     d. restockingRequiredCount
     e. totalRevenue
     f. averageInventoryTurnover
  3. Generate report sections:
     a. Executive summary
     b. Sales trends analysis
     c. Top performing products table
     d. Underperforming products table
     e. Inventory status and restocking needs
     f. Performance insights and recommendations
  4. Create PDF report file
  5. Generate HTML version for email body
  6. Save report files to configured directory
  7. Update Report entity with file paths and statistics
END
```

**Expected Output**:
- Generated report files (PDF and HTML)
- Report entity with complete statistics
- Entity state changed to "generated"

### 10. EmailSendingProcessor
**Entity**: Report  
**Transition**: generated → emailed  
**Purpose**: Sends report via email to recipients

**Input Data**:
- Generated Report entity
- Email configuration

**Process Logic**:
```
BEGIN EmailSendingProcessor
  1. Prepare email content:
     a. Subject = "Weekly Product Performance Report - [Date]"
     b. Body = HTML report summary + key insights
     c. Attachment = PDF report file
  2. Configure email settings:
     a. Recipient = report.recipientEmail
     b. Sender = configured system email
     c. SMTP settings
  3. Send email via SMTP server
  4. Handle email sending result:
     a. If successful: set emailSent = true, emailSentDate = current time
     b. If failed: trigger email failure transition
  5. Log email sending result
END
```

**Expected Output**:
- Email sent to recipient
- Report entity updated with email status
- Entity state changed to "emailed" or triggers failure transition

**Other Entity Updates**:
- Create EmailNotification entity (transition: null)

## Additional Processors

### 11. ExtractionRetryProcessor
**Entity**: DataExtraction
**Transition**: failed → scheduled
**Purpose**: Reschedules failed extraction for retry

**Input Data**:
- Failed DataExtraction entity
- Retry policy configuration

**Process Logic**:
```
BEGIN ExtractionRetryProcessor
  1. Check retry eligibility:
     a. Verify retryCount < maxRetries
     b. Check if error is retryable
     c. Verify time since last attempt > retry delay
  2. If eligible for retry:
     a. Increment retryCount
     b. Calculate next retry time with exponential backoff
     c. Set scheduledTime = calculated retry time
     d. Clear previous error message
     e. Reset progress counters
  3. If not eligible:
     a. Mark as permanently failed
     b. Send administrator notification
END
```

**Expected Output**:
- DataExtraction entity rescheduled for retry
- Entity state changed to "scheduled"

### 12. EmailFailureProcessor
**Entity**: Report
**Transition**: generated → email_failed
**Purpose**: Handles email sending failures

**Input Data**:
- Report entity with email failure
- Error details

**Process Logic**:
```
BEGIN EmailFailureProcessor
  1. Capture email failure details
  2. Log failure for monitoring
  3. Determine failure type (temporary/permanent)
  4. Update Report entity with failure information
  5. Send failure notification to administrators
  6. Prepare for potential retry
END
```

**Expected Output**:
- Report entity marked with email failure
- Entity state changed to "email_failed"

### 13. EmailRetryProcessor
**Entity**: Report
**Transition**: email_failed → generated
**Purpose**: Retries email sending

**Input Data**:
- Report entity with email failure
- Retry configuration

**Process Logic**:
```
BEGIN EmailRetryProcessor
  1. Check retry eligibility
  2. Reset email status flags
  3. Prepare for new email attempt
  4. Clear previous failure messages
END
```

**Expected Output**:
- Report entity ready for email retry
- Entity state changed to "generated"

## EmailNotification Entity Processors

### 14. EmailInitiationProcessor
**Entity**: EmailNotification
**Transition**: none → sending
**Purpose**: Prepares email content and attachments

**Input Data**:
- Report entity ready for email
- Email templates

**Process Logic**:
```
BEGIN EmailInitiationProcessor
  1. Create EmailNotification entity
  2. Generate unique notificationId
  3. Set recipientEmail from report
  4. Prepare email subject and body
  5. Set attachment path to report file
  6. Initialize sending parameters
END
```

**Expected Output**:
- EmailNotification entity created
- Entity state changed to "sending"

### 15. EmailSentProcessor
**Entity**: EmailNotification
**Transition**: sending → sent
**Purpose**: Confirms email was sent to mail server

**Input Data**:
- EmailNotification entity
- SMTP response

**Process Logic**:
```
BEGIN EmailSentProcessor
  1. Record successful SMTP submission
  2. Set sentDate = current timestamp
  3. Set deliveryStatus = "SENT"
  4. Log sending success
END
```

**Expected Output**:
- EmailNotification entity marked as sent
- Entity state changed to "sent"

### 16. EmailDeliveredProcessor
**Entity**: EmailNotification
**Transition**: sent → delivered
**Purpose**: Confirms email was delivered to recipient

**Input Data**:
- EmailNotification entity
- Delivery confirmation

**Process Logic**:
```
BEGIN EmailDeliveredProcessor
  1. Record delivery confirmation
  2. Set deliveryStatus = "DELIVERED"
  3. Log successful delivery
  4. Update final delivery metrics
END
```

**Expected Output**:
- EmailNotification entity marked as delivered
- Entity state changed to "delivered"
