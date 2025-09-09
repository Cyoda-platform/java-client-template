# Criteria Requirements

## BookingValidCriterion

### Overview
Evaluates whether a booking entity has valid data after being fetched from the external API.

### Criterion Name
`BookingValidCriterion`

### Entity
`Booking`

### Purpose
Determines if the fetched booking data is valid and complete for processing.

### Evaluation Logic
Checks that all required fields are present and valid according to business rules.

### Expected Input
- Booking entity with data populated from Restful Booker API
- Entity in FETCHED state

### Return Value
- `true` if booking data is valid and can proceed to PROCESSED state
- `false` if booking data is invalid and should go to ERROR state

### Validation Rules
1. First name and last name must not be null or empty
2. Total price must be non-negative
3. Check-in and check-out dates must be present
4. Check-in date must be before or equal to check-out date
5. Booking ID must be positive

### Pseudocode
```
CHECK BookingValidCriterion:
  INPUT: booking entity with fetched data
  
  BEGIN
    // Check required string fields
    IF entity.getFirstname() IS NULL OR TRIM(entity.getFirstname()) IS EMPTY THEN
      RETURN false
    END IF
    
    IF entity.getLastname() IS NULL OR TRIM(entity.getLastname()) IS EMPTY THEN
      RETURN false
    END IF
    
    // Check numeric fields
    IF entity.getTotalprice() IS NULL OR entity.getTotalprice() < 0 THEN
      RETURN false
    END IF
    
    IF entity.getBookingId() IS NULL OR entity.getBookingId() <= 0 THEN
      RETURN false
    END IF
    
    // Check date fields
    IF entity.getCheckin() IS NULL OR entity.getCheckout() IS NULL THEN
      RETURN false
    END IF
    
    IF entity.getCheckin().isAfter(entity.getCheckout()) THEN
      RETURN false
    END IF
    
    // All validations passed
    RETURN true
  END
```

## BookingInvalidCriterion

### Overview
Evaluates whether a booking entity has invalid data that requires error handling.

### Criterion Name
`BookingInvalidCriterion`

### Entity
`Booking`

### Purpose
Determines if the fetched booking data is invalid and should be moved to ERROR state.

### Evaluation Logic
Inverse of BookingValidCriterion - returns true when data is invalid.

### Expected Input
- Booking entity with data populated from Restful Booker API
- Entity in FETCHED state

### Return Value
- `true` if booking data is invalid and should go to ERROR state
- `false` if booking data is valid

### Validation Rules
Returns true if any of the following conditions are met:
1. First name or last name is null or empty
2. Total price is negative or null
3. Check-in or check-out dates are missing
4. Check-in date is after check-out date
5. Booking ID is null or non-positive

### Pseudocode
```
CHECK BookingInvalidCriterion:
  INPUT: booking entity with fetched data
  
  BEGIN
    // This is the inverse of BookingValidCriterion
    validCriterion = NEW BookingValidCriterion()
    isValid = validCriterion.check(entity)
    
    RETURN NOT isValid
  END
```

## ReportValidCriterion

### Overview
Evaluates whether a generated report contains valid data and statistics.

### Criterion Name
`ReportValidCriterion`

### Entity
`Report`

### Purpose
Determines if the generated report data is valid and complete for finalization.

### Evaluation Logic
Checks that all calculated statistics are reasonable and report data is properly formatted.

### Expected Input
- Report entity with generated statistics
- Entity in GENERATING state

### Return Value
- `true` if report data is valid and can proceed to COMPLETED state
- `false` if report data is invalid

### Validation Rules
1. Total bookings count must be non-negative
2. Total revenue must be non-negative
3. Average booking price must be non-negative
4. Deposit paid count must not exceed total bookings
5. Report data must be valid JSON
6. Generated date must be present

### Pseudocode
```
CHECK ReportValidCriterion:
  INPUT: report entity with generated data
  
  BEGIN
    // Check numeric statistics
    IF entity.getTotalBookings() IS NULL OR entity.getTotalBookings() < 0 THEN
      RETURN false
    END IF
    
    IF entity.getTotalRevenue() IS NULL OR entity.getTotalRevenue() < 0 THEN
      RETURN false
    END IF
    
    IF entity.getAverageBookingPrice() IS NULL OR entity.getAverageBookingPrice() < 0 THEN
      RETURN false
    END IF
    
    IF entity.getDepositPaidCount() IS NULL OR entity.getDepositPaidCount() < 0 THEN
      RETURN false
    END IF
    
    // Check logical consistency
    IF entity.getDepositPaidCount() > entity.getTotalBookings() THEN
      RETURN false
    END IF
    
    // Check required fields
    IF entity.getGeneratedDate() IS NULL THEN
      RETURN false
    END IF
    
    IF entity.getReportData() IS NULL OR TRIM(entity.getReportData()) IS EMPTY THEN
      RETURN false
    END IF
    
    // Validate JSON format
    TRY
      PARSE_JSON(entity.getReportData())
    CATCH JsonException
      RETURN false
    END TRY
    
    // All validations passed
    RETURN true
  END
```

## ReportInvalidCriterion

### Overview
Evaluates whether a generated report contains invalid data that requires error handling.

### Criterion Name
`ReportInvalidCriterion`

### Entity
`Report`

### Purpose
Determines if the generated report data is invalid and should be moved to FAILED state.

### Evaluation Logic
Inverse of ReportValidCriterion - returns true when report data is invalid.

### Expected Input
- Report entity with generated statistics
- Entity in GENERATING state

### Return Value
- `true` if report data is invalid and should go to FAILED state
- `false` if report data is valid

### Validation Rules
Returns true if any of the ReportValidCriterion validation rules fail.

### Pseudocode
```
CHECK ReportInvalidCriterion:
  INPUT: report entity with generated data
  
  BEGIN
    // This is the inverse of ReportValidCriterion
    validCriterion = NEW ReportValidCriterion()
    isValid = validCriterion.check(entity)
    
    RETURN NOT isValid
  END
```

## Criteria Usage Notes

### Pure Functions
All criteria are pure functions that:
- Do not modify entity state
- Do not have side effects
- Only evaluate conditions based on input data
- Return consistent results for the same input

### Error Handling
- Criteria should handle null values gracefully
- Invalid data should return false (for valid criteria) or true (for invalid criteria)
- No exceptions should be thrown from criteria evaluation

### Performance Considerations
- Criteria should be lightweight and fast
- Avoid complex calculations or external API calls
- Use simple field validation and logical checks

### Workflow Integration
- Criteria determine which transition path to take in the workflow
- Multiple criteria can be used in the same workflow state
- Criteria results directly influence workflow state transitions
