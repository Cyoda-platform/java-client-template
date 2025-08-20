# Functional Requirements

Last updated: 2025-08-20

This document defines the entities, workflows, processors/criteria, pseudo-code examples, and API design rules for the prototype e-commerce domain. It consolidates and clarifies state strings and processing behavior so implementation can be Cyoda / EDA ready.

---

## 1. Scope and Summary

- Maximum of 5 core entities: Product, User, Address, Cart, Order.
- Event-driven processors orchestrate transitions and validations.
- API endpoints follow the rule: POST that creates an entity returns only { "technicalId" }.
- Carts and Orders trigger asynchronous processing (Cyoda processors / EDA events).

---

## 2. Entity Definitions

All IDs called "productId", "userId", etc. are business-facing IDs. Every persisted record also receives a technicalId (internal unique id returned on creation endpoints).

### Product
- productId: String (business id / SKU)
- name: String (display name)
- description: String (marketing text)
- price: Number (unit price, >= 0)
- inventory: Number (available quantity, >= 0)
- active: Boolean (sellable)
- reserved: Number (optional, quantity reserved for initiated checkouts)

Notes: inventory represents immediately available stock. `reserved` represents quantity held by checkout flows. Processors must update both atomically to prevent oversell.

### User
- userId: String (business id)
- email: String (contact/login)
- name: String (display name)
- status: String (identity state)
  - Exact allowed values (IdentityFlow): ANONYMOUS, REGISTERED, VERIFIED, LOGGED_IN
- createdAt: Timestamp
- updatedAt: Timestamp

Notes: The system allows guest carts where cart.userId may be null. At order creation time, an implementation choice is to either keep the order linked to a null user (guest order) or create a lightweight user record; see "Policies" later.

### Address
- addressId: String (business id)
- userId: String (owner)
- line1: String
- line2: String (optional)
- city: String
- region: String
- postalCode: String
- country: String (ISO 2-letter)
- type: String (shipping | billing)
- primary: Boolean

Notes: Setting an address primary toggles other addresses of the same type for that user.

### Cart
- cartId: String (business id)
- userId: String | null (nullable for guest)
- items: Array of { itemId: String, productId: String, sku?: String, qty: Number, priceAtAdd: Number }
- subtotal: Number
- shippingEstimate: Number
- total: Number
- status: String (Cart status)
  - Exact allowed values: OPEN, CHECKOUT_INITIATED, CHECKED_OUT, ABANDONED
- createdAt: Timestamp
- updatedAt: Timestamp
- expiresAt: Timestamp (optional, for TTL-based abandonment)

Notes: items[].itemId is an internal id for item-level operations (PATCH/DELETE). priceAtAdd is used for historical pricing on the cart item.

### Order
- orderId: String (business id)
- technicalId: String (system id returned from POST)
- userId: String | null
- cartId: String
- items: Array of { productId: String, qty: Number, price: Number }
- subtotal: Number
- shipping: Number
- total: Number
- shippingAddressId: String
- billingAddressId: String
- paymentStatus: String
  - Exact allowed values: PAYMENT_PENDING, PAID
- fulfillmentStatus: String
  - Exact allowed values: CREATED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, REFUNDED
- createdAt: Timestamp
- updatedAt: Timestamp

Notes: Order lifecycle uses the above exact strings. The system separates paymentStatus and fulfillmentStatus for clarity.

---

## 3. Workflows & State Machines

Diagrams below use the exact string values defined for statuses.

### Cart workflow (CartFlow)
- Initial state: on persistence, cart is created in OPEN state.
- Manual operations: addItem, updateItem, removeItem while state is OPEN.
- Manual: beginCheckout -> transition to CHECKOUT_INITIATED (validates addresses and inventory; reserves inventory)
- Automatic: on payment success -> CHECKED_OUT (emit Order creation event and finalize inventory)
- Automatic: cart TTL expiry -> ABANDONED (release reserved inventory)
- Manual: checkout cancelled -> OPEN (release reservations)

Mermaid (Cart):

```mermaid
stateDiagram-v2
    [*] --> "OPEN"
    "OPEN" --> "CHECKOUT_INITIATED" : BeginCheckoutProcessor (manual)
    "CHECKOUT_INITIATED" --> "CHECKED_OUT" : PaymentConfirmedProcessor (automatic)
    "CHECKOUT_INITIATED" --> "OPEN" : CheckoutCancelledProcessor (manual)
    "OPEN" --> "ABANDONED" : CartExpirationProcessor (automatic)
    "CHECKED_OUT" --> [*]
    "ABANDONED" --> [*]
```

Cart processors / criteria:
- Processors: BeginCheckoutProcessor, PaymentConfirmedProcessor, CartExpirationProcessor, CheckoutCancelledProcessor
- Criteria: InventoryAvailableCriterion, AddressesPresentCriterion
- Important behaviors:
  - BeginCheckoutProcessor validates criteria and atomically reserves inventory (increment product.reserved and decrement available inventory as appropriate in a safe transactional manner or via events).
  - PaymentConfirmedProcessor finalizes the cart, converts it to an Order creation event, and adjusts inventory/reservations accordingly.
  - CartExpirationProcessor and CheckoutCancelledProcessor release reservations.

### Identity workflow (IdentityFlow)
- On persist: a newly created user can start as ANONYMOUS (if a lightweight/guest user) or REGISTERED.
- Manual: register -> REGISTERED
- Manual: verifyEmail -> VERIFIED
- Manual: login -> LOGGED_IN
- Automatic: inactivity cleanup may revert/log out users (optional maintenance task)

Mermaid (Identity):

```mermaid
stateDiagram-v2
    [*] --> "ANONYMOUS"
    "ANONYMOUS" --> "REGISTERED" : RegisterProcessor (manual)
    "REGISTERED" --> "VERIFIED" : VerifyEmailProcessor (manual)
    "VERIFIED" --> "LOGGED_IN" : LoginProcessor (manual)
    "LOGGED_IN" --> "ANONYMOUS" : InactivityCleanupProcessor (automatic)
    "REGISTERED" --> [*]
```

Identity processors / criteria:
- Processors: RegisterProcessor, VerifyEmailProcessor, LoginProcessor, InactivityCleanupProcessor
- Criteria: UniqueEmailCriterion, EmailFormatCriterion
- Behavior: RegisterProcessor enforces UniqueEmailCriterion and EmailFormatCriterion; VerifyEmailProcessor sets status to VERIFIED.

### Order lifecycle (OrderLifecycle)
- Event: Order persisted -> CREATED
- Automatic: InitiatePaymentProcessor -> PAYMENT_PENDING (when payment requested)
- Automatic: on payment success -> PAID
- Automatic/manual: after PAID -> CONFIRMED -> SHIPPED -> DELIVERED
- Manual/automatic: cancellations/refunds -> CANCELLED / REFUNDED

Mermaid (Order):

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "PAYMENT_PENDING" : InitiatePaymentProcessor (automatic)
    "PAYMENT_PENDING" --> "PAID" : PaymentSuccessProcessor (automatic)
    "PAYMENT_PENDING" --> "CANCELLED" : PaymentFailureProcessor (automatic)
    "PAID" --> "CONFIRMED" : ConfirmOrderProcessor (automatic)
    "CONFIRMED" --> "SHIPPED" : FulfillmentProcessor (automatic)
    "SHIPPED" --> "DELIVERED" : DeliveryConfirmedProcessor (automatic)
    "PAID" --> "REFUNDED" : RefundProcessor (manual)
    "CANCELLED" --> [*]
    "DELIVERED" --> [*]
    "REFUNDED" --> [*]
```

Order processors / criteria:
- Processors: InitiatePaymentProcessor, PaymentSuccessProcessor, ConfirmOrderProcessor, FulfillmentProcessor, RefundProcessor
- Criteria: PaymentMethodValidCriterion, InventoryReservedCriterion
- Behavior:
  - InitiatePaymentProcessor must validate the payment method (PaymentMethodValidCriterion). If invalid, the processor fails the change (ValidationError). If valid, set paymentStatus = PAYMENT_PENDING and emit the payment request.
  - PaymentSuccessProcessor sets paymentStatus = PAID and triggers ConfirmOrderProcessor.

### Product lifecycle (simple)
- On persist: product.active is respected when exposing to catalog.
- Automatic: when inventory reaches 0 (available minus reserved = 0) system may notify/product-marketing or set active=false per configuration.

### Address workflow
- On persist: Address is validated (AddressesPresentCriterion used during checkout).
- Manual: set primary toggles other addresses' primary flags for same user and type.

---

## 4. Processor / Criterion Pseudo-code (concise, corrected logic)

BeginCheckoutProcessor

```java
class BeginCheckoutProcessor {
  process(cart) {
    if (!InventoryAvailableCriterion.check(cart)) throw ValidationError("Insufficient inventory")
    if (!AddressesPresentCriterion.check(cart)) throw ValidationError("Addresses missing")
    reserveInventory(cart.items) // atomically mark reserved quantities on Product
    cart.status = "CHECKOUT_INITIATED"
    emit CartUpdatedEvent(cart)
  }
}
```

PaymentConfirmedProcessor

```java
class PaymentConfirmedProcessor {
  process(cart, paymentResult) {
    if (paymentResult.success) {
      cart.status = "CHECKED_OUT"
      emit OrderCreationRequestedEvent(cart)
      // inventory reservations are consumed/finalized by downstream order processors
    } else {
      // return cart to OPEN and release reservations
      cart.status = "OPEN"
      releaseReservedInventory(cart.items)
      emit CartUpdatedEvent(cart)
    }
  }
}
```

InitiatePaymentProcessor

```java
class InitiatePaymentProcessor {
  process(order) {
    if (!PaymentMethodValidCriterion.check(order)) {
      throw new ValidationError("Invalid payment method")
    }
    order.paymentStatus = "PAYMENT_PENDING"
    emit PaymentRequestEvent(order)
  }
}
```

PaymentSuccessProcessor

```java
class PaymentSuccessProcessor {
  process(order, paymentResult) {
    if (!paymentResult.success) throw new ValidationError("Unexpected payment result")
    order.paymentStatus = "PAID"
    emit OrderConfirmedEvent(order)
  }
}
```

InventoryAvailableCriterion

```java
class InventoryAvailableCriterion {
  static check(cart) {
    for (item in cart.items) {
      product = loadProduct(item.productId)
      available = product.inventory - (product.reserved || 0)
      if (available < item.qty) return false
    }
    return true
  }
}
```

Notes on inventory semantics: Processors should coordinate to ensure correctness under concurrency. Recommended approach is using optimistic updates with compare-and-swap, database transactions that lock rows, or separate reservation service.

---

## 5. API Endpoints Design Rules and Core Paths

Rules
- Every POST that creates an entity returns only { "technicalId" }.
- GET endpoints return the full stored resource as persisted (queried by technicalId or by business id where appropriate).
- Mutations that change state (e.g., checkout begin, payment callbacks) are expressed as POSTs to intent/command endpoints that trigger processors.
- Cart and Order POSTs/commands trigger Cyoda/EDA processing.

Core endpoints (recommended)

1) Users
- POST /users/register
  - Request: { userId, email, name, status } (server assigns technicalId)
  - Response: { technicalId }
- GET /users/technicalId/{technicalId}
- PATCH /users/technicalId/{technicalId} (update name, status transitions performed via processors)

2) Products
- POST /products
  - Request: { productId, name, description, price, inventory, active }
  - Response: { technicalId }
- GET /products
- GET /products/{productId}

3) Addresses
- POST /addresses
  - Request: { addressId, userId, line1, city, region, postalCode, country, type, primary }
  - Response: { technicalId }
- GET /addresses/{addressId}
- PATCH /addresses/{addressId} (toggle primary, update fields)

4) Carts
- POST /carts
  - Create a cart (guest carts allowed: userId may be null)
  - Request: { cartId, userId|null, items:[], subtotal, total, status:"OPEN" }
  - Response: { technicalId }
- POST /carts/{cartId}/items
  - Add item: body { productId, qty, priceAtAdd }
  - Response: { technicalId }
  - Side effect: attempts quick validation (InventoryAvailableCriterion) and may reserve inventory only during beginCheckout; adding an item should not permanently reserve inventory unless policy decides otherwise.
- PATCH /carts/{cartId}/items/{itemId}
  - Update qty or priceAtAdd
- DELETE /carts/{cartId}/items/{itemId}
  - Remove item
- POST /carts/{cartId}/beginCheckout
  - Triggers BeginCheckoutProcessor (validates addresses, inventory & reserves inventory)
  - Response: { technicalId: cartTechnicalId } (or 202 Accepted) and async events
- POST /carts/{cartId}/cancelCheckout
  - Triggers CheckoutCancelledProcessor

5) Orders
- POST /orders
  - Create order (usually triggered by cart checkout flow; client-facing POST allowed for direct placement)
  - Request: { orderId, userId|null, cartId, items, subtotal, shipping, total, shippingAddressId, billingAddressId, paymentStatus:"PAYMENT_PENDING", fulfillmentStatus:"CREATED" }
  - Response: { technicalId }
- GET /orders/technicalId/{technicalId}
- POST /payments/webhook
  - External payment gateway posts results; maps to PaymentSuccessProcessor/PaymentFailureProcessor and triggers Order transitions.

Extra endpoints (convenience for UI)
- PATCH /carts/{cartId} (update shippingEstimate, addresses, etc.)
- GET /carts/{cartId}/summary
- POST /carts/{cartId}/checkout/guest-to-user
  - Optional: convert a guest cart to a registered user (creates a lightweight user record and associates the cart)

---

## 6. Validations and Business Rules

- Price >= 0, inventory >= 0 enforced by processors and write-side validation.
- Cart add/update triggers InventoryAvailableCriterion when required (for example, at beginCheckout). Implementations may choose to do pre-checks at add-time for better UX, but final enforcement must be at beginCheckout to guarantee correctness.
- beginCheckout reserves inventory. If reservation fails, BeginCheckoutProcessor throws ValidationError and cart remains OPEN.
- Guest checkout: carts may have userId = null. Policy adopted here: allow guest checkout; do not automatically create a full Registered user unless the customer explicitly chooses to register. Optionally, the system can create a lightweight ANONYMOUS user record to link orders for tracking; this is implementation-specific but should be explicit in the flow.
- On Cart TTL expiry (expiresAt), CartExpirationProcessor sets status = ABANDONED and releases reservations.
- Checkout cancel must release reservations and transition status back to OPEN.
- Payment flows:
  - InitiatePaymentProcessor validates payment method; if invalid -> reject and keep order in CREATED or transition per local policy (here we prefer to fail fast and keep CREATED until valid payment method provided).
  - On payment success -> set PAID -> then CONFIRMED -> proceed to fulfillment.

---

## 7. Observability, Events and Error Handling

- All processors emit domain events for state transitions (CartUpdatedEvent, OrderCreationRequestedEvent, PaymentRequestEvent, OrderConfirmedEvent, etc.).
- Retries: processors handling external dependencies (payment, fulfillment) must implement retry/backoff and idempotency.
- Validation errors should provide clear error codes so clients can surface fields to users (e.g., INSUFFICIENT_INVENTORY, MISSING_ADDRESSES, INVALID_PAYMENT_METHOD).

---

## 8. Answers & Decisions (from previously posed questions)
1) Exact state string values (adopted as canonical):
   - IdentityFlow: ANONYMOUS, REGISTERED, VERIFIED, LOGGED_IN
   - Cart status: OPEN, CHECKOUT_INITIATED, CHECKED_OUT, ABANDONED
   - Order lifecycle states:
     - paymentStatus: PAYMENT_PENDING, PAID
     - fulfillmentStatus: CREATED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, REFUNDED

2) Guest checkout policy (decided):
   - Guest checkout is allowed (cart.userId may be null).
   - By default the system will not auto-create a full REGISTERED user at checkout. Implementations may create a lightweight ANONYMOUS user record to associate orders for tracking and analytics, but this is opt-in and must be explicit in API flows (e.g., POST /carts/{cartId}/checkout/guest-to-user).

3) Additional API paths (decided):
   - Yes. Add PATCH/DELETE endpoints for cart items and a POST /carts/{cartId}/beginCheckout command endpoint (recommended). Add POST /payments/webhook for payment provider callbacks.

---

## 9. Implementation Notes & Next Steps

- Finalize id format (technicalId generation strategy).
- Decide whether inventory reservations are stored on Product (product.reserved) or in a dedicated reservation service/aggregate.
- Implement idempotency for critical endpoints: beginCheckout, payment webhooks, and order creation.
- Provide concrete error code list and mapping to HTTP status codes (e.g., 400 for validation errors, 409 for concurrent inventory conflicts).
- If you prefer different exact state string values or a different guest user policy, reply with choices and this document will be updated.

---

Appendix: Example quick reference pseudo JSONs

- Create cart request example:

```json
{ "cartId":"c1","userId":null,"items":[], "subtotal":0, "total":0, "status":"OPEN" }
```

- Begin checkout command:

POST /carts/c1/beginCheckout -> triggers BeginCheckoutProcessor which reserves inventory and sets cart.status = "CHECKOUT_INITIATED" (or throws ValidationError)

- Payment webhook example:

POST /payments/webhook
{ "orderTechnicalId": "tc_order_1", "success": true }

This completes the updated functional requirements.
