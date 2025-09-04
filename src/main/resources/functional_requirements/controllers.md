# Controllers Requirements

## 1. BookingController

### Description
REST API controller for managing booking operations including fetching bookings from Restful Booker API, retrieving stored bookings, and managing booking lifecycle.

### Base Path
`/api/bookings`

### Endpoints

#### 1.1 Fetch Booking from API
**Endpoint:** `POST /api/bookings/fetch`  
**Description:** Fetches a booking from Restful Booker API and stores it in the system  
**Transition Name:** `fetch_booking`

**Request Example:**
```json
{
  "bookingId": 123
}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "bookingId": 123,
    "firstname": "John",
    "lastname": "Doe",
    "totalprice": 150,
    "depositpaid": true,
    "checkin": "2024-01-15",
    "checkout": "2024-01-20",
    "additionalneeds": "Breakfast",
    "retrievedAt": "2024-01-01T10:00:00",
    "state": "fetched"
  },
  "message": "Booking fetched successfully"
}
```

#### 1.2 Get All Bookings
**Endpoint:** `GET /api/bookings`  
**Description:** Retrieves all bookings stored in the system  
**Transition Name:** `null` (no state change)

**Request Example:**
```
GET /api/bookings
```

**Response Example:**
```json
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "bookingId": 123,
      "firstname": "John",
      "lastname": "Doe",
      "totalprice": 150,
      "depositpaid": true,
      "checkin": "2024-01-15",
      "checkout": "2024-01-20",
      "additionalneeds": "Breakfast",
      "retrievedAt": "2024-01-01T10:00:00",
      "state": "processed"
    }
  ],
  "count": 1
}
```

#### 1.3 Get Booking by ID
**Endpoint:** `GET /api/bookings/{id}`  
**Description:** Retrieves a specific booking by its system ID  
**Transition Name:** `null` (no state change)

**Request Example:**
```
GET /api/bookings/550e8400-e29b-41d4-a716-446655440000
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "bookingId": 123,
    "firstname": "John",
    "lastname": "Doe",
    "totalprice": 150,
    "depositpaid": true,
    "checkin": "2024-01-15",
    "checkout": "2024-01-20",
    "additionalneeds": "Breakfast",
    "retrievedAt": "2024-01-01T10:00:00",
    "state": "processed"
  }
}
```

#### 1.4 Update Booking State
**Endpoint:** `PUT /api/bookings/{id}/state`  
**Description:** Updates booking state through workflow transitions  
**Transition Name:** Specified in request body

**Request Example:**
```json
{
  "transition": "mark_processed"
}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "bookingId": 123,
    "firstname": "John",
    "lastname": "Doe",
    "totalprice": 150,
    "depositpaid": true,
    "checkin": "2024-01-15",
    "checkout": "2024-01-20",
    "additionalneeds": "Breakfast",
    "retrievedAt": "2024-01-01T10:00:00",
    "state": "processed"
  },
  "message": "Booking state updated successfully"
}
```

#### 1.5 Retry Failed Booking
**Endpoint:** `POST /api/bookings/{id}/retry`  
**Description:** Retries fetching a failed booking  
**Transition Name:** `retry_fetch`

**Request Example:**
```json
{}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "bookingId": 123,
    "state": "fetching"
  },
  "message": "Booking retry initiated"
}
```

---

## 2. ReportController

### Description
REST API controller for managing report generation, including creating reports, applying filters, and retrieving generated reports.

### Base Path
`/api/reports`

### Endpoints

#### 2.1 Create Report
**Endpoint:** `POST /api/reports`  
**Description:** Creates a new report request  
**Transition Name:** `start_collection`

**Request Example:**
```json
{
  "reportName": "Monthly Booking Summary",
  "reportType": "SUMMARY",
  "dateRange": "2024-01-01 to 2024-01-31",
  "filterCriteria": {
    "dateFrom": "2024-01-01",
    "dateTo": "2024-01-31",
    "depositpaid": true,
    "minPrice": 100
  }
}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "reportId": "RPT-2024-001",
    "reportName": "Monthly Booking Summary",
    "reportType": "SUMMARY",
    "dateRange": "2024-01-01 to 2024-01-31",
    "filterCriteria": "{\"dateFrom\":\"2024-01-01\",\"dateTo\":\"2024-01-31\",\"depositpaid\":true,\"minPrice\":100}",
    "state": "collecting"
  },
  "message": "Report creation initiated"
}
```

#### 2.2 Get All Reports
**Endpoint:** `GET /api/reports`  
**Description:** Retrieves all reports in the system  
**Transition Name:** `null` (no state change)

**Request Example:**
```
GET /api/reports
```

**Response Example:**
```json
{
  "success": true,
  "data": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "reportId": "RPT-2024-001",
      "reportName": "Monthly Booking Summary",
      "reportType": "SUMMARY",
      "totalBookings": 25,
      "totalRevenue": 3750.00,
      "averagePrice": 150.00,
      "depositPaidCount": 20,
      "depositUnpaidCount": 5,
      "generatedAt": "2024-01-01T15:30:00",
      "state": "completed"
    }
  ],
  "count": 1
}
```

#### 2.3 Get Report by ID
**Endpoint:** `GET /api/reports/{id}`  
**Description:** Retrieves a specific report by its system ID  
**Transition Name:** `null` (no state change)

**Request Example:**
```
GET /api/reports/660e8400-e29b-41d4-a716-446655440001
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "reportId": "RPT-2024-001",
    "reportName": "Monthly Booking Summary",
    "reportType": "SUMMARY",
    "dateRange": "2024-01-01 to 2024-01-31",
    "filterCriteria": "{\"dateFrom\":\"2024-01-01\",\"dateTo\":\"2024-01-31\",\"depositpaid\":true,\"minPrice\":100}",
    "totalBookings": 25,
    "totalRevenue": 3750.00,
    "averagePrice": 150.00,
    "depositPaidCount": 20,
    "depositUnpaidCount": 5,
    "generatedAt": "2024-01-01T15:30:00",
    "generatedBy": "system",
    "state": "completed"
  }
}
```

#### 2.4 Generate Filtered Report
**Endpoint:** `POST /api/reports/filtered`  
**Description:** Creates a report with specific filtering criteria  
**Transition Name:** `start_collection`

**Request Example:**
```json
{
  "reportName": "High Value Bookings",
  "reportType": "FILTERED",
  "filterCriteria": {
    "minPrice": 200,
    "depositpaid": true,
    "dateFrom": "2024-01-01",
    "dateTo": "2024-12-31"
  }
}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "id": "660e8400-e29b-41d4-a716-446655440002",
    "reportId": "RPT-2024-002",
    "reportName": "High Value Bookings",
    "reportType": "FILTERED",
    "filterCriteria": "{\"minPrice\":200,\"depositpaid\":true,\"dateFrom\":\"2024-01-01\",\"dateTo\":\"2024-12-31\"}",
    "state": "collecting"
  },
  "message": "Filtered report creation initiated"
}
```

#### 2.5 Retry Failed Report
**Endpoint:** `POST /api/reports/{id}/retry`  
**Description:** Retries generation of a failed report  
**Transition Name:** `retry_generation`

**Request Example:**
```json
{}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "reportId": "RPT-2024-001",
    "state": "collecting"
  },
  "message": "Report retry initiated"
}
```

#### 2.6 Get Report Summary
**Endpoint:** `GET /api/reports/summary`  
**Description:** Gets a summary of all reports and their statuses  
**Transition Name:** `null` (no state change)

**Request Example:**
```
GET /api/reports/summary
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "totalReports": 10,
    "completedReports": 8,
    "failedReports": 1,
    "inProgressReports": 1,
    "totalBookingsProcessed": 250,
    "totalRevenueReported": 37500.00
  }
}
```

---

## Controller Design Notes

### 1. Response Format
- All endpoints use consistent response format with `success`, `data`, and optional `message` fields
- Error responses include appropriate HTTP status codes and error details
- Success responses include relevant entity data and metadata

### 2. State Management
- Update endpoints specify transition names to trigger workflow state changes
- Read-only endpoints use `null` transition names to avoid state changes
- State transitions are validated against current entity state

### 3. Error Handling
- Controllers handle workflow exceptions and return appropriate HTTP status codes
- Validation errors return 400 Bad Request with detailed error messages
- Not found errors return 404 with clear error messages
- Server errors return 500 with generic error messages (detailed errors logged)

### 4. Request Validation
- All request bodies are validated for required fields and data types
- Date formats are validated and parsed consistently
- Filter criteria are validated before processing

### 5. Security Considerations
- Input validation prevents injection attacks
- Sensitive data is not exposed in error messages
- Rate limiting should be implemented for resource-intensive operations
