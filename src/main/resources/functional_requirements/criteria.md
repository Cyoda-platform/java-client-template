# Criteria Requirements

## Overview
This document defines the criteria that implement conditional logic for the Product Performance Analysis and Reporting System. Criteria are used to validate data, check business rules, and control workflow transitions.

## Product Entity Criteria

### 1. ProductAvailabilityCriterion

**Entity**: Product  
**Transition**: make_available (validated → available)  
**Purpose**: Check if product meets availability criteria for analysis

**Validation Logic**:
```
Check product data completeness:
- product.name is not null and not empty
- product.id is not null and > 0
- product.category is not null and not empty
- product.photoUrls is not null and not empty

Check product business rules:
- If product.stockQuantity is null, set to 0
- If product.price is null or <= 0, calculate from category average
- Product must have at least one photo URL

Return SUCCESS if all checks pass
Return FAILURE with specific reason if any check fails
```

**Evaluation Outcome**:
- **Success**: Product is ready for performance analysis
- **Failure**: Product data is incomplete or invalid
  - Structural failure: Missing required fields
  - Business rule failure: Invalid business data
  - Data quality failure: Poor data quality

### 2. ProductArchiveCriterion

**Entity**: Product  
**Transition**: archive_product (analyzed → archived)  
**Purpose**: Check if product should be archived

**Validation Logic**:
```
Check archival conditions:
- Product has been in 'analyzed' state for > 30 days
- OR product.stockQuantity = 0 for > 90 days
- OR product has no sales in last 180 days
- OR product is marked as discontinued

Check business constraints:
- Product is not part of active promotional campaigns
- Product has no pending orders
- Product performance analysis is complete

Return SUCCESS if product should be archived
Return FAILURE if product should remain active
```

**Evaluation Outcome**:
- **Success**: Product meets archival criteria
- **Failure**: Product should remain active

## PerformanceMetric Entity Criteria

### 3. MetricValidationCriterion

**Entity**: PerformanceMetric  
**Transition**: validate_metric (calculated → validated)  
**Purpose**: Validate calculated performance metrics

**Validation Logic**:
```
Check metric value validity:
- metricValue is not null
- metricValue is not negative (except for trend analysis)
- metricValue is within expected range for metric type:
  * SALES_VOLUME: 0 to 10,000 units
  * REVENUE: 0 to $1,000,000
  * INVENTORY_TURNOVER: 0 to 100
  * TREND_ANALYSIS: -100% to +100%

Check calculation period validity:
- periodStart is before periodEnd
- periodEnd is not in the future
- calculation period matches metric type requirements

Check data consistency:
- Compare with historical values for same product
- Flag if value differs by >500% from historical average
- Verify calculation timestamp is recent

Return SUCCESS if metric is valid
Return FAILURE with validation errors if invalid
```

**Evaluation Outcome**:
- **Success**: Metric calculation is valid and reliable
- **Failure**: Metric calculation has issues
  - Structural failure: Invalid metric structure
  - Data quality failure: Suspicious metric values
  - Business rule failure: Violates business constraints

### 4. MetricExpirationCriterion

**Entity**: PerformanceMetric  
**Transition**: expire_metric (published → expired)  
**Purpose**: Check if metrics should be expired

**Validation Logic**:
```
Check metric age:
- SALES_VOLUME metrics expire after 7 days
- REVENUE metrics expire after 7 days
- INVENTORY_TURNOVER metrics expire after 30 days
- TREND_ANALYSIS metrics expire after 90 days

Check data relevance:
- If underlying product data has changed significantly
- If calculation methodology has been updated
- If metric is marked for recalculation

Return SUCCESS if metric should be expired
Return FAILURE if metric is still valid
```

**Evaluation Outcome**:
- **Success**: Metric should be expired and recalculated
- **Failure**: Metric is still valid for use

## Report Entity Criteria

### 5. ReportQualityCriterion

**Entity**: Report  
**Transition**: review_report (generated → reviewed)  
**Purpose**: Validate report quality and completeness

**Validation Logic**:
```
Check report completeness:
- Report file exists at specified filePath
- Report file size is > 0 and < 50MB
- Report contains all required sections:
  * Executive summary
  * Product performance tables
  * Charts and visualizations
  * Recommendations

Check data accuracy:
- All referenced products exist in database
- All metrics used in report are validated
- Calculations in report match source data
- No missing or null values in critical sections

Check report format:
- PDF is properly formatted and readable
- All images and charts render correctly
- Text is not truncated or corrupted
- File is not password protected

Return SUCCESS if report meets quality standards
Return FAILURE with specific quality issues
```

**Evaluation Outcome**:
- **Success**: Report is ready for distribution
- **Failure**: Report has quality issues
  - Structural failure: Missing required sections
  - Data quality failure: Inaccurate or missing data
  - Business rule failure: Does not meet business standards

### 6. ReportArchiveCriterion

**Entity**: Report  
**Transition**: archive_report (distributed → archived)  
**Purpose**: Check if reports should be archived

**Validation Logic**:
```
Check report age:
- Report has been distributed for > 90 days
- Report is not the most recent report of its type
- Report has been superseded by newer version

Check storage constraints:
- Total report storage exceeds threshold
- Report is not flagged for retention
- Report is not referenced by active analysis

Return SUCCESS if report should be archived
Return FAILURE if report should be retained
```

**Evaluation Outcome**:
- **Success**: Report can be safely archived
- **Failure**: Report should be retained

## EmailNotification Entity Criteria

### 7. EmailFailureCriterion

**Entity**: EmailNotification  
**Transition**: mark_failed (sending → failed)  
**Purpose**: Determine if email sending has failed

**Validation Logic**:
```
Check SMTP response codes:
- 5xx codes indicate permanent failure
- 4xx codes indicate temporary failure
- Network timeout or connection errors

Check email content issues:
- Attachment file is missing or corrupted
- Email size exceeds server limits
- Invalid recipient email format

Check server constraints:
- SMTP server is unavailable
- Authentication failed
- Rate limiting exceeded

Return SUCCESS if email has permanently failed
Return FAILURE if failure is temporary/retryable
```

**Evaluation Outcome**:
- **Success**: Email has permanently failed
- **Failure**: Email failure is temporary

### 8. EmailRetryCriterion

**Entity**: EmailNotification  
**Transition**: queue_retry (failed → retry)  
**Purpose**: Check if failed email should be retried

**Validation Logic**:
```
Check retry eligibility:
- retryCount < maxRetries (default: 3)
- Failure was temporary (4xx SMTP code)
- Time since last attempt > minimum retry interval

Check retry conditions:
- SMTP server is now available
- Attachment file still exists
- Recipient email is still valid
- Email content has not expired

Return SUCCESS if email should be retried
Return FAILURE if email should not be retried
```

**Evaluation Outcome**:
- **Success**: Email is eligible for retry
- **Failure**: Email should not be retried

## DataExtractionJob Entity Criteria

### 9. JobFailureCriterion

**Entity**: DataExtractionJob  
**Transition**: fail_job (running → failed)  
**Purpose**: Determine if data extraction job has failed

**Validation Logic**:
```
Check API connectivity:
- Pet Store API is unreachable
- Authentication failed
- API rate limits exceeded

Check data extraction results:
- No products were successfully extracted
- More than 50% of API calls failed
- Critical API endpoints are unavailable

Check system resources:
- Database connection failed
- Insufficient disk space for data
- Memory or processing limits exceeded

Return SUCCESS if job has failed
Return FAILURE if job can continue
```

**Evaluation Outcome**:
- **Success**: Job has failed and should be marked as failed
- **Failure**: Job can continue or recover

### 10. JobRetryCriterion

**Entity**: DataExtractionJob  
**Transition**: queue_retry (failed → retrying)  
**Purpose**: Check if failed job should be retried

**Validation Logic**:
```
Check retry conditions:
- Job failure was due to temporary issues
- Pet Store API is now available
- System resources are available
- Retry count is within limits

Check business constraints:
- Job is still within scheduled execution window
- No newer job has been scheduled
- Data extraction is still needed

Return SUCCESS if job should be retried
Return FAILURE if job should not be retried
```

**Evaluation Outcome**:
- **Success**: Job is eligible for retry
- **Failure**: Job should not be retried

## Cross-Entity Criteria

### 11. SystemHealthCriterion

**Purpose**: Check overall system health before critical operations  
**Used By**: Multiple workflows for system-wide validation

**Validation Logic**:
```
Check system resources:
- Database connectivity and performance
- Available disk space > 10GB
- Memory usage < 80%
- CPU usage < 90%

Check external dependencies:
- Pet Store API availability
- Email server connectivity
- File system accessibility

Check data integrity:
- No corrupted entities in database
- All required reference data exists
- No orphaned records

Return SUCCESS if system is healthy
Return FAILURE with specific health issues
```

**Evaluation Outcome**:
- **Success**: System is healthy for operations
- **Failure**: System has health issues that need attention

## Criteria Implementation Notes

1. **Error Handling**: All criteria include comprehensive error handling and logging
2. **Performance**: Criteria are designed to execute quickly to avoid workflow delays
3. **Configurability**: Validation thresholds and rules are configurable via application properties
4. **Monitoring**: All criteria execution is logged for monitoring and debugging
5. **Consistency**: Criteria use consistent validation patterns and error reporting
6. **Extensibility**: Criteria are designed to be easily extended with additional validation rules
