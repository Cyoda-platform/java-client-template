# Customer Controller Requirements

## Overview
CustomerController manages REST API endpoints for customer registration, verification, and profile management.

## Endpoints

### GET /api/customers
**Purpose**: Retrieve all customers
**Request**: `GET /api/customers`
**Response**:
```json
[
  {
    "entity": {
      "customerId": "cust-001",
      "firstName": "John",
      "lastName": "Doe",
      "email": "john.doe@example.com",
      "phone": "555-1234",
      "experienceLevel": "intermediate"
    },
    "meta": {
      "uuid": "uuid-456",
      "state": "active"
    }
  }
]
```

### GET /api/customers/{id}
**Purpose**: Retrieve specific customer by ID
**Request**: `GET /api/customers/cust-001`
**Response**: Single customer object with metadata

### POST /api/customers
**Purpose**: Register new customer
**Request**:
```json
{
  "customerId": "cust-002",
  "firstName": "Jane",
  "lastName": "Smith",
  "email": "jane.smith@example.com",
  "phone": "555-5678",
  "address": {
    "street": "123 Main St",
    "city": "Anytown",
    "state": "CA",
    "zipCode": "12345"
  }
}
```
**Response**: Created customer with metadata

### PUT /api/customers/{id}
**Purpose**: Update customer with optional state transition
**Request**: `PUT /api/customers/cust-001?transition=verify_customer`
**Body**: Updated customer data
**Response**: Updated customer with new state
