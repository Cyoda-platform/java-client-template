# Functional Requirements

This document defines up-to-date functional requirements for the Java 21 prototype application. It describes entities, fields, workflows, processors/criteria behavior, API contracts, event-driven processing semantics, and cross-cutting constraints (idempotency, concurrency, observability). The content supersedes and clarifies earlier drafts.

---

## Table of contents

- Summary
- Entity Definitions
  - Common notes
  - User
  - Product
  - Cart
  - Order
  - Reservation (operational entity)
- Workflows (state machines + updated logic)
  - User
  - Product
  - Cart
  - Order
- Processors and Criteria (behavioral pseudocode, idempotency, concurrency)
- API Design and Rules
  - POST behaviour, idempotency and headers
  - GET behaviour and responses
  - Endpoints summary
- Event-driven processing
- Required Java classes (overview)
- Assumptions and constraints
- Open questions / recommended improvements

---

## Summary

This document captures current business logic and operational expectations. Key updates and clarifications include:

- Explicit treatment of inventory reservations as a first-class concept (Reservation records) instead of directly decrementing Product.availableQuantity on a temporary basis.
- Clear idempotency guidance for POST operations (recommended idempotency-key header) and idempotent processors.
- Payment interaction clarified to support both authorize-only and authorize-and-capture flows; explicit paymentStatus and order.status transitions described.
- Cart abandonment TTL, reservation hold TTL, and background jobs clarified.
- Concurrency and consistency guidance (optimistic locking, atomic transitions, retries).

---

## Entity Definitions

General guidelines:
- All entity ids are UUID strings (RFC4122). Fields named `id` represent the entity's business/technical identifier when returned by GETs.
- Timestamps are ISO8601 strings in UTC.
- Sensitive fields (passwordHash, payment tokens) must not be returned by GET endpoints.
- Role and status values are stored as Strings per constraint; code may map to internal enums for safety but persist as String.

User
- id: String (UUID, primary business identifier)
- role: String ("Admin" or "Customer")
- email: String (unique across active and pending users, used as login)
- name: String (display name)
- passwordHash: String (hashed password — NEVER returned via API)
- createdAt: String (ISO8601 timestamp)
- active: Boolean (account enabled/disabled)
- emailVerified: Boolean (true when verification is completed)
- externalId: String (optional, for external identity providers)

Product
- id: String (UUID, primary business identifier)
- sku: String (stock keeping unit, unique)
- name: String
- description: String
- price: Number (BigDecimal-equivalent; unit price)
- currency: String (ISO 4217 code)
- availableQuantity: Integer (current on-hand quantity — authoritative available quantity excluding reservations)
- active: Boolean (catalog active flag)
- createdAt: String (ISO8601 timestamp)
- allowBackorder: Boolean (optional; if true, orders may be created that exceed availableQuantity)

Cart
- id: String (UUID, primary business identifier)
- customerId: String (UUID referencing User)
- items: Array of CartItem
  - productId: String (UUID referencing Product)
  - quantity: Integer (requested quantity)
  - unitPrice: Number (snapshot of price at add time)
- totalAmount: Number (computed total of items in the cart)
- currency: String (ISO currency code; all items must use same currency)
- status: String (one of: OPEN, CHECKOUT_IN_PROGRESS, CHECKED_OUT, ABANDONED)
- createdAt: String (ISO8601 timestamp)
- updatedAt: String (ISO8601 timestamp)
- lastActivityAt: String (ISO8601 timestamp, used for abandonment TTL)

Order
- id: String (UUID, primary business identifier)
- customerId: String (UUID referencing User)
- items: Array of OrderItem
  - productId: String
  - quantity: Integer
  - unitPrice: Number
- totalAmount: Number
- currency: String
- status: String (one of: PENDING_PAYMENT, AUTHORIZED, PAID, FULFILLMENT_PENDING, PACKED, SHIPPED, DELIVERED, CANCELLED, REFUNDED)
- paymentStatus: String (one of: NOT_ATTEMPTED, AUTHORIZED, CAPTURED, FAILED, REFUNDED)
- paymentGatewayChargeId: String (optional external charge id)
- shippingAddress: String
- billingAddress: String
- createdAt: String
- updatedAt: String

Reservation (operational entity; not exposed via public API)
- id: String (UUID)
- productId: String
- cartId: String (nullable; reservation may be linked to cart or order)
- orderId: String (nullable; when converted to order)
- quantity: Integer
- reservedAt: String
- expiresAt: String (reservation TTL)
- status: String (ACTIVE, RELEASED, CONSUMED)

Notes on Reservation: reservations are the mechanism to lock inventory without directly reducing Product.availableQuantity in memory. Implementation may maintain availableQuantity as on-hand minus sum(active reservations).

---

## Workflows (state machines and updated logic)

Guiding principles for workflows:
- Processors and criteria must be idempotent where possible.
- State transitions must be atomic. Use optimistic locking (version field) or transactions to avoid races.
- External side-effecting integrations (payment, email, carriers) must be called once and tracked using idempotency keys and the entity's status/metadata.

### User workflow (updated)

High-level steps:
1. Persist User as NEW (trigger UserCreatedEvent).
2. Validate fields (email format, password policy, required fields).
3. Duplicate check (email and externalId uniqueness across active/pending/deleted scope as defined).
4. Activation flows:
   - Admin-created users may be activated immediately (active=true) when approved.
   - Customer-created users require email verification (emailVerified = false) until a verification link/token is validated.
5. On successful completion, set active/emailVerified appropriately and send notification(s).

State transitions remain as previously defined. Important updates:
- Duplicate check should also consider users in PENDING verification depending on business rule: optionally prevent duplicate registries by email.
- The validation processor must enforce password complexity policy (minimum length, entropy) and reject unsafe passwords.

Pseudocode (ValidateUserProcessor):
- if missing required fields -> set entity.validationErrors and mark FAILED
- if invalid email -> add error
- if password fails policy -> add error
- if entity.validationErrors.empty -> pass

DuplicateUserCheckProcessor:
- query user index by email (case-insensitive)
- if found and not soft-deleted -> mark duplicate and FAIL
- otherwise pass

ActivateUserProcessor:
- if role == "Admin" and createdByAdmin flag set, set active=true, emailVerified=true
- if role == "Customer" then set active=false and send verification email (create VerificationToken with expiry)

NotifyUserProcessor:
- call EmailService.send(template, recipient, metadata)
- record notification delivery attempt and status (for audit)

### Product workflow (updated)

Key clarifications:
- Validation ensures SKU uniqueness, non-negative integer quantity, price > 0, currency present.
- Upsert merges by SKU (preferred) or id if present; updates maintain history/audit where possible.
- Publishing toggles active flag; imports may set active=false by default.
- After publish, notify downstream systems (search index, caches) asynchronously.

UpsertProductProcessor pseudocode:
- if payload.sku exists -> find existing by sku
- if found -> update fields, bump updatedAt, maintain availableQuantity using delta rules (unless import explicitly overrides)
- if not found -> insert new product

PublishProductProcessor:
- set product.active based on payload or default rules
- schedule indexing and cache invalidation

NotifyCatalogProcessor:
- call SearchIndexService.index(product) (idempotent)
- call CacheService.invalidate(product.id)

### Cart workflow (revised with reservations and idempotency)

Overview and key updates:
- Cart creation persists cart with status OPEN.
- Adding/updating/removing items snapshots unitPrice at item-add time.
- On checkout initiation, the system attempts to reserve inventory (creating Reservation records) rather than decrementing Product.availableQuantity directly.
- Reservation TTL is configurable (default: 15 minutes). If reservation expires before payment completion, it is released and items are again available.
- The system supports both full and partial reservation semantics; however the default behavior is to fail checkout if any item cannot be reserved unless allowBackorder is set for that product.
- A successful charge transitions the cart -> CHECKED_OUT and results in Order creation with reservations consumed and inventory permanently decremented (or marked consumed).

State definitions: OPEN, CHECKOUT_IN_PROGRESS, CHECKED_OUT, ABANDONED

Checkout flow (StartCheckoutProcessor, ReserveInventoryProcessor, PaymentProcessor):
- StartCheckoutProcessor:
  - Validate cart not empty
  - Validate currency consistency and customerId presence
  - Update cart.status = CHECKOUT_IN_PROGRESS, updatedAt = now
  - generate and persist an idempotencyKey for checkout attempt if not provided

- ReserveInventoryProcessor (idempotent):
  - For each cart item: check (Product.availableQuantity - sumActiveReservationsForProduct) >= quantity OR allowBackorder true
  - If sufficient -> create Reservation record with expiresAt = now + reservationTTL and status ACTIVE
  - Persist reservations atomically or in idempotent manner (use reservation idempotency by cartId+productId+checkoutAttemptId)
  - If any insufficient -> release any newly created reservations and return a failure (detailed insufficient items payload)

- PaymentProcessor (supports immediate or delayed capture):
  - Use an idempotency key for payment calls derived from cartId/checkoutAttemptId
  - If payment mode configured as "authorize_and_capture": call PaymentGateway.authorizeAndCapture(amount)
  - If payment mode configured as "authorize": call PaymentGateway.authorize(amount) and set paymentStatus = AUTHORIZED
  - On success: mark reservations as CONSUMED and persist order creation (move reserved quantities to order consumption), set Cart.status = CHECKED_OUT
  - On failure: release reservations (mark RELEASED) and set cart.status back to OPEN (or a FAILURE state) and return failure info

Abandonment:
- Background job checks carts with lastActivityAt older than abandonmentTTL (configurable, default 7 days) and status OPEN or CHECKOUT_IN_PROGRESS with expired reservations -> mark ABANDONED and release reservations.

### Order workflow (clarified)

Key clarifications:
- Orders may be created by cart checkout (preferred) or by direct POST.
- On creation, Order.status = PENDING_PAYMENT and paymentStatus = NOT_ATTEMPTED.
- Payment flow supports authorize-only followed by capture (capture may be triggered on fulfillment or immediately depending on configuration).
- Allowed transitions: PENDING_PAYMENT -> AUTHORIZED -> PAID (after capture) -> FULFILLMENT_PENDING -> PACKED -> SHIPPED -> DELIVERED. Cancellation and refunds may occur from most states prior to delivery depending on business rules.

PaymentProcessor (Order-scoped) pseudocode:
- If order.paymentStatus == NOT_ATTEMPTED or FAILED and payment details present:
  - call PaymentGateway.authorize(order.totalAmount, paymentMethod) (idempotent with idempotency-key)
  - if authorize succeeds -> set paymentStatus = AUTHORIZED, order.status = AUTHORIZED
  - if immediate capture required or business rule -> call PaymentGateway.capture(chargeId) -> on success paymentStatus = CAPTURED and order.status = PAID
  - record paymentGatewayChargeId and responses
- If payment fails -> set paymentStatus = FAILED and order.status either remains PENDING_PAYMENT or transitions to CANCELLED based on policy and retries

Fulfillment
- On payment captured, call WarehouseService.createFulfillment(order) and transition to FULFILLMENT_PENDING. Fulfillment and packing steps may be manual or automatic.
- ShipmentProcessor will call CarrierService.createShipment(order), set tracking number, update order.status = SHIPPED, and notify the customer.

Refunds and Cancels
- RefundProcessor should call PaymentGateway.refund(chargeId) and on success set paymentStatus = REFUNDED and order.status = REFUNDED.
- Refunds and cancellations must be idempotent and record external refund ids.

---

## Processors and Criteria (detailed behavior and pseudocode)

General rules for processors:
- Be idempotent for safe retries. Use entity-level metadata (lastProcessedAttemptId, lastOutcome, version) to detect duplicates.
- Persist audit of external calls (request/response, attemptId, timestamps).
- Avoid in-memory-only reservation; store Reservation entities in DB to support concurrency and recovery.
- Use optimistic locking (version) or database transactions when modifying entities and related resources (e.g., creating reservations and updating cart status).

Selected pseudocode (concise):

AddItemProcessor:
- validate productId exists and product.active
- fetch product.price and snapshot as unitPrice
- find existing CartItem; if present, newQuantity = existing.quantity + delta
- set item.quantity = newQuantity
- recalc cart.totalAmount = sum(item.quantity * unitPrice)
- update cart.updatedAt and lastActivityAt
- persist
- idempotency: use client-supplied idempotency for add-item calls or dedupe by item add event id

ReserveInventoryProcessor (idempotent):
- input: cartId, checkoutAttemptId
- for each cart item:
  - compute available = product.availableQuantity - sumActiveReservations(product)
  - if available >= item.quantity or product.allowBackorder -> create Reservation with idempotency key (cartId+productId+checkoutAttemptId)
  - else -> fail and return insufficient items
- persist reservations; ensure all-or-nothing semantics when possible

PaymentProcessor (cart checkout):
- input: checkoutAttemptId and paymentMethod
- create paymentAttempt record with idempotencyKey
- call PaymentGateway with idempotency header/key
- on success -> mark PaymentAttempt.success and return success
- on failure -> mark PaymentAttempt.failed and return failure

ReleaseReservationProcessor:
- mark Reservation.status = RELEASED and persist
- notify inventory aggregates if necessary

AbandonCartProcessor:
- find carts with lastActivityAt < now - abandonmentTTL and status IN (OPEN, CHECKOUT_IN_PROGRESS)
- call ReleaseReservationProcessor for related reservations
- mark cart.status = ABANDONED and persist

Notify processors (email, indexing):
- These must be eventually consistent and idempotent. Use message/event delivery semantics and retries with exponential backoff.

---

## API Design and Rules

High-level rules (updated):
- POST endpoints create entities (trigger events). Each successful POST returns only {"technicalId": "string"} in the response body and MUST set a Location response header pointing to GET /api/{{entity}}/{technicalId}.
- POST endpoints should accept an optional Idempotency-Key header. If supplied, the server must use it to ensure exactly-once semantics for side effects (entity creation and subsequent processors), returning the same technicalId for retries with same key.
- POST endpoints MUST NOT return full entity details or secrets (e.g., passwordHash).
- GET endpoints return stored entity representation for the requested technicalId only.
- No GET by non-technical fields included in the public API unless explicitly required later.
- All responses must use UTC ISO8601 timestamps.

Security and validation notes for POST bodies:
- Passwords must follow the configured password policy; server must store only salted password hashes.
- Payment credentials: clients must send tokens or references (never raw card PANs unless PCI-compliant gateway is used). PaymentMethod in requests should contain a token representing a previously stored method or a one-time token.

Endpoints summary (with small clarifications):

1) Users
- POST /api/users
  - Request JSON:
    {
      "role": "Customer",
      "email": "user@example.com",
      "name": "Full Name",
      "password": "plaintextOrClientHash"
    }
  - Optional headers: Idempotency-Key: string
  - Response JSON (201): { "technicalId": "string" }
  - Response headers: Location: /api/users/{technicalId}

- GET /api/users/{technicalId}
  - Response JSON:
    {
      "id": "string",
      "role": "string",
      "email": "string",
      "name": "string",
      "createdAt": "string",
      "active": true,
      "emailVerified": true
    }

2) Products
- POST /api/products
  - Request JSON as before (sku, name, description, price, currency, availableQuantity, active)
  - Optional header: Idempotency-Key
  - Response JSON: {"technicalId": "string"}
  - Location header present

- GET /api/products/{technicalId}
  - Response JSON includes availableQuantity and allowBackorder flag

3) Carts
- POST /api/carts
  - Request JSON:
    {
      "customerId": "string",
      "items": [{ "productId": "string", "quantity": 2 }],
      "currency": "USD"
    }
  - Optional header: Idempotency-Key
  - Response JSON: {"technicalId": "string"}

- GET /api/carts/{technicalId}
  - Response JSON (sensitive fields excluded):
    {
      "id": "string",
      "customerId": "string",
      "items": [{ "productId": "string", "quantity": 2, "unitPrice": 19.99 }],
      "totalAmount": 39.98,
      "currency": "USD",
      "status": "OPEN",
      "createdAt": "string",
      "updatedAt": "string"
    }

- Additional cart actions (not exposed as separate public endpoints in this document) include: add-item, update-quantity, remove-item, start-checkout, abandon (background job).

4) Orders
- POST /api/orders
  - Request JSON:
    {
      "customerId": "string",
      "cartId": "string", // optional; server may create an order from cart
      "paymentMethod": { "type": "card", "token": "..." },
      "shippingAddress": "string",
      "billingAddress": "string"
    }
  - Optional header: Idempotency-Key
  - Response JSON: {"technicalId": "string"}
  - On cart-based checkout the system consumes reservations when the order is created & payment captured

- GET /api/orders/{technicalId}
  - Response JSON includes paymentStatus, status, shipping/billing addresses, createdAt, updatedAt

Notes on responses:
- All POST endpoints must return 409 Conflict when idempotency or uniqueness constraints are violated in a way that cannot be reconciled (e.g., trying to create duplicate SKU without idempotency key).
- Validation errors return 400 with a machine-readable errors array.

---

## Event-Driven Processing Notes

- Each POST that persists a User/Product/Cart/Order emits an EntityCreated event (UserCreatedEvent, ProductCreatedEvent, CartCreatedEvent, OrderCreatedEvent).
- The orchestration component (Cyoda or equivalent) subscribes and invokes the entity-specific process method which advances the workflow described in this document.
- Events must contain metadata (entityId, attemptId, timestamp, idempotencyKey) so downstream processors can ensure idempotency and correlate retries.
- Processors calling external integrations MUST store the external request id and response, and re-use the stored information when replaying events.

---

## Required Java classes (overview)

- Criteria interfaces and implementations:
  - ValidateUserCriterion, InventoryAvailabilityCriterion, ValidateProductCriterion
- Processor interfaces and implementations (examples):
  - ValidateUserProcessor, DuplicateUserCheckProcessor, ActivateUserProcessor, NotifyUserProcessor
  - ValidateProductProcessor, UpsertProductProcessor, PublishProductProcessor, NotifyCatalogProcessor
  - AddItemProcessor, StartCheckoutProcessor, ReserveInventoryProcessor, PaymentProcessor (cart checkout), ReleaseReservationProcessor, AbandonCartProcessor
  - OrderPaymentProcessor, FulfillmentProcessor, PackingProcessor, ShipmentProcessor, RefundProcessor
- Event classes:
  - UserCreatedEvent, ProductCreatedEvent, CartCreatedEvent, OrderCreatedEvent
- Operational classes:
  - Reservation entity repo, PaymentAttempt entity repo, Notification/Audit stores
- Workflow orchestrator bindings:
  - Register entity class with Cyoda so that persistence triggers process(entity)

Implementation guidance:
- Use repositories that support optimistic locking or ACID transactions for multi-entity updates (cart + reservations + order)
- External integrations wrappers should encapsulate idempotency keys and retry semantics

---

## Assumptions and Constraints

- Programming Language: Java 21
- POST endpoints return only technicalId in response body and include Location header.
- No additional top-level entities beyond User, Product, Cart, Order, and operational Reservations are introduced unless requested.
- Payment gateway, email, search index, and warehouse services are external integrations invoked by processors.
- All timestamps are ISO8601 strings in UTC.
- Role values, statuses, and payment states are stored as Strings (do not use enums in persistent storage) but code may map to internal enums for safety.
- Sensitive data are not returned by GET endpoints.

---

## Open questions / recommended improvements

- Should the API expose a dedicated endpoint to create and manage Reservations for advanced integrations? (Currently reservations are internal.)
- Should we allow partial-checkout semantics (allow some items to be reserved/checked out and others left in cart) or always require an all-or-nothing reservation? Current recommendation: default to all-or-nothing but allow per-product allowBackorder.
- Confirm default reservation TTL (15 minutes suggested) and cart abandonment TTL (7 days suggested).
- Consider persisting enums as strings but providing a central Java enum type with converters to reduce errors.
- For PCI considerations, prefer tokenization of card data via the payment gateway and never log raw PANs.

---

If you want I can:
- Add sequence/state diagrams in mermaid syntax for updated workflows (user, cart, order)
- Produce example API request/response traces including headers for idempotency
- Generate skeleton Java classes for the processors/criteria described above

Please confirm if you want any of these outputs added or further changes to the requirements.