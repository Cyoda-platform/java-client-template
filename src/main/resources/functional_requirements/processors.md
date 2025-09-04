# Processors Requirements

## 1. BookingFetchProcessor

### Description
Retrieves booking data from the Restful Booker API and populates the Booking entity with the fetched information.

### Entity
Booking

### Expected Input Data
- Booking entity with bookingId set
- Optional: API authentication credentials

### Expected Entity Output
- Booking entity populated with all fields from API response
- Entity state transitions to 'fetched' or 'error'
- retrievedAt timestamp set to current time

### Transition Name for Other Entities
- None (null transition)

### Pseudocode
```
PROCESS BookingFetchProcessor:
    INPUT: booking entity with bookingId
    
    BEGIN
        SET apiUrl = "https://restful-booker.herokuapp.com/booking/" + booking.bookingId
        
        TRY
            CALL HTTP GET request to apiUrl
            IF response.status == 200 THEN
                SET responseData = parse JSON response
                
                SET booking.firstname = responseData.firstname
                SET booking.lastname = responseData.lastname  
                SET booking.totalprice = responseData.totalprice
                SET booking.depositpaid = responseData.depositpaid
                SET booking.checkin = parse date from responseData.bookingdates.checkin
                SET booking.checkout = parse date from responseData.bookingdates.checkout
                SET booking.additionalneeds = responseData.additionalneeds
                SET booking.retrievedAt = current timestamp
                
                LOG "Successfully fetched booking " + booking.bookingId
                RETURN booking
            ELSE
                LOG ERROR "Failed to fetch booking " + booking.bookingId + ": " + response.status
                THROW BookingFetchException
            END IF
        CATCH exception
            LOG ERROR "Exception fetching booking " + booking.bookingId + ": " + exception.message
            THROW BookingFetchException
        END TRY
    END
```

---

## 2. BookingValidationProcessor

### Description
Validates booking data for completeness and business rules compliance.

### Entity
Booking

### Expected Input Data
- Booking entity with all fields populated from API

### Expected Entity Output
- Validated booking entity
- Entity state transitions to 'validated' or 'error'

### Transition Name for Other Entities
- None (null transition)

### Pseudocode
```
PROCESS BookingValidationProcessor:
    INPUT: booking entity with populated data
    
    BEGIN
        SET validationErrors = empty list
        
        // Validate required fields
        IF booking.firstname is null or empty THEN
            ADD "First name is required" to validationErrors
        END IF
        
        IF booking.lastname is null or empty THEN
            ADD "Last name is required" to validationErrors
        END IF
        
        IF booking.totalprice is null or less than 0 THEN
            ADD "Total price must be positive" to validationErrors
        END IF
        
        // Validate dates
        IF booking.checkin is null THEN
            ADD "Check-in date is required" to validationErrors
        END IF
        
        IF booking.checkout is null THEN
            ADD "Check-out date is required" to validationErrors
        END IF
        
        IF booking.checkin is not null AND booking.checkout is not null THEN
            IF booking.checkin >= booking.checkout THEN
                ADD "Check-in date must be before check-out date" to validationErrors
            END IF
        END IF
        
        // Business rule validations
        IF booking.totalprice > 10000 THEN
            LOG WARNING "High value booking detected: " + booking.bookingId
        END IF
        
        IF validationErrors is not empty THEN
            LOG ERROR "Validation failed for booking " + booking.bookingId + ": " + validationErrors
            THROW BookingValidationException with validationErrors
        ELSE
            LOG "Booking validation passed for " + booking.bookingId
            RETURN booking
        END IF
    END
```

---

## 3. ReportDataCollectionProcessor

### Description
Collects all processed booking data for report generation.

### Entity
Report

### Expected Input Data
- Report entity with reportType and optional filterCriteria

### Expected Entity Output
- Report entity with booking data collected
- Entity state transitions to 'collecting'

### Transition Name for Other Entities
- None (null transition)

### Pseudocode
```
PROCESS ReportDataCollectionProcessor:
    INPUT: report entity with report parameters
    
    BEGIN
        SET bookingList = empty list
        
        TRY
            // Fetch all processed bookings
            SET allBookings = entityService.findAll(Booking.class)
            
            FOR each booking in allBookings
                IF booking.meta.state == "processed" THEN
                    ADD booking to bookingList
                END IF
            END FOR
            
            SET report.totalBookings = size of bookingList
            LOG "Collected " + report.totalBookings + " bookings for report " + report.reportId
            
            // Store booking data reference for next processors
            SET report metadata with bookingList reference
            
            RETURN report
            
        CATCH exception
            LOG ERROR "Failed to collect booking data for report " + report.reportId + ": " + exception.message
            THROW ReportDataCollectionException
        END TRY
    END
```

---

## 4. ReportFilterProcessor

### Description
Applies filtering criteria to collected booking data based on report requirements.

### Entity
Report

### Expected Input Data
- Report entity with collected booking data and filterCriteria

### Expected Entity Output
- Report entity with filtered booking data
- Updated totalBookings count

### Transition Name for Other Entities
- None (null transition)

### Pseudocode
```
PROCESS ReportFilterProcessor:
    INPUT: report entity with booking data and filter criteria
    
    BEGIN
        SET bookingList = get booking data from report metadata
        SET filteredBookings = empty list
        SET filters = parse JSON from report.filterCriteria
        
        FOR each booking in bookingList
            SET includeBooking = true
            
            // Apply date range filter
            IF filters contains "dateFrom" THEN
                IF booking.checkin < filters.dateFrom THEN
                    SET includeBooking = false
                END IF
            END IF
            
            IF filters contains "dateTo" THEN
                IF booking.checkout > filters.dateTo THEN
                    SET includeBooking = false
                END IF
            END IF
            
            // Apply price filters
            IF filters contains "minPrice" THEN
                IF booking.totalprice < filters.minPrice THEN
                    SET includeBooking = false
                END IF
            END IF
            
            IF filters contains "maxPrice" THEN
                IF booking.totalprice > filters.maxPrice THEN
                    SET includeBooking = false
                END IF
            END IF
            
            // Apply deposit filter
            IF filters contains "depositpaid" THEN
                IF booking.depositpaid != filters.depositpaid THEN
                    SET includeBooking = false
                END IF
            END IF
            
            // Apply name filters
            IF filters contains "firstname" THEN
                IF booking.firstname does not contain filters.firstname THEN
                    SET includeBooking = false
                END IF
            END IF
            
            IF includeBooking THEN
                ADD booking to filteredBookings
            END IF
        END FOR
        
        SET report.totalBookings = size of filteredBookings
        UPDATE report metadata with filteredBookings
        
        LOG "Filtered to " + report.totalBookings + " bookings for report " + report.reportId
        RETURN report
    END
```

---

## 5. ReportCalculationProcessor

### Description
Calculates metrics and summaries for the report based on filtered booking data.

### Entity
Report

### Expected Input Data
- Report entity with filtered booking data

### Expected Entity Output
- Report entity with calculated metrics
- All summary fields populated

### Transition Name for Other Entities
- None (null transition)

### Pseudocode
```
PROCESS ReportCalculationProcessor:
    INPUT: report entity with filtered booking data
    
    BEGIN
        SET bookingList = get filtered booking data from report metadata
        SET totalRevenue = 0
        SET depositPaidCount = 0
        SET depositUnpaidCount = 0
        
        FOR each booking in bookingList
            SET totalRevenue = totalRevenue + booking.totalprice
            
            IF booking.depositpaid == true THEN
                SET depositPaidCount = depositPaidCount + 1
            ELSE
                SET depositUnpaidCount = depositUnpaidCount + 1
            END IF
        END FOR
        
        // Calculate metrics
        SET report.totalRevenue = totalRevenue
        SET report.depositPaidCount = depositPaidCount
        SET report.depositUnpaidCount = depositUnpaidCount
        
        IF report.totalBookings > 0 THEN
            SET report.averagePrice = totalRevenue / report.totalBookings
        ELSE
            SET report.averagePrice = 0
        END IF
        
        // Set generation timestamp
        SET report.generatedAt = current timestamp
        SET report.generatedBy = "system"
        
        LOG "Calculated metrics for report " + report.reportId + 
            ": Revenue=" + report.totalRevenue + 
            ", Average=" + report.averagePrice +
            ", Bookings=" + report.totalBookings
            
        RETURN report
    END
```

---

## 6. ReportGenerationProcessor

### Description
Generates the final report output in the requested format.

### Entity
Report

### Expected Input Data
- Report entity with all calculated metrics

### Expected Entity Output
- Report entity with generated output
- Entity state transitions to 'completed'

### Transition Name for Other Entities
- None (null transition)

### Pseudocode
```
PROCESS ReportGenerationProcessor:
    INPUT: report entity with calculated metrics
    
    BEGIN
        TRY
            // Generate report content based on type
            IF report.reportType == "SUMMARY" THEN
                SET reportContent = generate summary report format
            ELSE IF report.reportType == "DETAILED" THEN
                SET reportContent = generate detailed report format
            ELSE IF report.reportType == "FILTERED" THEN
                SET reportContent = generate filtered report format
            ELSE
                SET reportContent = generate default report format
            END IF
            
            // Store report content in metadata or file system
            STORE reportContent for report.reportId
            
            LOG "Successfully generated " + report.reportType + " report " + report.reportId
            RETURN report
            
        CATCH exception
            LOG ERROR "Failed to generate report " + report.reportId + ": " + exception.message
            THROW ReportGenerationException
        END TRY
    END
```
