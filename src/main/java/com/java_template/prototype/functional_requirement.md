### 1. Entity Definitions
(Max 5 entities as specified: Product, User, Address, Cart, Order)

```
Product:
- productId: String (business id / SKU)
- name: String (display name)
- description: String (marketing text)
- price: Number (unit price)
- inventory: Number (available quantity)
- active: Boolean (sellable)

User:
- userId: String (business id)
- email: String (contact/login)
- name: String (display name)
- status: String (identity state e.g. ANONYMOUS REGISTERED VERIFIED LOGGED_IN)

Address:
- addressId: String (business id)
- userId: String (owner)
- line1: String
- line2: String
- city: String
- region: String
- postalCode: String
- country: String
- type: String (shipping or billing)
- primary: Boolean

Cart:
- cartId: String (business id)
- userId: String (nullable for guest)
- items: Array of { productId: String, sku: String, qty: Number, priceAtAdd: Number }
- subtotal: Number
- shippingEstimate: Number
- total: Number
- status: String (OPEN CHECKOUT_INITIATED CHECKED_OUT ABANDONED)

Order:
- orderId: String (business id)
- userId: String
- cartId: String
- items: Array of { productId: String, qty: Number, price: Number }
- subtotal: Number
- shipping: Number
- total: Number
- shippingAddressId: String
- billingAddressId: String
- paymentStatus: String (e.g. PAYMENT_PENDING PAID)
- fulfillmentStatus: String (e.g. CONFIRMED SHIPPED DELIVERED CANCELLED REFUNDED)
```

---

### 2. Entity workflows

Cart workflow (CartFlow)
1. Event: Cart persisted -> START at OPEN
2. Manual: addItem/updateItem/removeItem keep OPEN
3. Manual: beginCheckout -> transition to CHECKOUT_INITIATED (validate addresses, inventory)
4. Automatic: on payment success -> CHECKED_OUT (emit Order creation event)
5. Automatic: cart TTL expiry -> ABANDONED

```mermaid
stateDiagram-v2
    [*] --> "OPEN"
    "OPEN" --> "CHECKOUT_INITIATED" : BeginCheckoutProcessor, manual
    "CHECKOUT_INITIATED" --> "CHECKED_OUT" : PaymentConfirmedProcessor, automatic
    "CHECKOUT_INITIATED" --> "OPEN" : CheckoutCancelledProcessor, manual
    "OPEN" --> "ABANDONED" : CartExpirationProcessor, automatic
    "CHECKED_OUT" --> [*]
    "ABANDONED" --> [*]
```

Cart processors / criteria:
- Processors: BeginCheckoutProcessor, PaymentConfirmedProcessor, CartExpirationProcessor, CheckoutCancelledProcessor
- Criteria: InventoryAvailableCriterion, AddressesPresentCriterion
- Pseudo behavior: BeginCheckoutProcessor validates criteria and reserves inventory; PaymentConfirmedProcessor converts cart -> Order event.

Identity workflow (IdentityFlow)
1. Event: User persisted -> if status ANONYMOUS then REGISTERED
2. Manual: verifyEmail -> VERIFIED
3. Manual: login -> LOGGED_IN
4. Automatic: inactivity -> revert to ANONYMOUS (optional cleanup)

```mermaid
stateDiagram-v2
    [*] --> "ANONYMOUS"
    "ANONYMOUS" --> "REGISTERED" : RegisterProcessor, manual
    "REGISTERED" --> "VERIFIED" : VerifyEmailProcessor, manual
    "VERIFIED" --> "LOGGED_IN" : LoginProcessor, manual
    "LOGGED_IN" --> "ANONYMOUS" : InactivityCleanupProcessor, automatic
    "REGISTERED" --> [*]
```

Identity processors / criteria:
- Processors: RegisterProcessor, VerifyEmailProcessor, LoginProcessor, InactivityCleanupProcessor
- Criteria: UniqueEmailCriterion, EmailFormatCriterion
- Pseudo behavior: RegisterProcessor enforces UniqueEmailCriterion; VerifyEmailProcessor marks status VERIFIED.

Order lifecycle (OrderLifecycle)
1. Event: Order persisted -> CREATED
2. Automatic: run PaymentProcessor -> PAYMENT_PENDING or PAID
3. Automatic/manual: after PAID -> CONFIRMED
4. Automatic: fulfillment -> SHIPPED -> DELIVERED
5. Manual/automatic: cancellations/refunds -> CANCELLED / REFUNDED

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "PAYMENT_PENDING" : InitiatePaymentProcessor, automatic
    "PAYMENT_PENDING" --> "PAID" : PaymentSuccessProcessor, automatic
    "PAYMENT_PENDING" --> "CANCELLED" : PaymentFailureProcessor, automatic
    "PAID" --> "CONFIRMED" : ConfirmOrderProcessor, automatic
    "CONFIRMED" --> "SHIPPED" : FulfillmentProcessor, automatic
    "SHIPPED" --> "DELIVERED" : DeliveryConfirmedProcessor, automatic
    "PAID" --> "REFUNDED" : RefundProcessor, manual
    "CANCELLED" --> [*]
    "DELIVERED" --> [*]
    "REFUNDED" --> [*]
```

Order processors / criteria:
- Processors: InitiatePaymentProcessor, PaymentSuccessProcessor, ConfirmOrderProcessor, FulfillmentProcessor, RefundProcessor
- Criteria: PaymentMethodValidCriterion, InventoryReservedCriterion
- Pseudo behavior: InitiatePaymentProcessor sets PAYMENT_PENDING; PaymentSuccessProcessor sets PAID and triggers ConfirmOrderProcessor.

Product lifecycle (simple)
1. Event: Product persisted -> active state respected
2. Automatic: inventory==0 -> outreach mark/notification

Address workflow
1. Event: Address persisted -> validated (AddressesPresentCriterion)
2. Manual: set primary toggles other addresses' primary flags

---

### 3. Pseudo code for processor classes (concise examples)

BeginCheckoutProcessor
```
class BeginCheckoutProcessor {
  process(cart) {
    if (!InventoryAvailableCriterion.check(cart)) throw ValidationError
    if (!AddressesPresentCriterion.check(cart)) throw ValidationError
    reserveInventory(cart.items)
    cart.status = "CHECKOUT_INITIATED"
    emit cart updated event
  }
}
```

PaymentConfirmedProcessor
```
class PaymentConfirmedProcessor {
  process(cart, paymentResult) {
    if (paymentResult.success) {
      cart.status = "CHECKED_OUT"
      emit Order creation event with cart data
    } else {
      cart.status = "OPEN"
    }
  }
}
```

InitiatePaymentProcessor
```
class InitiatePaymentProcessor {
  process(order) {
    if (!PaymentMethodValidCriterion.check(order)) {
      order.paymentStatus = "PAYMENT_PENDING"
      emit payment request
    }
  }
}
```

PaymentSuccessProcessor
```
class PaymentSuccessProcessor {
  process(order) {
    order.paymentStatus = "PAID"
    emit order confirmed event
  }
}
```

InventoryAvailableCriterion (example)
```
class InventoryAvailableCriterion {
  static check(cart) {
    for item in cart.items:
      if product.inventory < item.qty return false
    return true
  }
}
```

---

### 4. API Endpoints Design Rules (Cyoda / EDA aligned)

Rules applied:
- Every POST that creates an entity returns only { technicalId }.
- GET endpoints return stored results (by technicalId).
- Cart and Order POSTs trigger Cyoda processing workflows automatically.

Endpoints (core Lovable UI mappings)

1) POST /users/register
- Request:
```json
{ "userId":"u123","email":"a@b.com","name":"Alice","status":"REGISTERED" }
```
- Response:
```json
{ "technicalId":"tc_abc123" }
```
- GET /users/technicalId/{technicalId} returns full User entity JSON.

2) POST /products
- Request:
```json
{ "productId":"p123","name":"Tshirt","description":"...","price":19.99,"inventory":50,"active":true }
```
- Response:
```json
{ "technicalId":"tc_prod_1" }
```
- GET /products returns product list; GET /products/{productId} returns product.

3) POST /addresses
- Request:
```json
{ "addressId":"a1","userId":"u123","line1":"1 St","city":"X","region":"Y","postalCode":"Z","country":"US","type":"shipping","primary":true }
```
- Response:
```json
{ "technicalId":"tc_addr_1" }
```

4) POST /carts
- Create cart event
- Request:
```json
{ "cartId":"c1","userId":"u123","items":[], "subtotal":0, "total":0, "status":"OPEN" }
```
- Response:
```json
{ "technicalId":"tc_cart_1" }
```
- POST /carts/{cartId}/items (add item) — request includes item; response { technicalId } and Cart workflow triggers validators/reservation.

5) POST /orders
- Request:
```json
{ "orderId":"o1","userId":"u123","cartId":"c1","items":[{"productId":"p123","qty":2,"price":19.99}], "subtotal":39.98,"shipping":5,"total":44.98,"shippingAddressId":"a1","billingAddressId":"a1","paymentStatus":"PAYMENT_PENDING","fulfillmentStatus":"CREATED" }
```
- Response:
```json
{ "technicalId":"tc_order_1" }
```
- GET /orders/technicalId/{technicalId} returns order lifecycle state and full order.

Notes / validations:
- Price >= 0, inventory >= 0 enforced by processors/criteria.
- Cart add/update triggers InventoryAvailableCriterion; beginCheckout triggers BeginCheckoutProcessor.
- Guest checkout: cart.userId can be null; at checkout IdentityFlow may create REGISTERED user or proceed as guest — confirm preference.

Questions for you
1) Confirm exact state string values to use for IdentityFlow, Cart status, and Order lifecycle (I used ANONYMOUS REGISTERED VERIFIED LOGGED_IN; OPEN CHECKOUT_INITIATED CHECKED_OUT ABANDONED; CREATED PAYMENT_PENDING PAID CONFIRMED SHIPPED DELIVERED CANCELLED REFUNDED). Are these exact?
2) Do you want guest checkout allowed (cart.userId nullable) and if so should we auto-create a lightweight User at checkout?
3) Any extra API paths the Lovable UI expects (e.g. PATCH /cart/items/:id) or should we keep POST/GET only as above?

Once you confirm the answers I will produce the final Cyoda-ready entities + workflows (with exact state strings fixed) and finalize the processor/criterion names for implementation.