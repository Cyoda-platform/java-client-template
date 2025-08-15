# Final Functional Requirements

## Purpose

This document defines the functional requirements for a simple order management prototype that supports importing products and users, cart management, checkout, inventory control, payments, and shipments. It clarifies entity structures, workflows, API endpoints and important behaviors (validation, concurrency, idempotency, and security).

## Scope

- Import products and users from CSV or API (JSON). Imported data populates the system and is validated.
- Support two user roles: Admin and Customer.
- Allow Customers to build a shopping cart and perform checkout.
- Provide CRUD APIs for Products and Users, Cart management APIs, Checkout endpoint, Order and Shipment APIs, and Import endpoints.

---

## Actors

- Admin: manages products, users, inventory, and can trigger imports.
- Customer: browses products, manages a cart, and places orders.
- External systems: payment gateway(s), shipment provider(s), import sources (CSV uploads or API), and notification channels (email/webhook).

---

## Entities and Fields

All entities include createdAt timestamps and identifiers. Where applicable, fields marked with (required) must be present.

- Product
  - id (UUID) (required)
  - sku (string) (required, unique)
  - name (string) (required)
  - description (string)
  - price (decimal) (required)
  - currency (ISO 4217 code) (required)
  - stockQuantity (integer) (required, >= 0)
  - weight (decimal)
  - dimensions (object: length, width, height)
  - category (string)
  - images (array of URLs)
  - attributes (JSON map of key/value for flexible attributes)
  - importSource (string) (optional)
  - version (integer) for optimistic locking

- User
  - id (UUID) (required)
  - firstName (string)
  - lastName (string)
  - email (string) (required, unique)
  - phone (string)
  - billingAddress (object)
  - shippingAddress (object)
  - passwordHash (string) (required for local auth)
  - roles (array: values "ADMIN" or "CUSTOMER") (required)
  - createdAt (timestamp)
  - isActive (boolean)

- ShoppingCart
  - id (UUID)
  - userId (UUID) (nullable for anonymous carts if supported)
  - items: array of CartItem
  - currency (ISO 4217)
  - subtotal, taxes, shipping, discounts, total (calculated)
  - updatedAt, createdAt

- CartItem
  - productId (UUID) (required)
  - sku (string)
  - price (decimal at time of add)
  - quantity (integer > 0)
  - attributes (JSON)

- InventoryReservation
  - id (UUID)
  - productId
  - quantity
  - reservedAt
  - expiresAt
  - cartId or orderId

- Order
  - id (UUID)
  - userId
  - orderNumber (human-friendly)
  - items (snapshot of cart items including price)
  - subtotal, tax, shipping, discounts, total
  - currency
  - paymentStatus (enum: UNPAID, AUTHORIZED, CAPTURED, FAILED, REFUNDED)
  - fulfillmentStatus (enum: PENDING, PICKED, SHIPPED, DELIVERED, CANCELLED)
  - status (enum: PENDING, PAID, CANCELLED, COMPLETED)
  - createdAt, updatedAt

- PaymentRecord
  - id (UUID)
  - orderId
  - provider (string)
  - amount
  - currency
  - transactionId (external)
  - status (enum: INITIATED, AUTHORIZED, CAPTURED, FAILED, REFUNDED)
  - rawResponse (JSON)

- Shipment
  - id (UUID)
  - orderId
  - provider
  - trackingNumber
  - status (enum: CREATED, IN_TRANSIT, DELIVERED, CANCELLED)
  - shippingAddress

---

## Import Behavior (Products and Users)

- Supported formats: CSV file upload and JSON via API. CSV format must include documented column names; missing optional columns are allowed.
- Imports must be idempotent: an import request can be retried safely using an idempotency key provided by the client. The backend should deduplicate by sku (products) or email (users) unless a replace/overwrite flag is used.
- Validation: required fields must be present and valid (e.g., price >= 0, email format). Invalid rows should be flagged; import should return a summary with succeeded/failed rows and reasons.
- Role assignment: imported users must include a role value; if missing, default to CUSTOMER. Roles are restricted to ADMIN or CUSTOMER.
- Products: imported products populate stockQuantity (>= 0) and attributes JSON. If sku exists and overwrite flag is true, update product fields; otherwise ignore or append as configured.
- Audit: record importSource, uploader, and timestamps.

---

## Shopping Cart Behavior

- Cart lifecycle
  - Customers can create one or more carts. For logged-in users, the system should associate a primary active cart.
  - Anonymous carts are allowed but must be tied to a session id and can be merged into a user cart upon login.
- Cart operations
  - Add item: validate product exists and requested quantity is >0 and not exceeding a configurable per-order max.
  - Remove item, update quantity, clear cart.
  - Price snapshot: item price is captured when added, but price changes to product should be handled according to policy (e.g., remind user to refresh prices at checkout).
  - Totals: subtotal, taxes, shipping estimates, discounts, and grand total are computed consistently.
- Inventory reservation
  - On add-to-cart: no permanent decrement by default. Optionally create a short-lived InventoryReservation (configurable) to prevent overselling during high concurrency scenarios.
  - Reservation TTL: configurable (e.g., 15 minutes). A background job releases expired reservations and notifies affected carts.

---

## Checkout Workflow (detailed)

Preconditions: cart must have at least one item, user must be active, shipping and payment details present.

1. Validate cart contents
   - Ensure each product exists, verify price consistency policy, and check requested quantities.
2. Inventory check & reservation
   - Check stockQuantity for each product.
   - Two supported strategies (configurable):
     - Reserve-then-capture: create InventoryReservation entries for quantities and decrease available stock (or mark reserved) but do not permanently decrement until payment capture. Reservation has TTL.
     - Decrement-on-order: atomically decrement stockQuantity at order creation (use optimistic locking). If payment fails, create compensating flows to restock.
   - If insufficient stock for any item, return a failure with per-item availability.
3. Payment processing
   - Support both authorize-only and authorize+capture modes (configurable per integration or per order).
   - The system must support idempotent payment attempts using an idempotency key.
   - On payment success (captured or authorized per configuration), create a PaymentRecord and update Order paymentStatus.
   - On payment failure, release reservations (if any) and fail checkout with a descriptive error. Support retry flows and expose error codes from provider.
4. Order creation
   - Create Order record as a snapshot of cart items and totals. Order state depends on payment outcome (e.g., PENDING if payment in progress, PAID if captured).
   - Persist shipment address, selected shipping method, and taxes.
5. Post-order actions
   - If payment captured → mark paymentStatus=CAPTURED and status=PAID, decrement final stock (if reserved earlier, finalize reservation).
   - If payment authorized only → mark paymentStatus=AUTHORIZED and order status=PENDING; capture later via admin action or scheduled capture.
   - Trigger notifications (email/webhook) for order confirmation.
   - Create initial Shipment record(s) if shipment provider integration supports immediate creation; otherwise defer to fulfillment step.

Edge cases
- Partial fulfillment: if some items are unavailable, checkout should either fail entirely or allow partial fulfillment depending on configuration (default: fail entire order).
- Payment timeout: if payment gateway times out, release reservations and allow retry.
- Concurrency: use optimistic locking on Product stockQuantity (version field) and/or database transactions to prevent oversell.

---

## Order Lifecycle and Statuses

- Order.status: PENDING, PAID, CANCELLED, COMPLETED
- Order.paymentStatus: UNPAID, AUTHORIZED, CAPTURED, FAILED, REFUNDED
- Order.fulfillmentStatus: PENDING, PICKED, SHIPPED, DELIVERED, CANCELLED

Transitions
- PENDING -> PAID when payment captured
- PENDING -> CANCELLED on user/admin cancellation before payment capture
- PAID -> COMPLETED after delivery
- PAID -> CANCELLED possible with refund flow (paymentStatus -> REFUNDED)

Cancellation & Refunds
- Cancellation requests before capture should release reservations and restock as needed.
- Refunds must create PaymentRecord entries and update paymentStatus.

---

## Shipment

- Shipments link to orders and contain provider-specific tracking.
- Support creation via sync API call to provider or via manual creation by admin.
- Shipment status updates can be received via webhooks; system must map external statuses to internal statuses.

---

## APIs (high-level)

Assume base path /api/v1. All APIs require authentication (JWT or OAuth2). Admin-only endpoints require ADMIN role.

- Products
  - GET /products [pagination, filtering by category, price range, sku]
  - GET /products/{id}
  - POST /products (Admin)
  - PUT /products/{id} (Admin)
  - DELETE /products/{id} (Admin)
- Users
  - GET /users (Admin)
  - GET /users/{id} (Admin or owner)
  - POST /users (signup or Admin-created)
  - PUT /users/{id}
  - DELETE /users/{id} (Admin)
- Imports
  - POST /imports/products (Admin) - supports CSV upload or JSON body, idempotencyKey, overwrite flag
  - POST /imports/users (Admin) - supports CSV upload or JSON body, idempotencyKey, overwrite flag
  - GET /imports/{id}/status (Admin)
- Cart
  - GET /cart (current user)
  - POST /cart/items (add item)
  - PUT /cart/items/{itemId} (update quantity)
  - DELETE /cart/items/{itemId}
  - POST /cart/merge (merge anonymous with user cart)
- Checkout
  - POST /checkout - triggers checkout workflow, requires payment method token or payment details, idempotencyKey
  - POST /orders/{id}/capture (Admin or automated) - capture authorized payments
  - POST /orders/{id}/cancel - cancel order
- Orders
  - GET /orders (owner/Admin) [pagination]
  - GET /orders/{id}
- Shipments
  - GET /shipments
  - POST /shipments (Admin or integration)
  - POST /shipments/{id}/status (webhook/updater)

API behaviors
- Use standard HTTP status codes. Return structured error payloads with code and message.
- Support pagination, sorting and filtering where lists are returned.
- Provide idempotency for imports and checkout using Idempotency-Key header.

---

## Security and Authentication

- Authentication: JWT or OAuth2 for APIs.
- Authorization: role-based access control (ADMIN or CUSTOMER). Admin-only endpoints enforced.
- Passwords: store only passwordHash using a strong algorithm (bcrypt/argon2) + salt.
- Sensitive data: encrypt sensitive fields at rest if required (e.g., payment tokens).
- Validation and sanitization: all inputs must be validated server-side to prevent injection.
- Rate limiting: configurable throttling for public endpoints and import endpoints.

---

## Data Validation & Constraints

- Prices must be >= 0 and use decimal with appropriate precision.
- Currency must be valid ISO 4217 code.
- stockQuantity must be integer >= 0.
- SKU and email uniqueness enforced.
- Import rows failing validation must be reported back with line number and reason.

---

## Error Handling & Idempotency

- Provide clear error codes and human-readable messages.
- All mutating endpoints that may be retried (imports, checkout, payment capture) must accept and honor an Idempotency-Key to avoid duplicate side-effects.
- Background jobs (e.g., reservation cleanup) must be idempotent.

---

## Notifications & Webhooks

- Send order confirmation emails to customers on successful order creation (configurable).
- Provide webhooks for order.created, order.paid, shipment.updated, import.completed.
- Allow webhook endpoint registration and retry logic for failed deliveries.

---

## Audit, Monitoring & Logging

- Record audit logs for critical actions: product changes, price updates, stock changes, user role changes, imports, payment captures/refunds.
- Log payment provider responses for troubleshooting (store rawResponse in PaymentRecord, masked as needed).
- Expose metrics for order rates, failed payments, reservation expirations, import success/failure.

---

## Non-functional Requirements (brief)

- Performance: handle typical prototype load; endpoints for listing support pagination.
- Scalability: stateless API servers with shared DB and background workers for reservations and imports.
- Reliability: retries for external integrations (payment/shipment) with backoff.

---

## Assumptions & Open Questions

- Tax and shipping calculation: externalized or simplified for prototype — policy to be defined.
- Pricing policy on product price changes between add-to-cart and checkout: default behavior is to show warning and use price at checkout if policy configured.
- Partial order fulfillment vs full-order guarantee should be configurable; default is full-order guarantee (fail if any item unavailable).

---

## Acceptance Criteria (high level)

- Admin can import products and users via CSV and API and see import results.
- Customers can add products to cart and complete checkout through configured payment provider.
- System enforces stock constraints and prevents oversell under concurrency.
- Orders are created with correct statuses and appropriate notifications are triggered.


