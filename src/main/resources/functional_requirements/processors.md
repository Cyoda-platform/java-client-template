# Processor Requirements

## BookingFetchProcessor

### Overview
Fetches booking data from the Restful Booker API and populates the Booking entity with external data.

### Processor Name
`BookingFetchProcessor`

### Entity
`Booking`

### Expected Input Data
- Booking entity with `bookingId` field populated
- Entity in INITIAL or ERROR state

### Business Logic
Retrieves booking details from the external Restful Booker API and updates the entity with the fetched data.

### Expected Entity Output
- Updated Booking entity with all fields populated from API response
- Entity state transitions to FETCHED (managed by workflow)

### Other Entity Operations
None - this processor only updates the current booking entity with external data.

### Pseudocode
```
PROCESS BookingFetchProcessor:
  INPUT: booking entity with bookingId
  
  BEGIN
    // Get booking ID from current entity
    bookingId = entity.getBookingId()
    
    // Call Restful Booker API
    apiUrl = "https://restful-booker.herokuapp.com/booking/" + bookingId
    apiResponse = HTTP_GET(apiUrl)
    
    IF apiResponse.status == 200 THEN
      // Parse JSON response
      bookingData = PARSE_JSON(apiResponse.body)
      
      // Update entity fields
      entity.setFirstname(bookingData.firstname)
      entity.setLastname(bookingData.lastname)
      entity.setTotalprice(bookingData.totalprice)
      entity.setDepositpaid(bookingData.depositpaid)
      entity.setCheckin(PARSE_DATE(bookingData.bookingdates.checkin))
      entity.setCheckout(PARSE_DATE(bookingData.bookingdates.checkout))
      entity.setAdditionalneeds(bookingData.additionalneeds)
      
      LOG("Successfully fetched booking data for ID: " + bookingId)
      RETURN entity
    ELSE
      LOG_ERROR("Failed to fetch booking data for ID: " + bookingId)
      THROW ApiException("Failed to fetch booking data")
    END IF
  END
```

## BookingValidationProcessor

### Overview
Validates the fetched booking data and ensures data integrity before marking it as processed.

### Processor Name
`BookingValidationProcessor`

### Entity
`Booking`

### Expected Input Data
- Booking entity with all fields populated from API
- Entity in FETCHED state

### Business Logic
Validates booking data fields and performs data quality checks.

### Expected Entity Output
- Validated Booking entity ready for reporting
- Entity state transitions to PROCESSED (managed by workflow)

### Other Entity Operations
None - this processor only validates the current booking entity.

### Pseudocode
```
PROCESS BookingValidationProcessor:
  INPUT: booking entity with populated data
  
  BEGIN
    // Validate required fields
    IF entity.getFirstname() IS NULL OR EMPTY THEN
      LOG_ERROR("Firstname is required")
      THROW ValidationException("Invalid firstname")
    END IF
    
    IF entity.getLastname() IS NULL OR EMPTY THEN
      LOG_ERROR("Lastname is required")
      THROW ValidationException("Invalid lastname")
    END IF
    
    IF entity.getTotalprice() < 0 THEN
      LOG_ERROR("Total price cannot be negative")
      THROW ValidationException("Invalid total price")
    END IF
    
    // Validate date logic
    IF entity.getCheckin() IS NULL OR entity.getCheckout() IS NULL THEN
      LOG_ERROR("Check-in and check-out dates are required")
      THROW ValidationException("Invalid dates")
    END IF
    
    IF entity.getCheckin().isAfter(entity.getCheckout()) THEN
      LOG_ERROR("Check-in date must be before check-out date")
      THROW ValidationException("Invalid date range")
    END IF
    
    LOG("Booking validation successful for ID: " + entity.getBookingId())
    RETURN entity
  END
```

## ReportGenerationProcessor

### Overview
Generates reports by aggregating data from processed booking entities.

### Processor Name
`ReportGenerationProcessor`

### Entity
`Report`

### Expected Input Data
- Report entity with reportType and date filters
- Entity in INITIAL or FAILED state

### Business Logic
Queries processed booking data and generates aggregated reports with statistics.

### Expected Entity Output
- Report entity with calculated statistics and report data
- Entity state transitions to GENERATING (managed by workflow)

### Other Entity Operations
- **GET** multiple Booking entities with state = PROCESSED
- **Filter** bookings based on date range if specified
- **No transition needed** for booking entities (read-only operation)

### Pseudocode
```
PROCESS ReportGenerationProcessor:
  INPUT: report entity with report parameters
  
  BEGIN
    // Get report parameters
    reportType = entity.getReportType()
    dateFrom = entity.getDateFrom()
    dateTo = entity.getDateTo()
    
    // Query processed bookings
    bookingFilter = CREATE_FILTER()
    bookingFilter.setState("PROCESSED")
    
    IF dateFrom IS NOT NULL THEN
      bookingFilter.setCheckinFrom(dateFrom)
    END IF
    
    IF dateTo IS NOT NULL THEN
      bookingFilter.setCheckoutTo(dateTo)
    END IF
    
    bookings = entityService.findBookings(bookingFilter)
    
    // Calculate statistics
    totalBookings = bookings.size()
    totalRevenue = SUM(bookings.totalprice)
    averagePrice = totalRevenue / totalBookings
    depositPaidCount = COUNT(bookings WHERE depositpaid = true)
    
    // Generate report data based on type
    reportData = GENERATE_REPORT_DATA(reportType, bookings)
    
    // Update report entity
    entity.setTotalBookings(totalBookings)
    entity.setTotalRevenue(totalRevenue)
    entity.setAverageBookingPrice(averagePrice)
    entity.setDepositPaidCount(depositPaidCount)
    entity.setReportData(JSON_STRINGIFY(reportData))
    entity.setGeneratedDate(CURRENT_TIMESTAMP())
    
    LOG("Report generation completed for type: " + reportType)
    RETURN entity
  END
```

## ReportFinalizationProcessor

### Overview
Finalizes the generated report and prepares it for display.

### Processor Name
`ReportFinalizationProcessor`

### Entity
`Report`

### Expected Input Data
- Report entity with generated statistics
- Entity in GENERATING state

### Business Logic
Performs final validation and formatting of the generated report.

### Expected Entity Output
- Finalized Report entity ready for display
- Entity state transitions to COMPLETED (managed by workflow)

### Other Entity Operations
None - this processor only finalizes the current report entity.

### Pseudocode
```
PROCESS ReportFinalizationProcessor:
  INPUT: report entity with generated data
  
  BEGIN
    // Validate generated data
    IF entity.getTotalBookings() < 0 THEN
      LOG_ERROR("Invalid total bookings count")
      THROW ValidationException("Invalid report data")
    END IF
    
    IF entity.getTotalRevenue() IS NULL THEN
      LOG_ERROR("Total revenue not calculated")
      THROW ValidationException("Missing revenue data")
    END IF
    
    // Format report data for display
    reportData = PARSE_JSON(entity.getReportData())
    formattedData = FORMAT_FOR_DISPLAY(reportData)
    entity.setReportData(JSON_STRINGIFY(formattedData))
    
    // Generate report ID if not set
    IF entity.getReportId() IS NULL OR EMPTY THEN
      reportId = "RPT-" + CURRENT_DATE() + "-" + RANDOM_NUMBER()
      entity.setReportId(reportId)
    END IF
    
    LOG("Report finalization completed for ID: " + entity.getReportId())
    RETURN entity
  END
```
