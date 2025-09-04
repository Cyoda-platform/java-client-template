# Criteria Requirements

## 1. BookingFetchFailureCriterion

### Description
Evaluates whether a booking fetch operation has failed and determines if the entity should transition to error state.

### Entity
Booking

### Purpose
Check if the booking fetch from Restful Booker API failed due to network issues, invalid booking ID, or API errors.

### Evaluation Logic
- Returns `true` if booking fetch failed (transition to error state)
- Returns `false` if booking fetch was successful (continue normal flow)

### Conditions Checked
1. HTTP response status is not 200
2. Network connectivity issues
3. Invalid booking ID (404 response)
4. API timeout or server errors (5xx responses)
5. Malformed response data

### Implementation Notes
- Keep evaluation simple and focused on failure detection
- Log failure reasons for debugging
- Consider retry-able vs non-retry-able failures

---

## 2. BookingValidationCriterion

### Description
Validates that booking data meets all business rules and data quality requirements after being fetched from the API.

### Entity
Booking

### Purpose
Ensure booking data is complete, valid, and meets business requirements before marking as validated.

### Evaluation Logic
- Returns `true` if all validation checks pass (transition to validated state)
- Returns `false` if any validation fails (transition to error state)

### Conditions Checked

#### Required Field Validation
1. `firstname` is not null and not empty
2. `lastname` is not null and not empty
3. `totalprice` is not null and greater than 0
4. `checkin` date is not null and is a valid date
5. `checkout` date is not null and is a valid date
6. `depositpaid` is not null (boolean value)

#### Business Rule Validation
1. Check-in date must be before check-out date
2. Total price must be within reasonable range (0 < price <= 50000)
3. Date range must be reasonable (not more than 365 days)
4. Check-in date should not be more than 2 years in the past
5. Check-out date should not be more than 5 years in the future

#### Data Quality Validation
1. First name and last name should contain only valid characters
2. Additional needs field should not exceed 500 characters
3. Price should be a reasonable hotel booking amount

### Implementation Notes
- Use logical chaining with EvaluationOutcome.and() for multiple validations
- Provide specific failure reasons for each validation type
- Categorize failures as structural, business rule, or data quality failures

---

## 3. ReportDataAvailabilityCriterion

### Description
Checks if sufficient booking data is available for report generation.

### Entity
Report

### Purpose
Ensure there is adequate processed booking data available before proceeding with report generation.

### Evaluation Logic
- Returns `true` if sufficient data is available (continue report generation)
- Returns `false` if insufficient data (transition to failed state)

### Conditions Checked
1. At least one booking exists in 'processed' state
2. Booking data is not older than configured threshold (e.g., 30 days)
3. Required booking fields are populated for report calculations
4. No critical data corruption detected in booking records

### Implementation Notes
- Consider minimum data requirements for meaningful reports
- Check data freshness based on retrievedAt timestamps
- Validate data integrity before proceeding

---

## 4. ReportNoFiltersCriterion

### Description
Determines if a report request has no filtering criteria and should proceed directly to calculation.

### Entity
Report

### Purpose
Allow reports without filters to skip the filtering step and proceed directly to metric calculation.

### Evaluation Logic
- Returns `true` if no filters are specified (skip filtering step)
- Returns `false` if filters are present (proceed with filtering)

### Conditions Checked
1. `filterCriteria` field is null or empty
2. No date range filters specified
3. No price range filters specified
4. No deposit status filters specified
5. No name-based filters specified

### Implementation Notes
- Simple boolean evaluation based on filter presence
- Helps optimize workflow by skipping unnecessary filtering step
- Ensures all bookings are included when no filters are applied

---

## 5. BookingDataQualityCriterion

### Description
Evaluates the overall quality of booking data for report inclusion.

### Entity
Booking

### Purpose
Ensure only high-quality booking data is included in reports by checking for data anomalies and inconsistencies.

### Evaluation Logic
- Returns `true` if booking data meets quality standards
- Returns `false` if data quality issues are detected

### Conditions Checked

#### Data Consistency
1. Price consistency: total price matches expected range for date duration
2. Date consistency: booking dates are logical and sequential
3. Name consistency: first and last names follow expected patterns

#### Data Completeness
1. All required fields have meaningful values (not just non-null)
2. Additional needs field is relevant if provided
3. Deposit status aligns with price and booking patterns

#### Data Accuracy
1. Dates are realistic and not obviously incorrect
2. Prices are within market reasonable ranges
3. Names appear to be real person names (basic validation)

### Implementation Notes
- Use statistical analysis for price range validation
- Implement pattern matching for name validation
- Consider seasonal and market factors for price validation
- Flag suspicious data for manual review rather than automatic rejection

---

## 6. ReportGenerationReadinessCriterion

### Description
Validates that all prerequisites are met for final report generation.

### Entity
Report

### Purpose
Ensure report entity has all required data and calculations completed before generating final output.

### Evaluation Logic
- Returns `true` if report is ready for generation
- Returns `false` if prerequisites are not met

### Conditions Checked
1. All required metrics have been calculated
2. Total bookings count is consistent with processed data
3. Revenue calculations are complete and valid
4. Average price calculation is mathematically correct
5. Deposit counts sum to total bookings
6. Report metadata is complete and valid

### Implementation Notes
- Perform mathematical validation of calculated metrics
- Ensure data consistency across all report fields
- Validate report type and format requirements
- Check for any missing or corrupted calculation results

---

## Criteria Design Principles

### 1. Simplicity
- Each criterion focuses on a single evaluation concern
- Avoid complex business logic in criteria
- Use clear, boolean-based evaluation logic

### 2. Error Handling
- Provide meaningful failure reasons using EvaluationOutcome
- Categorize failures appropriately (structural, business rule, data quality)
- Log evaluation results for debugging and monitoring

### 3. Performance
- Keep evaluations lightweight and fast
- Avoid expensive operations like external API calls
- Cache validation results when appropriate

### 4. Maintainability
- Use consistent naming conventions
- Document evaluation logic clearly
- Make criteria easily testable and modifiable

### 5. Integration
- Align with workflow transition requirements
- Support both positive and negative evaluation paths
- Enable proper error recovery and retry mechanisms
