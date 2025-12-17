# Customer Management System - Functional Requirements

## Purpose
Provide a centralized service to manage customer data (create, read, update, delete), including validation, audit trails, and lifecycle workflows.

## Scope
This service manages customer entities, handles validation and business rules, provides REST APIs for integration, supports promo codes and discount application for orders, and exposes events for downstream processing.

## Entities
- Customer
  - id (string)
  - name (string)
  - email (string)
  - phone (string)
- Product
  - id (string)
  - name (string)
  - description (string)
  - price (number)
  - stock (integer)
- PromoCode
  - code (string)
  - type (PERCENT|FIXED)
  - value (number)
  - expiryDate (ISO date string)
  - usageLimit (integer)
  - timesUsed (integer)
  - active (boolean)
- Order (note: part of discount feature)
  - id (string)
  - items (array of product references)
  - total (number)
  - discountAmount (number)
  - discountType (PERCENT|FIXED)
  - discountedTotal (number)
  - appliedPromoCode (string|null)

## Workflows
- CustomerWorkflow
  - States: initial_state -> active -> deleted
  - Transitions: create (initial_state -> active), update (active -> active), delete (active -> deleted)
- OrderWorkflow
  - Transitions: create, update, complete, cancel (with discount processors attached to create/update)

## API Endpoints
- Customers
  - GET /customers
  - GET /customers/{id}
  - POST /customers
  - PUT /customers/{id}
  - DELETE /customers/{id}
- Products
  - GET /products
  - GET /products/{id}
  - POST /products
  - PUT /products/{id}
  - DELETE /products/{id}
- Orders
  - GET /orders
  - GET /orders/{id}
  - POST /orders
  - PUT /orders/{id}
  - DELETE /orders/{id}

## Non-functional Requirements
- Validation: Email format, phone format, price >= 0, stock >= 0
- Security: Authentication and authorization for order and product operations
- Performance: API responses under 300ms for typical requests
- Reliability: Event delivery guarantee for downstream services

## Acceptance Criteria
- All CRUD endpoints for Customer and Product functioning
- Customer email validation enforced on create/update
- Discount feature applies correctly based on promo code type and value
- Workflows and processors wired and visible in Canvas

## Notes
- Use Cyoda Canvas to review entities, workflows, processors and routes.
- The discount feature was added and will be available after the CLI task completes.
