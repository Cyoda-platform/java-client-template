# Customer Management - Functional Requirements

## Overview
A simple Customer Management API to create, read, update, and delete customer records.

## Entities
Customer
- id: string (unique)
- name: string
- email: string (validated)
- phone: string
- address: string
- createdAt: timestamp

## Endpoints
- POST /customers -> Create customer
- GET /customers -> List customers (supports pagination and filtering by name/email)
- GET /customers/{id} -> Retrieve customer by id
- PUT /customers/{id} -> Update customer
- DELETE /customers/{id} -> Delete customer

## Validation
- email must be a valid email format
- name is required

## Error handling
- 404 for not found
- 400 for validation errors
- 500 for server errors

## Notes
- Use in-memory storage for initial implementation (can switch to a persistent store later)
- Include basic unit tests and example curl commands
