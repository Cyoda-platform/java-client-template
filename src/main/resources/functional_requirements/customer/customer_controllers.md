# Customer Controller Requirements

## Overview
CustomerController manages REST API endpoints for customer operations in the Purrfect Pets store.

## Endpoints

### GET /api/customers
Get all customers
**Response**: List of Customer entities with metadata

### GET /api/customers/{customerId}
Get specific customer by ID
**Response**: Customer entity with metadata

### POST /api/customers
Create new customer
**Request**: Customer entity data
**Response**: Created Customer entity with metadata

### PUT /api/customers/{customerId}
Update customer with optional state transition
**Request**: 
```json
{
  "customer": { "firstName": "John", "lastName": "Doe", "email": "john@example.com" },
  "transitionName": "verify_customer"
}
```
**Response**: Updated Customer entity with metadata

### DELETE /api/customers/{customerId}
Suspend customer account
**Request**: Empty body with transition
**Response**: Updated Customer entity with metadata

## Transition Names
- register_customer (automatic)
- verify_customer
- suspend_customer
- suspend_verified_customer
- reactivate_customer
