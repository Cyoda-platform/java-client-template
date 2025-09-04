# Entities Requirements

## 1. Booking Entity

### Description
The Booking entity represents a hotel booking retrieved from the Restful Booker API. This entity stores all booking information and tracks the processing state through the workflow.

### Attributes

| Attribute | Type | Description | Required | Example |
|-----------|------|-------------|----------|---------|
| bookingId | Integer | Unique identifier from Restful Booker API | Yes | 123 |
| firstname | String | Guest's first name | Yes | "John" |
| lastname | String | Guest's last name | Yes | "Doe" |
| totalprice | Integer | Total price of the booking | Yes | 150 |
| depositpaid | Boolean | Whether deposit has been paid | Yes | true |
| checkin | LocalDate | Check-in date | Yes | "2024-01-15" |
| checkout | LocalDate | Check-out date | Yes | "2024-01-20" |
| additionalneeds | String | Additional requirements | No | "Breakfast" |
| retrievedAt | LocalDateTime | When booking was fetched from API | Yes | "2024-01-01T10:00:00" |

### Entity State
The entity state is managed automatically by the workflow system and represents the current processing state of the booking:
- `initial` - Newly created booking record
- `fetched` - Successfully retrieved from Restful Booker API
- `validated` - Booking data has been validated
- `processed` - Ready for report generation
- `error` - Error occurred during processing

**Note**: The entity state is accessed via `entity.meta.state` and cannot be modified directly. The workflow system manages state transitions automatically.

### Relationships
- **One-to-Many** with Report entity: A booking can be included in multiple reports
- **No direct foreign key relationships** - relationships are managed through report generation logic

---

## 2. Report Entity

### Description
The Report entity represents generated reports containing booking analytics and summaries. Reports can contain filtered booking data and calculated metrics.

### Attributes

| Attribute | Type | Description | Required | Example |
|-----------|------|-------------|----------|---------|
| reportId | String | Unique report identifier | Yes | "RPT-2024-001" |
| reportName | String | Human-readable report name | Yes | "Monthly Booking Summary" |
| reportType | String | Type of report generated | Yes | "SUMMARY", "DETAILED", "FILTERED" |
| dateRange | String | Date range for the report | No | "2024-01-01 to 2024-01-31" |
| filterCriteria | String | JSON string of applied filters | No | "{\"depositpaid\":true,\"minPrice\":100}" |
| totalBookings | Integer | Number of bookings in report | Yes | 25 |
| totalRevenue | BigDecimal | Sum of all booking prices | Yes | 3750.00 |
| averagePrice | BigDecimal | Average booking price | Yes | 150.00 |
| depositPaidCount | Integer | Number of bookings with deposit paid | Yes | 20 |
| depositUnpaidCount | Integer | Number of bookings without deposit | Yes | 5 |
| generatedAt | LocalDateTime | When report was generated | Yes | "2024-01-01T15:30:00" |
| generatedBy | String | User or system that generated report | No | "system" |

### Entity State
The entity state represents the current status of the report:
- `initial` - Report request created
- `generating` - Report generation in progress
- `completed` - Report successfully generated
- `failed` - Report generation failed

### Relationships
- **Many-to-Many** with Booking entity: A report can include multiple bookings, and a booking can appear in multiple reports
- **No direct foreign key relationships** - relationships are managed through report generation logic and stored as metadata

---

## 3. BookingDates (Embedded Object)

### Description
BookingDates is an embedded object within the Booking entity that represents the check-in and check-out dates.

### Attributes

| Attribute | Type | Description | Required | Example |
|-----------|------|-------------|----------|---------|
| checkin | LocalDate | Check-in date | Yes | "2024-01-15" |
| checkout | LocalDate | Check-out date | Yes | "2024-01-20" |

### Validation Rules
- Check-in date must be before check-out date
- Both dates must be valid dates
- Dates cannot be null

---

## Entity Design Notes

1. **State Management**: Both entities use the workflow state system. The `state` field is not included in the entity schema as it's managed by `entity.meta.state`.

2. **API Integration**: The Booking entity closely mirrors the Restful Booker API structure to ensure seamless data mapping.

3. **Report Flexibility**: The Report entity is designed to support various report types and filtering criteria through flexible JSON storage.

4. **Data Types**: 
   - Use `LocalDate` for dates to ensure proper date handling
   - Use `BigDecimal` for monetary calculations to avoid floating-point precision issues
   - Use `LocalDateTime` for timestamps with timezone considerations

5. **Validation**: Entity validation will be handled through workflow criteria rather than entity-level constraints.
