# Customer API - Functional Requirements

## Overview
A simple REST API for managing customers with CRUD operations.

## Entities
- Customer
  - id (UUID)
  - name (string, required)
  - email (string, required, basic format validation)
  - phone (string, optional)

## Endpoints
- POST /customers - Create a new customer
- GET /customers - List all customers
- GET /customers/{id} - Get customer by id
- PUT /customers/{id} - Update customer by id
- DELETE /customers/{id} - Delete customer by id

## Validation
- name and email required for create
- email must contain an '@' character

## Persistence
- In-memory repository (for initial implementation). Later, swap with a persistent store.

## Non-functional
- JSON over HTTP
- Use standard HTTP status codes
- Basic unit tests for controller and service layers
