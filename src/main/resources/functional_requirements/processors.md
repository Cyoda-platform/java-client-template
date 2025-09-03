# Processor Requirements

## Overview
This document defines the processors required for the London Houses Data Analysis application. Each processor implements specific business logic for workflow transitions.

## DataSource Processors

### 1. DataSourceDownloadProcessor
**Entity**: DataSource  
**Transition**: start_download (active → downloading)  
**Input**: DataSource entity with URL and configuration  
**Output**: DataSource entity with download status updated  

**Pseudocode**:
```
BEGIN process(dataSource)
    SET downloadStartTime = current timestamp
    UPDATE dataSource.lastDownloadTime = downloadStartTime
    
    TRY
        VALIDATE dataSource.url is not empty
        VALIDATE dataSource.url is valid HTTP/HTTPS URL
        
        CREATE HTTP client with timeout configuration
        SEND GET request to dataSource.url
        
        IF response status is not 200
            THROW exception "Failed to download: HTTP " + status
        END IF
        
        READ response content as CSV
        VALIDATE CSV has expected columns from dataSource.expectedColumns
        
        STORE CSV data in temporary storage with dataSource.id as key
        LOG "Successfully downloaded data from " + dataSource.url
        
        RETURN dataSource (transition to downloading state)
        
    CATCH exception
        LOG ERROR "Download failed for " + dataSource.id + ": " + exception.message
        SET dataSource.errorMessage = exception.message
        TRIGGER transition download_failed (null transition - automatic)
        RETURN dataSource
    END TRY
END
```

### 2. DataSourceCompletionProcessor
**Entity**: DataSource  
**Transition**: download_completed (downloading → active)  
**Input**: DataSource entity after successful download  
**Output**: DataSource entity with completion status  

**Pseudocode**:
```
BEGIN process(dataSource)
    SET dataSource.lastDownloadTime = current timestamp
    CLEAR dataSource.errorMessage
    
    LOG "Data download completed for " + dataSource.id
    
    // Trigger analysis job creation
    CREATE new AnalysisJob entity
    SET analysisJob.dataSourceId = dataSource.id
    SET analysisJob.jobName = "Analysis for " + dataSource.name
    SET analysisJob.analysisType = "statistical_summary"
    SAVE analysisJob (triggers create_job transition)
    
    RETURN dataSource
END
```

### 3. DataSourceErrorProcessor
**Entity**: DataSource  
**Transition**: download_failed (downloading → error)  
**Input**: DataSource entity with error information  
**Output**: DataSource entity with error details logged  

**Pseudocode**:
```
BEGIN process(dataSource)
    LOG ERROR "Download failed for DataSource " + dataSource.id
    
    // Clean up any partial downloads
    DELETE temporary data for dataSource.id
    
    // Notify administrators if critical data source
    IF dataSource.isActive AND dataSource.id equals "london_houses"
        SEND notification to admin about critical data source failure
    END IF
    
    RETURN dataSource
END
```

## AnalysisJob Processors

### 4. AnalysisJobProcessor
**Entity**: AnalysisJob  
**Transition**: start_analysis (pending → running)  
**Input**: AnalysisJob entity ready for processing  
**Output**: AnalysisJob entity with analysis results  

**Pseudocode**:
```
BEGIN process(analysisJob)
    SET analysisJob.startTime = current timestamp
    
    TRY
        // Retrieve data from temporary storage
        csvData = GET stored data for analysisJob.dataSourceId
        IF csvData is null
            THROW exception "No data available for analysis"
        END IF
        
        // Perform pandas-like analysis
        dataFrame = PARSE csvData as structured data
        SET analysisJob.dataRowsProcessed = dataFrame.rowCount
        
        analysisResults = CREATE empty map
        
        // Statistical summary
        IF analysisJob.analysisType equals "statistical_summary"
            FOR each numeric column in dataFrame
                analysisResults[column + "_mean"] = CALCULATE mean of column
                analysisResults[column + "_median"] = CALCULATE median of column
                analysisResults[column + "_std"] = CALCULATE standard deviation of column
                analysisResults[column + "_min"] = CALCULATE minimum of column
                analysisResults[column + "_max"] = CALCULATE maximum of column
            END FOR
            
            analysisResults["total_records"] = dataFrame.rowCount
            analysisResults["analysis_date"] = current timestamp
        END IF
        
        SET analysisJob.resultData = CONVERT analysisResults to JSON string
        SET analysisJob.endTime = current timestamp
        
        LOG "Analysis completed for job " + analysisJob.id
        TRIGGER transition analysis_completed (null transition - automatic)
        
        RETURN analysisJob
        
    CATCH exception
        LOG ERROR "Analysis failed for job " + analysisJob.id + ": " + exception.message
        SET analysisJob.errorMessage = exception.message
        SET analysisJob.endTime = current timestamp
        TRIGGER transition analysis_failed (null transition - automatic)
        RETURN analysisJob
    END TRY
END
```

### 5. AnalysisCompletionProcessor
**Entity**: AnalysisJob  
**Transition**: analysis_completed (running → completed)  
**Input**: AnalysisJob entity with completed analysis  
**Output**: AnalysisJob entity and triggers Report generation  

**Pseudocode**:
```
BEGIN process(analysisJob)
    LOG "Analysis job " + analysisJob.id + " completed successfully"
    
    // Create report from analysis results
    CREATE new Report entity
    SET report.analysisJobId = analysisJob.id
    SET report.reportTitle = "London Houses Analysis Report - " + current date
    SET report.reportType = "summary"
    SET report.generatedTime = current timestamp
    SET report.format = "HTML"
    
    // Generate report content from analysis results
    analysisData = PARSE analysisJob.resultData as JSON
    reportContent = GENERATE HTML report from analysisData
    SET report.content = reportContent
    SET report.summary = EXTRACT key findings from analysisData
    
    SAVE report (triggers generate_report transition)
    
    RETURN analysisJob
END
```

## Report Processors

### 6. ReportGenerationProcessor
**Entity**: Report  
**Transition**: generate_report (none → generated)  
**Input**: Report entity with analysis data  
**Output**: Report entity with formatted content  

**Pseudocode**:
```
BEGIN process(report)
    // Get analysis job data
    analysisJob = GET AnalysisJob by report.analysisJobId
    analysisData = PARSE analysisJob.resultData as JSON
    
    // Generate HTML report content
    htmlContent = CREATE HTML document
    ADD title "London Houses Data Analysis Report"
    ADD section "Executive Summary" with report.summary
    ADD section "Statistical Analysis" with formatted analysisData
    ADD section "Key Findings" with insights from data
    ADD footer with generation timestamp
    
    SET report.content = htmlContent
    LOG "Report generated: " + report.id
    
    RETURN report
END
```

### 7. ReportEmailProcessor
**Entity**: Report  
**Transition**: start_sending (generated → sending)  
**Input**: Report entity ready for distribution  
**Output**: Report entity with email sending initiated  

**Pseudocode**:
```
BEGIN process(report)
    // Get all active subscribers
    subscribers = GET all Subscriber entities where state = "active"
    
    IF subscribers is empty
        LOG WARNING "No active subscribers found for report " + report.id
        RETURN report
    END IF
    
    emailSubject = "London Houses Analysis Report - " + current date
    emailBody = report.content
    
    FOR each subscriber in subscribers
        TRY
            SEND email to subscriber.email with subject and body
            LOG "Email sent to " + subscriber.email
            UPDATE subscriber.lastEmailSent = current timestamp
            RESET subscriber.emailDeliveryFailures = 0
            
        CATCH email exception
            LOG ERROR "Failed to send email to " + subscriber.email
            INCREMENT subscriber.emailDeliveryFailures
            
            IF subscriber.emailDeliveryFailures >= 3
                TRIGGER transition email_bounced for subscriber (null transition)
            END IF
        END TRY
    END FOR
    
    TRIGGER transition email_sent (null transition - automatic)
    RETURN report
END
```

## Subscriber Processors

### 8. SubscriberRegistrationProcessor
**Entity**: Subscriber  
**Transition**: subscribe (none → active)  
**Input**: Subscriber entity with registration data  
**Output**: Subscriber entity with welcome email sent  

**Pseudocode**:
```
BEGIN process(subscriber)
    VALIDATE subscriber.email is valid email format
    VALIDATE subscriber.firstName is not empty
    
    SET subscriber.subscriptionDate = current timestamp
    SET subscriber.isActive = true
    SET subscriber.emailDeliveryFailures = 0
    
    // Send welcome email
    welcomeSubject = "Welcome to London Houses Analysis Reports"
    welcomeBody = "Thank you for subscribing to our analysis reports!"
    
    TRY
        SEND email to subscriber.email with welcomeSubject and welcomeBody
        LOG "Welcome email sent to " + subscriber.email
    CATCH exception
        LOG WARNING "Failed to send welcome email: " + exception.message
    END TRY
    
    RETURN subscriber
END
```

### 9. EmailBounceProcessor
**Entity**: Subscriber  
**Transition**: email_bounced (active → bounced)  
**Input**: Subscriber entity with email delivery failures  
**Output**: Subscriber entity marked as bounced  

**Pseudocode**:
```
BEGIN process(subscriber)
    LOG WARNING "Subscriber " + subscriber.email + " marked as bounced after " + 
                subscriber.emailDeliveryFailures + " failures"
    
    SET subscriber.isActive = false
    
    // Notify admin about bounced subscriber
    SEND notification to admin about bounced subscriber
    
    RETURN subscriber
END
```
