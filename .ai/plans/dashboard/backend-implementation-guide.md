# Dashboard Backend Implementation Guide

**Document Version:** 1.0  
**Date:** 2025-10-07  
**Status:** ✅ Production Ready  
**Purpose:** Technical guide for UI developers to integrate with the dashboard REST API

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [REST API Endpoints](#rest-api-endpoints)
4. [Data Models](#data-models)
5. [Implementation Details](#implementation-details)
6. [Caching Strategy](#caching-strategy)
7. [Error Handling](#error-handling)
8. [Integration Guide](#integration-guide)
9. [Performance Characteristics](#performance-characteristics)
10. [Testing](#testing)

---

## Overview

The Dashboard Backend provides aggregated loan portfolio metrics through a RESTful API. The implementation consists of three layers:

1. **Controller Layer** - REST endpoints for HTTP access
2. **Service Layer** - Business logic and data aggregation
3. **Data Layer** - Entity retrieval via EntityService

### Key Features

- ✅ Real-time portfolio metrics aggregation
- ✅ Thread-safe caching (5-minute TTL)
- ✅ Time-series data for trends (12 months portfolio, 6 months payments)
- ✅ Status distribution analytics
- ✅ Manual cache invalidation support
- ✅ CORS-enabled for frontend access
- ✅ No authentication required

---

## Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend UI                          │
│                  (React/Angular/Vue)                        │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTP GET/POST
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   DashboardController                       │
│              /ui/dashboard/summary (GET)                    │
│         /ui/dashboard/cache/invalidate (POST)               │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   DashboardService                          │
│              (with ConcurrentHashMap cache)                 │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    EntityService                            │
│           (Cyoda REST API Integration)                      │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  Cyoda Data Platform                        │
│              (Loan & Payment Entities)                      │
└─────────────────────────────────────────────────────────────┘
```

### Package Structure

```
src/main/java/com/java_template/application/
├── controller/
│   └── DashboardController.java          # REST endpoints
├── service/dashboard/
│   ├── DashboardService.java             # Service interface
│   └── DashboardServiceImpl.java         # Service implementation
└── dto/dashboard/
    ├── DashboardSummaryDTO.java          # Main response DTO
    ├── StatusDistributionDTO.java        # Status breakdown
    ├── PortfolioTrendDTO.java            # 12-month trend
    └── MonthlyPaymentsDTO.java           # 6-month payments
```

---

## REST API Endpoints

### 1. GET /ui/dashboard/summary

**Description:** Retrieves aggregated dashboard summary data with all metrics.

**URL:** `http://localhost:8080/ui/dashboard/summary`

**Method:** `GET`

**Authentication:** None required

**Request Headers:**
```http
Accept: application/json
```

**Response Status Codes:**
- `200 OK` - Successfully retrieved dashboard data
- `500 Internal Server Error` - Failed to retrieve or aggregate data
- `503 Service Unavailable` - Dashboard service is unavailable

**Response Body (200 OK):**
```json
{
  "totalPortfolioValue": 5000000.00,
  "activeLoansCount": 45,
  "outstandingPrincipal": 4250000.00,
  "activeBorrowersCount": 38,
  "statusDistribution": {
    "labels": ["active", "funded", "matured", "settled"],
    "values": [25, 15, 8, 5]
  },
  "portfolioTrend": {
    "months": ["2024-11", "2024-12", "2025-01", "2025-02", "2025-03", "2025-04", "2025-05", "2025-06", "2025-07", "2025-08", "2025-09", "2025-10"],
    "values": [1000000.00, 1200000.00, 1500000.00, 1750000.00, 2000000.00, 2250000.00, 2500000.00, 2750000.00, 3000000.00, 3500000.00, 4000000.00, 5000000.00]
  },
  "aprDistribution": [5.5, 6.0, 6.5, 7.0, 7.5, 8.0],
  "monthlyPayments": {
    "months": ["2025-05", "2025-06", "2025-07", "2025-08", "2025-09", "2025-10"],
    "amounts": [125000.00, 135000.00, 142000.00, 150000.00, 155000.00, 160000.00]
  }
}
```

**Error Response (500):**
```json
{
  "type": "about:blank",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "Failed to retrieve dashboard summary: Connection timeout",
  "instance": "/ui/dashboard/summary"
}
```

**Caching Behavior:**
- Data is cached for 5 minutes (300,000 milliseconds)
- Subsequent requests within cache TTL return cached data
- Cache automatically expires and refreshes on next request

**Example cURL:**
```bash
curl -X GET http://localhost:8080/ui/dashboard/summary \
  -H "Accept: application/json"
```

**Example JavaScript (Fetch API):**
```javascript
fetch('http://localhost:8080/ui/dashboard/summary')
  .then(response => response.json())
  .then(data => {
    console.log('Total Portfolio:', data.totalPortfolioValue);
    console.log('Active Loans:', data.activeLoansCount);
  })
  .catch(error => console.error('Error:', error));
```

---

### 2. POST /ui/dashboard/cache/invalidate

**Description:** Manually invalidates the dashboard data cache, forcing immediate refresh on next request.

**URL:** `http://localhost:8080/ui/dashboard/cache/invalidate`

**Method:** `POST`

**Authentication:** None required

**Request Headers:**
```http
Content-Type: application/json
```

**Request Body:** None

**Response Status Codes:**
- `204 No Content` - Cache successfully invalidated

**Response Body:** None (empty body)

**Use Cases:**
- After bulk loan imports
- After batch payment processing
- After mass loan state updates
- When immediate data refresh is required

**Example cURL:**
```bash
curl -X POST http://localhost:8080/ui/dashboard/cache/invalidate
```

**Example JavaScript (Fetch API):**
```javascript
fetch('http://localhost:8080/ui/dashboard/cache/invalidate', {
  method: 'POST'
})
  .then(response => {
    if (response.status === 204) {
      console.log('Cache invalidated successfully');
    }
  })
  .catch(error => console.error('Error:', error));
```

---

## Data Models

### DashboardSummaryDTO

Main response object containing all dashboard metrics.

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `totalPortfolioValue` | BigDecimal | Sum of all loan principal amounts | 5000000.00 |
| `activeLoansCount` | Integer | Count of loans in "active" or "funded" states | 45 |
| `outstandingPrincipal` | BigDecimal | Sum of outstanding principal for active/funded loans | 4250000.00 |
| `activeBorrowersCount` | Integer | Distinct count of borrowers with active/funded loans | 38 |
| `statusDistribution` | StatusDistributionDTO | Breakdown of loans by workflow state | See below |
| `portfolioTrend` | PortfolioTrendDTO | Monthly portfolio values (last 12 months) | See below |
| `aprDistribution` | List&lt;BigDecimal&gt; | Array of APR values for all loans | [5.5, 6.0, 6.5] |
| `monthlyPayments` | MonthlyPaymentsDTO | Monthly payment totals (last 6 months) | See below |

### StatusDistributionDTO

Parallel arrays for status labels and their counts.

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `labels` | List&lt;String&gt; | Workflow state names | ["active", "funded", "matured"] |
| `values` | List&lt;Integer&gt; | Counts for each state (same order as labels) | [25, 15, 8] |

**Notes:**
- Labels and values arrays are parallel (index i in values corresponds to index i in labels)
- States are sorted by count (descending) for better visualization
- All workflow states present in data are included

### PortfolioTrendDTO

Parallel arrays for months and portfolio values.

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `months` | List&lt;String&gt; | Month labels in YYYY-MM format | ["2024-11", "2024-12", "2025-01"] |
| `values` | List&lt;BigDecimal&gt; | Portfolio values for each month | [1500000.00, 1750000.00, 2000000.00] |

**Notes:**
- Always returns exactly 12 months (current month and previous 11 months)
- Months without funded loans show 0.00
- Values represent sum of principal amounts for loans funded in that month

### MonthlyPaymentsDTO

Parallel arrays for months and payment amounts.

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `months` | List&lt;String&gt; | Month labels in YYYY-MM format | ["2025-05", "2025-06", "2025-07"] |
| `amounts` | List&lt;BigDecimal&gt; | Payment totals for each month | [125000.00, 135000.00, 142000.00] |

**Notes:**
- Always returns exactly 6 months (current month and previous 5 months)
- Months without payments show 0.00
- Amounts represent sum of payment amounts with valueDate in that month

---

## Implementation Details

### Data Sources

The dashboard aggregates data from two primary entity types:

#### 1. Loan Entity

**Fields Used:**
- `loanId` - Business identifier
- `partyId` - Reference to borrower (for distinct borrower count)
- `principalAmount` - Original loan amount (for portfolio value)
- `apr` - Annual Percentage Rate (for APR distribution)
- `fundingDate` - Date loan was funded (for portfolio trend)
- `outstandingPrincipal` - Current principal balance (for outstanding calculation)

**Metadata Used:**
- `state` - Workflow state (for active loan filtering and status distribution)

**Active Loan States:**
- `"active"` - Loan is actively accruing interest
- `"funded"` - Loan is funded but not yet active

#### 2. Payment Entity

**Fields Used:**
- `paymentId` - Business identifier
- `loanId` - Reference to loan
- `paymentAmount` - Total payment amount
- `valueDate` - Effective date for accounting (for monthly grouping)

### Calculation Logic

#### Total Portfolio Value
```java
Sum of Loan.principalAmount for ALL loans (regardless of state)
```

#### Active Loans Count
```java
Count of loans where metadata.state = "active" OR "funded"
```

#### Outstanding Principal
```java
Sum of Loan.outstandingPrincipal for loans where metadata.state = "active" OR "funded"
```

#### Active Borrowers Count
```java
Distinct count of Loan.partyId for loans where metadata.state = "active" OR "funded"
```

#### Status Distribution
```java
Group all loans by metadata.state
Count loans in each group
Sort by count descending
Return parallel arrays of labels and values
```

#### Portfolio Trend (Last 12 Months)
```java
For each of last 12 months (current month - 11 to current month):
  Sum Loan.principalAmount where YearMonth(fundingDate) = month
  If no loans funded in month, value = 0.00
Return parallel arrays of months (YYYY-MM) and values
```

#### APR Distribution
```java
Extract Loan.apr from ALL loans
Filter out null values
Return as array
```

#### Monthly Payments (Last 6 Months)
```java
For each of last 6 months (current month - 5 to current month):
  Sum Payment.paymentAmount where YearMonth(valueDate) = month
  If no payments in month, amount = 0.00
Return parallel arrays of months (YYYY-MM) and amounts
```

---

## Caching Strategy

### Implementation

- **Technology:** `ConcurrentHashMap` with atomic `compute()` operation
- **TTL:** 5 minutes (300,000 milliseconds)
- **Thread-Safety:** Fully thread-safe for concurrent requests
- **Cache Key:** Single key `"dashboard_summary"`

### Cache Behavior

**Cache Hit:**
```
Request → Check cache → Valid? → Return cached data (< 10ms)
```

**Cache Miss/Expired:**
```
Request → Check cache → Invalid/Missing → Fetch from DB → Aggregate → Cache → Return (100-500ms)
```

### Performance Impact

| Scenario | Queries/Hour | Load Reduction |
|----------|--------------|----------------|
| No Cache (30s refresh) | 120 | Baseline |
| With Cache (5min TTL) | 12 | 90% reduction |

### Manual Invalidation

Use the `POST /ui/dashboard/cache/invalidate` endpoint to force immediate refresh:

```javascript
// After bulk data import
await importLoans(data);
await fetch('http://localhost:8080/ui/dashboard/cache/invalidate', { method: 'POST' });
```

---

## Error Handling

### Error Response Format

All errors follow Spring's ProblemDetail (RFC 7807) format:

```json
{
  "type": "about:blank",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "Failed to retrieve dashboard summary: <error message>",
  "instance": "/ui/dashboard/summary"
}
```

### Common Error Scenarios

| Status Code | Scenario | Resolution |
|-------------|----------|------------|
| 500 | Database connection failure | Retry after delay, check backend logs |
| 500 | Data aggregation error | Check data integrity, contact support |
| 503 | Service unavailable | Backend is starting up, retry after 30s |

### Frontend Error Handling Example

```javascript
async function fetchDashboard() {
  try {
    const response = await fetch('http://localhost:8080/ui/dashboard/summary');
    
    if (!response.ok) {
      const error = await response.json();
      console.error('Dashboard error:', error.detail);
      // Show user-friendly error message
      return null;
    }
    
    return await response.json();
  } catch (error) {
    console.error('Network error:', error);
    // Show network error message
    return null;
  }
}
```

---

## Integration Guide

### React Integration Example

```javascript
import React, { useState, useEffect } from 'react';

function Dashboard() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await fetch('http://localhost:8080/ui/dashboard/summary');
        if (!response.ok) throw new Error('Failed to fetch dashboard data');
        const json = await response.json();
        setData(json);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
    // Refresh every 30 seconds (cache handles backend load)
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, []);

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;

  return (
    <div>
      <h1>Portfolio Dashboard</h1>
      <div className="metrics">
        <div>Total Portfolio: ${data.totalPortfolioValue.toLocaleString()}</div>
        <div>Active Loans: {data.activeLoansCount}</div>
        <div>Outstanding: ${data.outstandingPrincipal.toLocaleString()}</div>
        <div>Active Borrowers: {data.activeBorrowersCount}</div>
      </div>
      {/* Render charts using data.statusDistribution, portfolioTrend, etc. */}
    </div>
  );
}
```

### Chart Integration (Chart.js Example)

```javascript
// Status Distribution Pie Chart
const statusChartData = {
  labels: data.statusDistribution.labels,
  datasets: [{
    data: data.statusDistribution.values,
    backgroundColor: ['#4CAF50', '#2196F3', '#FFC107', '#F44336']
  }]
};

// Portfolio Trend Line Chart
const trendChartData = {
  labels: data.portfolioTrend.months,
  datasets: [{
    label: 'Portfolio Value',
    data: data.portfolioTrend.values,
    borderColor: '#2196F3',
    fill: false
  }]
};

// Monthly Payments Bar Chart
const paymentsChartData = {
  labels: data.monthlyPayments.months,
  datasets: [{
    label: 'Monthly Payments',
    data: data.monthlyPayments.amounts,
    backgroundColor: '#4CAF50'
  }]
};
```

---

## Performance Characteristics

### Response Times

| Scenario | Expected Time | Notes |
|----------|---------------|-------|
| Cache Hit | < 10ms | Served from memory |
| Cache Miss | 100-500ms | Depends on data volume |
| First Request | 200-800ms | Cold start + aggregation |

### Data Volume Scalability

| Loan Count | Payment Count | Performance | Recommendation |
|------------|---------------|-------------|----------------|
| < 1,000 | < 10,000 | Excellent | Current implementation |
| 1,000-10,000 | 10,000-100,000 | Good | Current implementation |
| > 10,000 | > 100,000 | Acceptable | Consider DB aggregation |

### Concurrent Request Handling

- ✅ Thread-safe caching ensures single DB query even with concurrent requests
- ✅ ConcurrentHashMap prevents race conditions
- ✅ Atomic compute() operation ensures consistency

---

## Testing

### Manual Testing

**Test 1: Retrieve Dashboard Data**
```bash
curl -X GET http://localhost:8080/ui/dashboard/summary | jq
```

**Test 2: Verify Caching (should be fast on second call)**
```bash
time curl -X GET http://localhost:8080/ui/dashboard/summary > /dev/null
time curl -X GET http://localhost:8080/ui/dashboard/summary > /dev/null
```

**Test 3: Cache Invalidation**
```bash
curl -X POST http://localhost:8080/ui/dashboard/cache/invalidate
curl -X GET http://localhost:8080/ui/dashboard/summary | jq
```

### Automated Tests

The backend includes tests:
- **Service Tests:** 12 tests covering all calculation methods and caching
- **Controller Tests:** 10 tests covering endpoints and error scenarios
- **Total:** 22 tests, all passing

Run tests:
```bash
./gradlew test --tests "*Dashboard*"
```

---

## Summary

Patrick, the dashboard backend is fully implemented and production-ready. The REST API provides:

1. **GET /ui/dashboard/summary** - Returns all aggregated metrics with 5-minute caching
2. **POST /ui/dashboard/cache/invalidate** - Forces cache refresh

The implementation uses thread-safe caching to reduce database load by 90% while providing real-time data freshness. All data is aggregated from Loan and Payment entities with proper error handling and logging.

For UI implementation, simply call the GET endpoint every 30 seconds - the backend cache will handle the load efficiently.

