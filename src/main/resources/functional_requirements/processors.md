# Processor Specifications

## Overview
This document defines the processors that implement the business logic for each workflow transition in the book data analysis application.

## Book Entity Processors

### 1. BookDataExtractionProcessor

**Entity**: Book  
**Transition**: `extract_book_data` (none → extracted)  
**Purpose**: Retrieves book data from the Fake REST API

**Input Data**: Book entity with minimal data (bookId may be null for bulk extraction)  
**Output**: Book entity populated with API data  
**Next Transition**: Automatic progression to `analyze_book_data`

**Pseudocode**:
```
PROCESS extract_book_data:
    IF book.bookId is null THEN
        // Bulk extraction scenario
        apiBooks = CALL FakeRestAPI.getAllBooks()
        FOR each apiBook in apiBooks:
            book.bookId = apiBook.id
            book.title = apiBook.title
            book.description = apiBook.description
            book.pageCount = apiBook.pageCount
            book.excerpt = apiBook.excerpt
            book.publishDate = PARSE_DATE(apiBook.publishDate)
            book.retrievedAt = CURRENT_TIMESTAMP()
            SAVE book as new entity
    ELSE
        // Single book extraction
        apiBook = CALL FakeRestAPI.getBookById(book.bookId)
        book.title = apiBook.title
        book.description = apiBook.description
        book.pageCount = apiBook.pageCount
        book.excerpt = apiBook.excerpt
        book.publishDate = PARSE_DATE(apiBook.publishDate)
        book.retrievedAt = CURRENT_TIMESTAMP()
    
    LOG "Book data extracted for ID: " + book.bookId
    RETURN book
```

### 2. BookAnalysisProcessor

**Entity**: Book  
**Transition**: `analyze_book_data` (extracted → analyzed)  
**Purpose**: Analyzes book data and calculates metrics

**Input Data**: Book entity with extracted API data  
**Output**: Book entity with calculated analysis score  
**Next Transition**: Automatic progression to `complete_book_processing`

**Pseudocode**:
```
PROCESS analyze_book_data:
    // Calculate popularity score based on multiple factors
    pageScore = NORMALIZE(book.pageCount, 0, 1000) * 0.3
    titleScore = CALCULATE_TITLE_POPULARITY(book.title) * 0.2
    descriptionScore = CALCULATE_DESCRIPTION_RICHNESS(book.description) * 0.2
    excerptScore = CALCULATE_EXCERPT_QUALITY(book.excerpt) * 0.1
    dateScore = CALCULATE_RECENCY_SCORE(book.publishDate) * 0.2
    
    book.analysisScore = pageScore + titleScore + descriptionScore + excerptScore + dateScore
    
    LOG "Analysis completed for book: " + book.title + " (Score: " + book.analysisScore + ")"
    RETURN book

FUNCTION CALCULATE_TITLE_POPULARITY(title):
    popularWords = ["Guide", "Complete", "Ultimate", "Essential", "Mastering"]
    score = 0
    FOR each word in popularWords:
        IF title CONTAINS word THEN score += 0.2
    RETURN MIN(score, 1.0)

FUNCTION CALCULATE_DESCRIPTION_RICHNESS(description):
    IF description is null OR description.length < 50 THEN RETURN 0.1
    IF description.length > 500 THEN RETURN 1.0
    RETURN description.length / 500.0

FUNCTION CALCULATE_EXCERPT_QUALITY(excerpt):
    IF excerpt is null OR excerpt.length < 20 THEN RETURN 0.1
    RETURN MIN(excerpt.length / 200.0, 1.0)

FUNCTION CALCULATE_RECENCY_SCORE(publishDate):
    yearsOld = CURRENT_YEAR() - publishDate.year
    IF yearsOld <= 1 THEN RETURN 1.0
    IF yearsOld >= 10 THEN RETURN 0.1
    RETURN 1.0 - (yearsOld / 10.0)
```

## Report Entity Processors

### 3. ReportGenerationProcessor

**Entity**: Report  
**Transition**: `generate_report` (none → generated)  
**Purpose**: Generates analytics report from analyzed book data

**Input Data**: Report entity with basic metadata  
**Output**: Report entity with generated analytics content  
**Next Transition**: Automatic progression to `send_report_email`

**Pseudocode**:
```
PROCESS generate_report:
    // Get all analyzed books for the reporting period
    books = GET_BOOKS_BY_DATE_RANGE(report.reportPeriodStart, report.reportPeriodEnd)
    analyzedBooks = FILTER books WHERE state = "analyzed" OR state = "completed"
    
    // Calculate aggregate metrics
    report.totalBooksAnalyzed = analyzedBooks.count()
    report.totalPageCount = SUM(analyzedBooks.pageCount)
    report.averagePageCount = report.totalPageCount / report.totalBooksAnalyzed
    
    // Identify popular titles (top 10 by analysis score)
    topBooks = SORT analyzedBooks BY analysisScore DESC LIMIT 10
    popularTitlesArray = []
    FOR each book in topBooks:
        popularTitlesArray.ADD({
            "title": book.title,
            "score": book.analysisScore,
            "pageCount": book.pageCount
        })
    report.popularTitles = JSON_STRINGIFY(popularTitlesArray)
    
    // Analyze publication dates
    publicationInsights = ANALYZE_PUBLICATION_DATES(analyzedBooks)
    report.publicationDateInsights = JSON_STRINGIFY(publicationInsights)
    
    // Generate summary text
    report.reportSummary = GENERATE_SUMMARY_TEXT(report)
    report.generatedAt = CURRENT_TIMESTAMP()
    
    LOG "Report generated: " + report.reportId
    RETURN report

FUNCTION ANALYZE_PUBLICATION_DATES(books):
    yearCounts = GROUP books BY publishDate.year
    insights = {
        "totalYearsSpanned": yearCounts.keys().max() - yearCounts.keys().min(),
        "mostProductiveYear": yearCounts.maxByValue().key,
        "booksInMostProductiveYear": yearCounts.maxByValue().value,
        "averageBooksPerYear": books.count() / yearCounts.keys().count()
    }
    RETURN insights

FUNCTION GENERATE_SUMMARY_TEXT(report):
    summary = "Weekly Book Analytics Report\n"
    summary += "Generated on: " + report.generatedAt + "\n"
    summary += "Period: " + report.reportPeriodStart + " to " + report.reportPeriodEnd + "\n\n"
    summary += "Key Insights:\n"
    summary += "- Total books analyzed: " + report.totalBooksAnalyzed + "\n"
    summary += "- Total pages: " + report.totalPageCount + "\n"
    summary += "- Average pages per book: " + ROUND(report.averagePageCount, 2) + "\n"
    summary += "- Top performing titles included in popular titles section\n"
    RETURN summary
```

### 4. ReportEmailProcessor

**Entity**: Report  
**Transition**: `send_report_email` (generated → email_sending)  
**Purpose**: Sends the generated report via email to analytics team

**Input Data**: Report entity with generated content  
**Output**: Report entity with email sending status  
**Next Transition**: Automatic progression to `confirm_email_delivery`

**Pseudocode**:
```
PROCESS send_report_email:
    emailSubject = "Weekly Book Analytics Report - " + report.reportId
    emailBody = COMPOSE_EMAIL_BODY(report)
    recipients = SPLIT(report.emailRecipients, ",")
    
    TRY:
        emailService = GET_EMAIL_SERVICE()
        emailService.SEND_EMAIL(
            to: recipients,
            subject: emailSubject,
            body: emailBody,
            attachments: []
        )
        
        report.emailSentAt = CURRENT_TIMESTAMP()
        LOG "Email sent successfully for report: " + report.reportId
        
    CATCH EmailException as e:
        LOG_ERROR "Failed to send email for report: " + report.reportId + " - " + e.message
        THROW ProcessingException("Email sending failed: " + e.message)
    
    RETURN report

FUNCTION COMPOSE_EMAIL_BODY(report):
    body = "Dear Analytics Team,\n\n"
    body += "Please find the weekly book analytics report below:\n\n"
    body += report.reportSummary + "\n\n"
    
    // Add popular titles section
    popularTitles = JSON_PARSE(report.popularTitles)
    body += "Popular Titles (Top 10):\n"
    FOR each title in popularTitles:
        body += "- " + title.title + " (Score: " + title.score + ", Pages: " + title.pageCount + ")\n"
    
    body += "\n"
    
    // Add publication insights
    insights = JSON_PARSE(report.publicationDateInsights)
    body += "Publication Date Insights:\n"
    body += "- Years spanned: " + insights.totalYearsSpanned + "\n"
    body += "- Most productive year: " + insights.mostProductiveYear + " (" + insights.booksInMostProductiveYear + " books)\n"
    body += "- Average books per year: " + ROUND(insights.averageBooksPerYear, 2) + "\n\n"
    
    body += "Best regards,\nBook Analytics System"
    RETURN body
```

## AnalyticsJob Entity Processors

### 5. AnalyticsJobSchedulerProcessor

**Entity**: AnalyticsJob  
**Transition**: `schedule_job` (none → scheduled)  
**Purpose**: Schedules analytics job for execution

**Input Data**: AnalyticsJob entity with basic job information  
**Output**: AnalyticsJob entity with scheduling details  
**Next Transition**: Automatic progression to `start_job_execution` when scheduled time arrives

**Pseudocode**:
```
PROCESS schedule_job:
    // Set job scheduling details
    IF job.scheduledFor is null THEN
        job.scheduledFor = NEXT_WEDNESDAY_AT_TIME("09:00")
    
    // Generate unique job ID
    weekNumber = GET_WEEK_NUMBER(job.scheduledFor)
    year = job.scheduledFor.year
    job.jobId = "JOB-" + year + "-W" + weekNumber + "-WED"
    
    // Set configuration
    config = {
        "apiUrl": "https://fakerestapi.azurewebsites.net/api/v1/Books",
        "emailRecipients": "analytics-team@company.com",
        "reportType": "WEEKLY_ANALYTICS",
        "maxRetries": 3
    }
    job.configurationData = JSON_STRINGIFY(config)
    
    // Schedule next job
    nextWednesday = ADD_DAYS(job.scheduledFor, 7)
    nextJob = CREATE_ANALYTICS_JOB(nextWednesday)
    job.nextJobId = nextJob.jobId
    
    LOG "Job scheduled: " + job.jobId + " for " + job.scheduledFor
    RETURN job
```

### 6. AnalyticsJobExecutorProcessor

**Entity**: AnalyticsJob  
**Transition**: `start_job_execution` (scheduled → running)  
**Purpose**: Executes the analytics job by triggering book extraction and report generation

**Input Data**: AnalyticsJob entity in scheduled state  
**Output**: AnalyticsJob entity with execution status  
**Next Transition**: Automatic progression to `complete_job_successfully` or `fail_job_execution`

**Pseudocode**:
```
PROCESS start_job_execution:
    job.startedAt = CURRENT_TIMESTAMP()
    config = JSON_PARSE(job.configurationData)
    
    TRY:
        // Create book entities for data extraction
        bookEntity = CREATE_BOOK_ENTITY()
        TRIGGER_TRANSITION(bookEntity, "extract_book_data")
        
        // Wait for book processing to complete
        WAIT_FOR_BOOKS_TO_COMPLETE_ANALYSIS()
        
        // Create report entity
        reportEntity = CREATE_REPORT_ENTITY(
            reportPeriodStart: job.scheduledFor - 7 days,
            reportPeriodEnd: job.scheduledFor,
            emailRecipients: config.emailRecipients
        )
        TRIGGER_TRANSITION(reportEntity, "generate_report")
        
        job.booksProcessed = COUNT_PROCESSED_BOOKS()
        job.reportsGenerated = 1
        
        LOG "Job execution completed successfully: " + job.jobId
        
    CATCH Exception as e:
        job.errorMessage = e.message
        LOG_ERROR "Job execution failed: " + job.jobId + " - " + e.message
        THROW ProcessingException("Job execution failed: " + e.message)
    
    RETURN job
```

### 7. AnalyticsJobCompletionProcessor

**Entity**: AnalyticsJob  
**Transition**: `complete_job_successfully` (running → completed)  
**Purpose**: Finalizes successful job completion

**Input Data**: AnalyticsJob entity in running state  
**Output**: AnalyticsJob entity marked as completed  
**Next Transition**: None (terminal state)

**Pseudocode**:
```
PROCESS complete_job_successfully:
    job.completedAt = CURRENT_TIMESTAMP()
    
    // Log completion metrics
    duration = job.completedAt - job.startedAt
    LOG "Job completed: " + job.jobId + 
        " (Duration: " + duration + 
        ", Books: " + job.booksProcessed + 
        ", Reports: " + job.reportsGenerated + ")"
    
    // Trigger next scheduled job if exists
    IF job.nextJobId is not null THEN
        nextJob = GET_JOB_BY_ID(job.nextJobId)
        IF nextJob.scheduledFor <= CURRENT_TIMESTAMP() THEN
            TRIGGER_TRANSITION(nextJob, "start_job_execution")
    
    RETURN job
```

### 8. AnalyticsJobErrorProcessor

**Entity**: AnalyticsJob  
**Transition**: `fail_job_execution` (running → failed)  
**Purpose**: Handles job execution failures

**Input Data**: AnalyticsJob entity in running state with error  
**Output**: AnalyticsJob entity marked as failed  
**Next Transition**: Manual `retry_failed_job` available

**Pseudocode**:
```
PROCESS fail_job_execution:
    job.completedAt = CURRENT_TIMESTAMP()
    
    // Log failure details
    duration = job.completedAt - job.startedAt
    LOG_ERROR "Job failed: " + job.jobId + 
        " (Duration: " + duration + 
        ", Error: " + job.errorMessage + ")"
    
    // Send failure notification
    SEND_FAILURE_NOTIFICATION(job)
    
    RETURN job

FUNCTION SEND_FAILURE_NOTIFICATION(job):
    emailSubject = "Analytics Job Failed - " + job.jobId
    emailBody = "The analytics job " + job.jobId + " failed with error: " + job.errorMessage
    SEND_EMAIL("admin@company.com", emailSubject, emailBody)
```
