# Functional Requirements — Order Management (Event‑Driven / Cyoda)

This document defines the functional requirements and up‑to‑date logic for the order management system. It covers the entities, workflows, processors/criteria, pseudocode, and API rules. The logic below corrects and unifies earlier inconsistencies (especially around stock reservation and order lifecycle).

---

## 1. Overview

- Architecture: event‑driven (Cyoda-like) processing. Entities are persisted by POST calls that emit creation events which drive processors and criteria.
- Conventions: technicalId = internal UUID returned on POST. businessId (userId/productId/orderId) is the business identifier visible to integrators/users.
- Default entities included: User, Product, CartOrder. Recommended additional entity: ImportJob (for bulk imports) — see questions at the end.

---

## 2. Entities and Fields

Each entity lists required fields and notes about validation/visibility.

### User

- technicalId: String (internal UUID)
- userId: String (business id visible to users)
- name: String
- email: String
- role: String (Admin | Customer)
- status: String (Created | PendingValidation | NeedsReview | Active | Blocked)
- createdAt: String (ISO timestamp)
- updatedAt: String (ISO timestamp)
- importedFrom: String (optional source identifier)

Notes:
- `Created` is the persisted initial state. Automatic validation moves the entity to `PendingValidation`, `NeedsReview`, or `Active`.

### Product

- technicalId: String (internal UUID)
- productId: String (business id)
- name: String
- description: String
- price: Number (unit price, required > 0)
- sku: String
- stockQuantity: Integer (available units)
- status: String (Created | PendingValidation | NeedsReview | Active | Inactive | LowStock)
- lowStockThreshold: Integer (optional; default system threshold)
- createdAt: String (ISO timestamp)
- updatedAt: String (ISO timestamp)

Notes:
- `Active` products are sellable. `LowStock` is informational and should trigger admin notification.

### CartOrder

- technicalId: String (internal UUID)
- orderId: String (business id)
- customerId: String (links to User.userId; optional for guest checkouts)
- items: Array of { productId: String, quantity: Integer, unitPrice: Number }
- subtotal: Number
- tax: Number
- total: Number
- currency: String (ISO code)
- status: String (Cart | PendingPayment | PaymentConfirmed | StockReserved | Confirmed | Fulfillment | Shipped | Completed | Cancelled)
- createdAt: String (ISO timestamp)
- updatedAt: String (ISO timestamp)

Notes:
- Status progression is event‑driven. Stock reservation is performed after payment confirmation (see rationale below).

---

## 3. Design Decisions / Rationale (updated)

- Stock is reserved/decremented at the point of payment confirmation (PaymentConfirmed event), not when adding items to cart. Rationale: prevents holding inventory for abandoned carts and aligns stock changes with actual committed purchases. Systems that require immediate reservation can be configured to reserve on checkout — see optional modes.
- Payment is treated as an external event: the payment gateway posts results (PaymentConfirmed or PaymentFailed) to the system which drives processors.
- POST endpoints create/persist entities and emit creation events. External systems send event callbacks (payment, shipment) to designated event endpoints.
- Processors that modify multiple entities (e.g., decrementing stock for many items) must operate atomically where possible or implement compensation/rollback strategies when atomicity is not available.

---

## 4. Entity Workflows (updated)

### User workflow

- States: Created -> PendingValidation -> (Active | NeedsReview) -> Blocked

State diagram (conceptual):

Created -> PendingValidation (automatic validation)
PendingValidation -> Active (if all validations pass)
PendingValidation -> NeedsReview (if duplicates or suspicious data)
NeedsReview -> Active (Admin approves)
NeedsReview -> Blocked (Admin blocks)
Active -> Blocked (Admin blocks)

Processors/criteria:
- ValidateUserProcessor: validate email format, normalize, basic checks
- DuplicateFoundCriterion: detect duplicate email/userId
- AdminApproveProcessor: manual approval to Active
- AdminBlockProcessor: manual block

Pseudocode (ValidateUserProcessor):
```
process(user):
  if not validEmail(user.email):
    user.status = "PendingValidation"
    emit UserValidationFailedEvent(user.technicalId, reason="invalid-email")
    persist(user)
    return
  if existsUserWithEmail(user.email) and not sameTechnicalId(user):
    user.status = "NeedsReview"
    emit DuplicateFoundEvent(user.technicalId, conflictingTechnicalId)
    persist(user)
    return
  user.status = "Active"
  emit UserActivatedEvent(user.technicalId)
  persist(user)
```

### Product workflow

- States: Created -> PendingValidation -> (Active | NeedsReview | Inactive)
- Active products can transition to LowStock automatically when threshold crossed.

State diagram (conceptual):

Created -> PendingValidation (automatic)
PendingValidation -> Active (if valid)
PendingValidation -> NeedsReview (missing/invalid fields)
Active -> LowStock (if stockQuantity <= threshold)
LowStock -> Active (when replenished)
Active -> Inactive (manual deactivation)

Processors/criteria:
- ValidateProductProcessor: validate price>0, sku present
- MissingFieldsCriterion: flag if required fields missing
- StockThresholdCriterion: returns true when stockQuantity <= lowStockThreshold
- StockAdjustmentProcessor: decrement or increment stock on reservation or restock
- NotifyAdminProcessor: send low stock alert

Pseudocode (ValidateProductProcessor):
```
process(product):
  if product.price == null or product.price <= 0:
    product.status = "PendingValidation"
    emit ProductValidationFailedEvent(product.technicalId, reason="invalid-price")
    persist(product)
    return
  if skuExists(product.sku) and not sameTechnicalId(product):
    product.status = "NeedsReview"
    emit DuplicateSkuEvent(product.technicalId, conflictingTechnicalId)
    persist(product)
    return
  product.status = "Active"
  emit ProductActivatedEvent(product.technicalId)
  persist(product)
```

Pseudocode (StockThresholdCriterion):
```
evaluate(product):
  return product.stockQuantity <= (product.lowStockThreshold or SYSTEM_DEFAULT_LOW_STOCK)
```

### CartOrder workflow (updated and unified)

- Canonical states: Cart -> PendingPayment -> PaymentConfirmed -> StockReserved -> Confirmed -> Fulfillment -> Shipped -> Completed
- Cancellation: can occur from PendingPayment (payment timeout or failure), from PaymentConfirmed/StockReserved (if reservation fails), or from admin action.

State diagram (conceptual):

Cart -> PendingPayment (CheckoutProcessor) [manual action by customer] 
PendingPayment -> PaymentConfirmed (PaymentConfirmed event from payment gateway)
PaymentConfirmed -> StockReserved (ReserveStockProcessor)  
StockReserved -> Confirmed (if reservation succeeded)
Confirmed -> Fulfillment (create fulfillment task)
Fulfillment -> Shipped (manual or fulfillment service posts shipped event)
Shipped -> Completed (delivery confirmed)
PendingPayment -> Cancelled (PaymentFailed event or timeout)
Any state -> Cancelled (admin triggered or unrecoverable failure)

Processors/criteria:
- CalculateTotalsProcessor: compute subtotal/taxes/total on item change
- CheckoutProcessor: validate cart contents, availability check (optional), prepare for payment
- ReserveStockProcessor: reserve/decrement stock after PaymentConfirmed — atomic or compensated
- PaymentConfirmedCriterion: triggered by external payment success event
- PaymentFailedCriterion: triggered by external payment failure event
- NotifyCustomerProcessor: send emails/SMS for status changes
- FulfillmentCreateProcessor: create fulfillment task on Confirmed

Pseudocode (CalculateTotalsProcessor):
```
process(cartOrder):
  subtotal = sum(item.quantity * item.unitPrice for item in cartOrder.items)
  tax = applyTaxRules(subtotal, cartOrder.currency, cartOrder.customerId)
  total = subtotal + tax
  cartOrder.subtotal = subtotal
  cartOrder.tax = tax
  cartOrder.total = total
  persist(cartOrder)
  emit CartTotalsUpdatedEvent(cartOrder.technicalId)
```

Pseudocode (CheckoutProcessor):
```
process(cartOrder):
  if cartOrder.items is empty:
    reject checkout; emit CheckoutValidationFailedEvent
  # optional pre‑reservation check (non‑committal)
  if any item.quantity > product.stockQuantity:
    emit CheckoutValidationFailedEvent(reason="insufficient_stock")
    return
  cartOrder.status = "PendingPayment"
  persist(cartOrder)
  emit CheckoutInitiatedEvent(cartOrder.technicalId)
```

Pseudocode (ReserveStockProcessor) — invoked after PaymentConfirmed event:
```
process(paymentEvent):
  order = loadOrder(paymentEvent.orderTechnicalId)
  # ensure idempotency: if order.status in ["StockReserved", "Confirmed", "Fulfillment", "Shipped", "Completed"] -> ignore
  if order.status != "PaymentConfirmed":
    # either already processed or not in right state
    return

  # Attempt atomic reservation (DB transaction or distributed lock)
  begin transaction
    for item in order.items:
      product = loadProductForUpdate(item.productId)
      if product.stockQuantity < item.quantity:
        rollback transaction
        order.status = "Cancelled"
        persist(order)
        emit StockInsufficientEvent(order.technicalId, item.productId)
        return
    for item in order.items:
      product.stockQuantity -= item.quantity
      persist(product)
    order.status = "StockReserved"
    persist(order)
  commit transaction

  emit StockReservedEvent(order.technicalId)
  # move to Confirmed (ready for fulfillment) or let a separate processor transition
  order.status = "Confirmed"
  persist(order)
  emit OrderConfirmedEvent(order.technicalId)
```

Notes:
- ReserveStockProcessor must be idempotent and use row‑level locks / transactions to avoid overselling.
- If your operations require holding stock earlier, configure a policy to reserve on checkout instead of payment confirmation and adjust processors accordingly.

---

## 5. Events and Criteria (summary)

Key events emitted and consumed:
- UserActivatedEvent, UserValidationFailedEvent, DuplicateFoundEvent
- ProductActivatedEvent, ProductValidationFailedEvent, DuplicateSkuEvent, LowStockEvent
- CartTotalsUpdatedEvent, CheckoutInitiatedEvent, PaymentConfirmedEvent, PaymentFailedEvent
- StockReservedEvent, StockInsufficientEvent, OrderConfirmedEvent, OrderCancelledEvent

Criteria examples:
- UserValidCriterion: true when ValidateUserProcessor passed
- PaymentConfirmedCriterion: true when PaymentConfirmedEvent is received for the order
- StockThresholdCriterion: true when product stock at or below threshold

---

## 6. API Endpoints and Rules (updated)

Design rules:
- POST endpoints persist an entity and emit a creation event. Response contains only { "technicalId": "..." }.
- GET /{entity}/{technicalId} returns the full persisted entity including computed fields and current status.
- External systems should post callback/events via dedicated endpoints (see below), not by directly mutating core entities.
- No GET by search/conditions unless explicitly requested.

Endpoints (examples):

- POST /users
  - Request body: full User business payload
  - Response: { "technicalId": "string" }
  - Effect: persist user, emit UserCreatedEvent -> triggers ValidateUserProcessor

- GET /users/{technicalId}
  - Response: persisted User record

- POST /products
  - Request body: product payload
  - Response: { "technicalId": "string" }
  - Effect: persist product, emit ProductCreatedEvent -> triggers ValidateProductProcessor

- GET /products/{technicalId}
  - Response: persisted Product record

- POST /orders
  - Request body: order/cart payload. If the payload includes explicit intent to checkout (e.g., action: "checkout"), system treats it as Checkout initiation and may set status to PendingPayment; otherwise it creates/updates a Cart.
  - Response: { "technicalId": "string" }
  - Effect: persist order/cart, emit CartCreated or CartUpdated Event; if checkout, emit CheckoutInitiatedEvent

- GET /orders/{technicalId}
  - Response: persisted CartOrder record with totals and status

- POST /orders/{technicalId}/events
  - Used by external systems to post events related to the order (Payments, Shipment updates, Fulfillment results). Example payloads:
    - Payment result: { "type": "PaymentResult", "status": "Success" | "Failure", "gatewayReference": "..." }
    - Shipment update: { "type": "ShipmentUpdate", "status": "Shipped" | "Delivered", ... }
  - Effect: maps to event processing in Cyoda (PaymentConfirmedCriterion, etc.)

Notes on payment integration:
- Payment gateway should call POST /orders/{technicalId}/events with the payment result. The system will map this to PaymentConfirmedEvent or PaymentFailedEvent.

---

## 7. Processors — Summary and Responsibilities

- ValidateUserProcessor: field validation, normalization, duplicate checks
- ValidateProductProcessor: price/sku validation
- CalculateTotalsProcessor: recompute subtotal/tax/total on item changes
- CheckoutProcessor: pre‑checkout validation and set PendingPayment
- ReserveStockProcessor: perform atomic reservation/decrement on PaymentConfirmed
- StockAdjustmentProcessor: manual or automatic restock
- NotifyCustomerProcessor: enqueue notifications (email/SMS)
- FulfillmentCreateProcessor: create tasks for fulfillment systems

Processor implementation notes:
- Must be idempotent (events may be retried).
- Use transactions and optimistic/pessimistic locking for multi‑entity changes (stock adjustments).
- Emit clear domain events for downstream systems to react to (notifications, analytics, fulfillment).

---

## 8. Example Flows (concise)

1) Guest checkout, success:
- Customer creates cart (POST /orders) -> technicalId returned
- Customer posts checkout action (or POST with action=checkout) -> system sets status PendingPayment and returns
- Payment gateway posts PaymentConfirmed to /orders/{technicalId}/events
- ReserveStockProcessor runs, decrements stock (transactional), emits StockReservedEvent and OrderConfirmedEvent
- FulfillmentCreateProcessor creates fulfillment task -> status Fulfillment
- Fulfillment marks Shipped -> Shipped, eventually Completed

2) Payment success but insufficient stock (rare race):
- PaymentConfirmed received
- ReserveStockProcessor finds insufficient stock -> cancels order, emits StockInsufficientEvent and OrderCancelledEvent
- NotifyCustomerProcessor informs customer and operations

---

## 9. Questions / Options to Confirm

- Do you want an explicit ImportJob entity for bulk CSV/JSON imports (recommended)? This allows tracking import status, row‑level errors, and retry.
- Do you want to support guest checkout (customerId optional) or require registered users only?
- Reserve policy: The default above reserves on payment confirmation. Do you prefer reserve-on‑checkout instead (tradeoff: risk of holding inventory for abandoned carts)?
- Taxes / discounts / promotions: need business rules (region based taxes, per‑customer discounts, coupon codes).

---

## 10. Appendix — Mapping of previous draft changes

- Resolved inconsistent use of ReserveStockProcessor: it now runs after PaymentConfirmed and performs atomic reservation. The order state transitions were clarified so that PaymentConfirmed event triggers stock reservation and then Confirmed.
- Standardised status names across entities and processors.
- Added explicit endpoint for external events (/orders/{technicalId}/events).


If you confirm the choices above (especially reserve policy and guest checkout), I will expand the document with expanded processors, additional entities (ImportJob, Payment, Inventory, etc.), and full sequence diagrams as needed.