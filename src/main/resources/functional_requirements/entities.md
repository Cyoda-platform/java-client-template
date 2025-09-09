# Entity Requirements

## Booking Entity

### Overview
The Booking entity represents a hotel booking retrieved from the Restful Booker API. This entity will be used to store booking data locally and generate reports.

### Entity Name
`Booking`

### Attributes

| Attribute | Type | Description | Required | Example |
|-----------|------|-------------|----------|---------|
| bookingId | Integer | Unique identifier for the booking from external API | Yes | 1 |
| firstname | String | First name of the guest | Yes | "Jim" |
| lastname | String | Last name of the guest | Yes | "Brown" |
| totalprice | Integer | Total price of the booking | Yes | 111 |
| depositpaid | Boolean | Whether deposit has been paid | Yes | true |
| checkin | LocalDate | Check-in date | Yes | "2018-01-01" |
| checkout | LocalDate | Check-out date | Yes | "2019-01-01" |
| additionalneeds | String | Additional needs/requests | No | "Breakfast" |

### Entity State
The entity state represents the current status of the booking in the workflow:
- **INITIAL**: Booking data has been created but not yet fetched from external API
- **FETCHED**: Booking data has been successfully retrieved from Restful Booker API
- **PROCESSED**: Booking data has been processed and is ready for reporting
- **REPORTED**: Booking has been included in generated reports

**Note**: The state field is managed internally by the workflow system and is not part of the entity schema. Access it via `entity.meta.state` in processor code.

### Relationships
- **No direct relationships**: This entity is standalone as it represents external API data
- **Aggregation relationships**: Multiple bookings are aggregated for report generation

### Validation Rules
1. `bookingId` must be positive integer
2. `firstname` and `lastname` cannot be null or empty
3. `totalprice` must be non-negative
4. `checkin` date must be before or equal to `checkout` date
5. `checkin` and `checkout` dates cannot be null

### Model Key
The model key for this entity will be: `"booking"`

### Usage Notes
- This entity will be populated from the Restful Booker API (https://restful-booker.herokuapp.com)
- The `bookingId` corresponds to the booking ID from the external API
- Date fields use LocalDate format for easy manipulation and filtering
- The entity supports both individual booking operations and bulk report generation

## Report Entity

### Overview
The Report entity represents generated reports containing aggregated booking data and statistics.

### Entity Name
`Report`

### Attributes

| Attribute | Type | Description | Required | Example |
|-----------|------|-------------|----------|---------|
| reportId | String | Unique identifier for the report | Yes | "RPT-2024-001" |
| reportType | String | Type of report generated | Yes | "REVENUE_SUMMARY" |
| generatedDate | LocalDateTime | When the report was generated | Yes | "2024-01-15T10:30:00" |
| dateFrom | LocalDate | Start date for report data | No | "2024-01-01" |
| dateTo | LocalDate | End date for report data | No | "2024-01-31" |
| totalBookings | Integer | Total number of bookings in report | Yes | 150 |
| totalRevenue | BigDecimal | Total revenue from bookings | Yes | 15000.00 |
| averageBookingPrice | BigDecimal | Average price per booking | Yes | 100.00 |
| depositPaidCount | Integer | Number of bookings with deposit paid | Yes | 120 |
| reportData | String | JSON string containing detailed report data | Yes | "{...}" |

### Entity State
- **INITIAL**: Report request has been created
- **GENERATING**: Report is being generated from booking data
- **COMPLETED**: Report has been successfully generated
- **FAILED**: Report generation failed

### Relationships
- **Aggregates**: Multiple Booking entities (indirect relationship through data processing)

### Validation Rules
1. `reportId` cannot be null or empty
2. `reportType` must be one of: "REVENUE_SUMMARY", "BOOKING_ANALYSIS", "DEPOSIT_REPORT"
3. `generatedDate` cannot be null
4. If `dateFrom` and `dateTo` are provided, `dateFrom` must be before or equal to `dateTo`
5. `totalBookings`, `totalRevenue`, `averageBookingPrice`, `depositPaidCount` must be non-negative

### Model Key
The model key for this entity will be: `"report"`

### Usage Notes
- Reports are generated based on filtered booking data
- The `reportData` field contains detailed JSON data for display purposes
- Reports can be filtered by date ranges or generated for all available data
- Different report types provide different views of the same underlying booking data
