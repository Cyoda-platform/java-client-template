# Processors Requirements

## Overview
This document defines the processors that implement the business logic for the Product Performance Analysis and Reporting System. Each processor handles specific workflow transitions and business operations.

## Product Entity Processors

### 1. ProductExtractionProcessor

**Entity**: Product  
**Transition**: extract_product (none → extracted)  
**Input**: DataExtractionJob entity with API configuration  
**Purpose**: Extract product data from Pet Store API

**Process Logic**:
```
1. Get Pet Store API configuration from input job
2. Call Pet Store API endpoints:
   - GET /pet/findByStatus?status=available
   - GET /pet/findByStatus?status=pending  
   - GET /pet/findByStatus?status=sold
   - GET /store/inventory
3. For each pet returned:
   - Map pet data to Product entity
   - Set product.id = pet.id
   - Set product.name = pet.name
   - Set product.category = pet.category.name
   - Set product.categoryId = pet.category.id
   - Set product.photoUrls = pet.photoUrls
   - Set product.tags = pet.tags[].name
   - Set product.stockQuantity from inventory API
   - Initialize sales metrics to 0
   - Set createdAt = current timestamp
4. Save Product entity to database
5. Update job.recordsExtracted count
6. Return updated Product entity
```

**Output**: Product entity in 'extracted' state  
**Next Transition**: validate_product

### 2. ProductValidationProcessor

**Entity**: Product  
**Transition**: validate_product (extracted → validated)  
**Input**: Product entity in 'extracted' state  
**Purpose**: Validate and enrich product data

**Process Logic**:
```
1. Validate required fields:
   - Ensure product.name is not null/empty
   - Ensure product.id is valid
   - Ensure product.photoUrls is not empty
2. Enrich product data:
   - Set default price if missing (calculate from category average)
   - Normalize category names to standard format
   - Clean and validate photo URLs
   - Set default stockQuantity if missing
3. Calculate initial metrics:
   - Set salesVolume = 0 for new products
   - Set revenue = 0 for new products
   - Set lastSaleDate = null for new products
4. Set updatedAt = current timestamp
5. Return validated Product entity
```

**Output**: Product entity in 'validated' state  
**Next Transition**: make_available

### 3. ProductAnalysisInitiatorProcessor

**Entity**: Product  
**Transition**: start_analysis (available → analyzing)  
**Input**: Product entity in 'available' state  
**Purpose**: Initiate performance analysis for product

**Process Logic**:
```
1. Create PerformanceMetric entities for product:
   - Create SALES_VOLUME metric for current week
   - Create REVENUE metric for current week  
   - Create INVENTORY_TURNOVER metric for current month
   - Create TREND_ANALYSIS metric for last 3 months
2. For each metric:
   - Set metric.productId = product.id
   - Set metric.calculationPeriod based on type
   - Set metric.periodStart and periodEnd
   - Trigger metric calculation (transition to 'pending')
3. Update product.updatedAt = current timestamp
4. Return Product entity
```

**Output**: Product entity in 'analyzing' state  
**Other Entities**: Creates PerformanceMetric entities with transition to 'pending'

### 4. ProductAnalysisCompletionProcessor

**Entity**: Product  
**Transition**: complete_analysis (analyzing → analyzed)  
**Input**: Product entity in 'analyzing' state  
**Purpose**: Complete performance analysis when all metrics are calculated

**Process Logic**:
```
1. Check all PerformanceMetric entities for this product
2. Verify all metrics are in 'published' state
3. If all metrics complete:
   - Calculate aggregate performance score
   - Update product performance summary
   - Set analysis completion timestamp
4. If any metrics still pending:
   - Keep product in 'analyzing' state
   - Log waiting status
5. Return Product entity
```

**Output**: Product entity in 'analyzed' state  
**Next Transition**: archive_product (manual)

## PerformanceMetric Entity Processors

### 5. MetricQueueProcessor

**Entity**: PerformanceMetric  
**Transition**: queue_calculation (none → pending)  
**Input**: PerformanceMetric entity with calculation parameters  
**Purpose**: Queue metric for calculation

**Process Logic**:
```
1. Validate metric parameters:
   - Ensure productId exists
   - Ensure metricType is valid
   - Ensure calculation period is valid
2. Set calculation priority based on metric type:
   - SALES_VOLUME = high priority
   - REVENUE = high priority
   - INVENTORY_TURNOVER = medium priority
   - TREND_ANALYSIS = low priority
3. Set calculatedAt = null (not calculated yet)
4. Set isOutlier = false (default)
5. Return PerformanceMetric entity
```

**Output**: PerformanceMetric entity in 'pending' state  
**Next Transition**: start_calculation

### 6. MetricCalculationProcessor

**Entity**: PerformanceMetric  
**Transition**: start_calculation (pending → calculating)  
**Input**: PerformanceMetric entity in 'pending' state  
**Purpose**: Calculate performance metric value

**Process Logic**:
```
1. Get Product entity by productId
2. Based on metricType, calculate value:
   
   If SALES_VOLUME:
   - Query sales data for period
   - Sum total units sold
   - Set metricValue = total units
   
   If REVENUE:
   - Query sales data for period
   - Sum (units sold * price) for period
   - Set metricValue = total revenue
   
   If INVENTORY_TURNOVER:
   - Calculate average inventory for period
   - Calculate cost of goods sold
   - Set metricValue = COGS / average inventory
   
   If TREND_ANALYSIS:
   - Get historical sales data
   - Calculate trend slope using linear regression
   - Set metricValue = trend slope percentage

3. Detect outliers:
   - Compare with historical values for same product
   - Set isOutlier = true if value is >2 standard deviations
4. Set calculatedAt = current timestamp
5. Return PerformanceMetric entity
```

**Output**: PerformanceMetric entity in 'calculating' state  
**Next Transition**: complete_calculation

### 7. MetricCompletionProcessor

**Entity**: PerformanceMetric  
**Transition**: complete_calculation (calculating → calculated)  
**Input**: PerformanceMetric entity in 'calculating' state  
**Purpose**: Finalize metric calculation

**Process Logic**:
```
1. Validate calculated metric value:
   - Ensure metricValue is not null
   - Ensure metricValue is within reasonable bounds
2. Round metric value to appropriate precision:
   - SALES_VOLUME: round to integer
   - REVENUE: round to 2 decimal places
   - INVENTORY_TURNOVER: round to 3 decimal places
   - TREND_ANALYSIS: round to 2 decimal places
3. Log calculation completion
4. Return PerformanceMetric entity
```

**Output**: PerformanceMetric entity in 'calculated' state  
**Next Transition**: validate_metric

## Report Entity Processors

### 8. ReportSchedulingProcessor

**Entity**: Report  
**Transition**: schedule_report (none → scheduled)  
**Input**: Report configuration parameters  
**Purpose**: Schedule report generation

**Process Logic**:
```
1. Set report parameters:
   - reportName = "Weekly Performance Report " + current date
   - reportType = WEEKLY_SUMMARY
   - reportPeriodStart = last Monday
   - reportPeriodEnd = current Sunday
   - generationDate = current timestamp
2. Set file parameters:
   - filePath = "/reports/" + reportName + "_" + timestamp
   - fileFormat = PDF
3. Initialize counters:
   - totalProducts = 0
   - topPerformingProducts = empty list
   - underperformingProducts = empty list
   - keyInsights = empty list
4. Return Report entity
```

**Output**: Report entity in 'scheduled' state  
**Next Transition**: start_generation

### 9. ReportGenerationProcessor

**Entity**: Report  
**Transition**: start_generation (scheduled → generating)  
**Input**: Report entity in 'scheduled' state  
**Purpose**: Generate performance report

**Process Logic**:
```
1. Query all Products in 'analyzed' state for report period
2. Query all PerformanceMetrics in 'published' state for report period
3. Analyze data:
   - Calculate total products analyzed
   - Identify top 5 performing products by revenue
   - Identify bottom 5 performing products by sales volume
   - Calculate key insights:
     * Total revenue for period
     * Average inventory turnover
     * Products needing restocking (stock < 10)
     * Trending products (positive trend analysis)
4. Generate report content:
   - Create executive summary
   - Generate charts and graphs
   - Create product performance tables
   - Add recommendations
5. Save report to file system as PDF
6. Update report.filePath with actual file location
7. Create summary for email body
8. Return Report entity
```

**Output**: Report entity in 'generating' state  
**Next Transition**: complete_generation

### 10. ReportCompletionProcessor

**Entity**: Report  
**Transition**: complete_generation (generating → generated)  
**Input**: Report entity in 'generating' state  
**Purpose**: Finalize report generation

**Process Logic**:
```
1. Verify report file was created successfully
2. Validate report content:
   - Ensure all required sections are present
   - Verify data accuracy
   - Check file size is reasonable
3. Generate email summary:
   - Create brief overview of key findings
   - Include top 3 insights
   - Add link to full report
4. Set report.summary with email content
5. Log report completion
6. Return Report entity
```

**Output**: Report entity in 'generated' state  
**Next Transition**: review_report

### 11. ReportDistributionProcessor

**Entity**: Report  
**Transition**: distribute_report (reviewed → distributed)  
**Input**: Report entity in 'reviewed' state  
**Purpose**: Distribute report to recipients

**Process Logic**:
```
1. Create EmailNotification entity:
   - recipientEmail = "victoria.sagdieva@cyoda.com"
   - subject = "Weekly Product Performance Report - " + report period
   - bodyContent = report.summary
   - attachmentPath = report.filePath
   - scheduledSendTime = current timestamp
2. Set email parameters:
   - deliveryStatus = PENDING
   - retryCount = 0
   - maxRetries = 3
3. Trigger email notification (transition to 'pending')
4. Update report distribution timestamp
5. Return Report entity
```

**Output**: Report entity in 'distributed' state  
**Other Entities**: Creates EmailNotification entity with transition to 'pending'

## EmailNotification Entity Processors

### 12. EmailQueueProcessor

**Entity**: EmailNotification  
**Transition**: queue_email (none → pending)  
**Input**: EmailNotification entity with email details  
**Purpose**: Queue email for sending

**Process Logic**:
```
1. Validate email parameters:
   - Ensure recipientEmail is valid format
   - Ensure subject is not empty
   - Ensure bodyContent is not empty
   - Verify attachment file exists
2. Set email priority based on type:
   - Report emails = high priority
   - Notification emails = medium priority
3. Set scheduledSendTime if not provided
4. Initialize retry parameters
5. Return EmailNotification entity
```

**Output**: EmailNotification entity in 'pending' state  
**Next Transition**: start_sending

### 13. EmailSendingProcessor

**Entity**: EmailNotification  
**Transition**: start_sending (pending → sending)  
**Input**: EmailNotification entity in 'pending' state  
**Purpose**: Send email notification

**Process Logic**:
```
1. Configure email client with SMTP settings
2. Create email message:
   - Set recipient = recipientEmail
   - Set subject = subject
   - Set body = bodyContent (HTML format)
   - Attach report file from attachmentPath
3. Send email via SMTP
4. If send successful:
   - Set actualSendTime = current timestamp
   - Log successful send
5. If send fails:
   - Set errorMessage with failure details
   - Log error details
6. Return EmailNotification entity
```

**Output**: EmailNotification entity in 'sending' state  
**Next Transition**: mark_sent or mark_failed

### 14. EmailDeliveryConfirmationProcessor

**Entity**: EmailNotification  
**Transition**: confirm_delivery (sent → delivered)  
**Input**: EmailNotification entity in 'sent' state  
**Purpose**: Confirm email delivery

**Process Logic**:
```
1. Check email delivery status via SMTP response
2. If delivery confirmed:
   - Update deliveryStatus = DELIVERED
   - Log successful delivery
3. If delivery pending:
   - Keep in 'sent' state
   - Schedule recheck in 5 minutes
4. Return EmailNotification entity
```

**Output**: EmailNotification entity in 'delivered' state  
**Next Transition**: None (terminal state)

### 15. EmailRetryProcessor

**Entity**: EmailNotification  
**Transition**: retry_send (retry → sending)  
**Input**: EmailNotification entity in 'retry' state  
**Purpose**: Retry failed email sending

**Process Logic**:
```
1. Increment retryCount
2. Check if retryCount <= maxRetries
3. If within retry limit:
   - Wait for exponential backoff delay
   - Clear previous error message
   - Attempt to send email again
4. If retry limit exceeded:
   - Set deliveryStatus = FAILED permanently
   - Log final failure
5. Return EmailNotification entity
```

**Output**: EmailNotification entity in 'sending' state  
**Next Transition**: mark_sent or mark_failed

## DataExtractionJob Entity Processors

### 16. JobSchedulingProcessor

**Entity**: DataExtractionJob  
**Transition**: schedule_job (none → scheduled)  
**Input**: Job configuration parameters  
**Purpose**: Schedule data extraction job

**Process Logic**:
```
1. Set job parameters:
   - jobName = "Weekly Pet Store Data Extraction"
   - scheduledTime = every Monday at 9:00 AM
   - extractionType = PRODUCTS
   - apiEndpoint = "https://petstore.swagger.io/v2"
2. Initialize counters:
   - recordsExtracted = 0
   - recordsProcessed = 0
   - recordsFailed = 0
3. Set nextScheduledRun = scheduledTime + 7 days
4. Return DataExtractionJob entity
```

**Output**: DataExtractionJob entity in 'scheduled' state  
**Next Transition**: start_execution

### 17. JobExecutionProcessor

**Entity**: DataExtractionJob  
**Transition**: start_execution (scheduled → running)  
**Input**: DataExtractionJob entity in 'scheduled' state  
**Purpose**: Execute data extraction job

**Process Logic**:
```
1. Set startTime = current timestamp
2. Initialize API client for Pet Store
3. For each API endpoint to extract:
   - Call ProductExtractionProcessor for each product
   - Update recordsExtracted count
   - Handle any API errors gracefully
4. Log extraction progress
5. Return DataExtractionJob entity
```

**Output**: DataExtractionJob entity in 'running' state  
**Other Entities**: Triggers Product entities with transition to 'extracted'

### 18. JobCompletionProcessor

**Entity**: DataExtractionJob  
**Transition**: complete_job (running → completed)  
**Input**: DataExtractionJob entity in 'running' state  
**Purpose**: Complete data extraction job

**Process Logic**:
```
1. Set endTime = current timestamp
2. Calculate job duration = endTime - startTime
3. Finalize counters:
   - Verify recordsExtracted count
   - Set recordsProcessed = successful product validations
   - Set recordsFailed = failed product validations
4. Log job completion statistics
5. Schedule next job run:
   - Create new DataExtractionJob for next week
   - Set scheduledTime = current time + 7 days
6. Return DataExtractionJob entity
```

**Output**: DataExtractionJob entity in 'completed' state  
**Other Entities**: Creates new DataExtractionJob for next execution

## Cross-Entity Integration Notes

1. **Cascading Triggers**: Job completion triggers product extraction, which triggers metric calculation, which triggers report generation
2. **Error Propagation**: Failed processors update error logs and trigger appropriate failure transitions
3. **Retry Logic**: All processors implement retry mechanisms for transient failures
4. **Monitoring**: All processors log execution metrics for monitoring and debugging
