# Controller Requirements

## BookingController

### Overview
REST controller for managing booking entities and their lifecycle operations.

### Controller Name
`BookingController`

### Base Path
`/api/bookings`

### Endpoints

#### 1. Create Booking
**POST** `/api/bookings`

Creates a new booking entity and initiates the fetch process from Restful Booker API.

**Request Body:**
```json
{
  "bookingId": 1
}
```

**Response Body:**
```json
{
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "entity": {
    "bookingId": 1,
    "firstname": null,
    "lastname": null,
    "totalprice": null,
    "depositpaid": null,
    "checkin": null,
    "checkout": null,
    "additionalneeds": null
  },
  "meta": {
    "state": "INITIAL",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  }
}
```

**Transition:** `fetch_booking` (automatic transition to FETCHED state)

#### 2. Get Booking by UUID
**GET** `/api/bookings/{uuid}`

Retrieves a booking entity by its UUID.

**Response Body:**
```json
{
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "entity": {
    "bookingId": 1,
    "firstname": "Jim",
    "lastname": "Brown",
    "totalprice": 111,
    "depositpaid": true,
    "checkin": "2018-01-01",
    "checkout": "2019-01-01",
    "additionalneeds": "Breakfast"
  },
  "meta": {
    "state": "PROCESSED",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:35:00Z"
  }
}
```

#### 3. Get All Bookings
**GET** `/api/bookings`

Retrieves all booking entities with optional filtering.

**Query Parameters:**
- `state` (optional): Filter by entity state (INITIAL, FETCHED, PROCESSED, ERROR)
- `depositpaid` (optional): Filter by deposit status (true/false)
- `checkinFrom` (optional): Filter by check-in date from (YYYY-MM-DD)
- `checkinTo` (optional): Filter by check-in date to (YYYY-MM-DD)

**Example Request:**
```
GET /api/bookings?state=PROCESSED&depositpaid=true&checkinFrom=2024-01-01&checkinTo=2024-12-31
```

**Response Body:**
```json
[
  {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "entity": {
      "bookingId": 1,
      "firstname": "Jim",
      "lastname": "Brown",
      "totalprice": 111,
      "depositpaid": true,
      "checkin": "2018-01-01",
      "checkout": "2019-01-01",
      "additionalneeds": "Breakfast"
    },
    "meta": {
      "state": "PROCESSED"
    }
  }
]
```

#### 4. Update Booking with Transition
**PUT** `/api/bookings/{uuid}`

Updates a booking entity and optionally triggers a workflow transition.

**Request Body:**
```json
{
  "entity": {
    "bookingId": 1,
    "firstname": "Jim",
    "lastname": "Brown",
    "totalprice": 111,
    "depositpaid": true,
    "checkin": "2018-01-01",
    "checkout": "2019-01-01",
    "additionalneeds": "Breakfast"
  },
  "transitionName": "refresh_data"
}
```

**Response Body:**
```json
{
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "entity": {
    "bookingId": 1,
    "firstname": "Jim",
    "lastname": "Brown",
    "totalprice": 111,
    "depositpaid": true,
    "checkin": "2018-01-01",
    "checkout": "2019-01-01",
    "additionalneeds": "Breakfast"
  },
  "meta": {
    "state": "FETCHED",
    "updatedAt": "2024-01-15T11:00:00Z"
  }
}
```

**Valid Transitions:**
- From PROCESSED state: `refresh_data` (manual transition to FETCHED)
- From ERROR state: `retry_fetch` (manual transition to FETCHED)

#### 5. Delete Booking
**DELETE** `/api/bookings/{uuid}`

Deletes a booking entity.

**Response:** 204 No Content

## ReportController

### Overview
REST controller for managing report generation and retrieval.

### Controller Name
`ReportController`

### Base Path
`/api/reports`

### Endpoints

#### 1. Create Report
**POST** `/api/reports`

Creates a new report entity and initiates the generation process.

**Request Body:**
```json
{
  "reportType": "REVENUE_SUMMARY",
  "dateFrom": "2024-01-01",
  "dateTo": "2024-01-31"
}
```

**Response Body:**
```json
{
  "uuid": "456e7890-e89b-12d3-a456-426614174001",
  "entity": {
    "reportId": null,
    "reportType": "REVENUE_SUMMARY",
    "generatedDate": null,
    "dateFrom": "2024-01-01",
    "dateTo": "2024-01-31",
    "totalBookings": null,
    "totalRevenue": null,
    "averageBookingPrice": null,
    "depositPaidCount": null,
    "reportData": null
  },
  "meta": {
    "state": "INITIAL",
    "createdAt": "2024-01-15T10:30:00Z"
  }
}
```

**Transition:** `start_generation` (automatic transition to GENERATING state)

#### 2. Get Report by UUID
**GET** `/api/reports/{uuid}`

Retrieves a report entity by its UUID.

**Response Body:**
```json
{
  "uuid": "456e7890-e89b-12d3-a456-426614174001",
  "entity": {
    "reportId": "RPT-2024-001",
    "reportType": "REVENUE_SUMMARY",
    "generatedDate": "2024-01-15T10:35:00Z",
    "dateFrom": "2024-01-01",
    "dateTo": "2024-01-31",
    "totalBookings": 150,
    "totalRevenue": 15000.00,
    "averageBookingPrice": 100.00,
    "depositPaidCount": 120,
    "reportData": "{\"summary\": \"Revenue report for January 2024\"}"
  },
  "meta": {
    "state": "COMPLETED",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:35:00Z"
  }
}
```

#### 3. Get All Reports
**GET** `/api/reports`

Retrieves all report entities with optional filtering.

**Query Parameters:**
- `state` (optional): Filter by entity state (INITIAL, GENERATING, COMPLETED, FAILED)
- `reportType` (optional): Filter by report type (REVENUE_SUMMARY, BOOKING_ANALYSIS, DEPOSIT_REPORT)
- `generatedFrom` (optional): Filter by generation date from (YYYY-MM-DD)
- `generatedTo` (optional): Filter by generation date to (YYYY-MM-DD)

**Example Request:**
```
GET /api/reports?state=COMPLETED&reportType=REVENUE_SUMMARY
```

**Response Body:**
```json
[
  {
    "uuid": "456e7890-e89b-12d3-a456-426614174001",
    "entity": {
      "reportId": "RPT-2024-001",
      "reportType": "REVENUE_SUMMARY",
      "generatedDate": "2024-01-15T10:35:00Z",
      "totalBookings": 150,
      "totalRevenue": 15000.00,
      "averageBookingPrice": 100.00,
      "depositPaidCount": 120
    },
    "meta": {
      "state": "COMPLETED"
    }
  }
]
```

#### 4. Update Report with Transition
**PUT** `/api/reports/{uuid}`

Updates a report entity and optionally triggers a workflow transition.

**Request Body:**
```json
{
  "entity": {
    "reportType": "REVENUE_SUMMARY",
    "dateFrom": "2024-01-01",
    "dateTo": "2024-01-31"
  },
  "transitionName": "regenerate"
}
```

**Response Body:**
```json
{
  "uuid": "456e7890-e89b-12d3-a456-426614174001",
  "entity": {
    "reportId": "RPT-2024-001",
    "reportType": "REVENUE_SUMMARY",
    "dateFrom": "2024-01-01",
    "dateTo": "2024-01-31"
  },
  "meta": {
    "state": "GENERATING",
    "updatedAt": "2024-01-15T11:00:00Z"
  }
}
```

**Valid Transitions:**
- From COMPLETED state: `regenerate` (manual transition to GENERATING)
- From FAILED state: `retry_generation` (manual transition to GENERATING)

#### 5. Delete Report
**DELETE** `/api/reports/{uuid}`

Deletes a report entity.

**Response:** 204 No Content

## Controller Implementation Notes

### Error Handling
- All controllers should return appropriate HTTP status codes
- 400 Bad Request for invalid input data
- 404 Not Found for non-existent entities
- 500 Internal Server Error for processing failures

### Validation
- Request bodies should be validated before processing
- Required fields must be present
- Date formats should be validated (YYYY-MM-DD)
- Enum values should be validated (reportType, state filters)

### Transition Management
- Transition names must match those defined in workflows.md
- Invalid transitions should return 400 Bad Request
- Null transition names are allowed for updates without state changes
- Only manual transitions should be exposed via API endpoints

### Response Format
- All responses follow EntityWithMetadata pattern
- Include both entity data and metadata
- Consistent timestamp formats (ISO 8601)
- Proper JSON structure for all responses
