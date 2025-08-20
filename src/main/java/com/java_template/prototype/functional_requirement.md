# Functional Requirements

Last updated: 2025-08-20

This document defines the current functional requirements, entity schemas, workflows, processors and API design rules for the prototype. It also records decisions about naming, idempotency and order numbering.

---

## Summary of decisions (answers to outstanding questions)

1) Field naming: Keep camelCase for JSON fields and entity properties (e.g., quantityAvailable, grandTotal, orderNumber).

2) orderNumber: Must be a sequential, human-friendly, unique identifier. Format: `ORD-{YYYYMMDD}-{NNNN}` where NNNN is a day-sequence (0001, 0002, ...). The system enforces uniqueness and monotonic sequence per day.

3) Idempotency: All mutating POST endpoints require a client-provided requestId. The service MUST treat duplicate requestId for the same operation and resource as idempotent and return the same result (technicalId and status). requestId is mandatory for:
   - any POST that creates or mutates entities (e.g., create product, create cart, add cart line, checkout identify, place order, start picking, mark sent).

---

## 1. Entity Definitions

All fields use camelCase JSON property names. Fields marked optional are not required on creation but may be set later.

Product
```
- technicalId: string (internal, e.g., "t_prod_001")
- sku: string (unique product code, business id)
- name: string (display name)
- description: string (optional)
- price: number (unit price, in decimal currency)
- quantityAvailable: integer (current stock, >= 0)
- category: string (optional)
- stockState: string (derived: ADDED | IN_STOCK | LOW_STOCK | OUT_OF_STOCK)
- lowStockThreshold: integer (optional, used to evaluate LOW_STOCK)
- createdAt: datetime
- updatedAt: datetime
```

Cart
```
- technicalId: string (internal, e.g., "t_cart_001")
- cartId: string (business id, optional alias)
- userId: string (optional, links User. technicalId format t_user_...)
- status: string (NEW | ACTIVE | CHECKING_OUT | CONVERTED)
- lines: array of { sku:string, name:string, unitPrice:number, qty:integer, lineTotal:number }
- totalItems: integer (sum of qty)
- grandTotal: number (sum of lineTotal)
- createdAt: datetime
- updatedAt: datetime
```
Notes:
- lineTotal = unitPrice * qty. unitPrice is captured at the time the line is added (price snapshot).
- status transitions are implemented by processors (see workflows).

User
```
- technicalId: string (internal, e.g., "t_user_001")
- userId: string (business id, optional)
- name: string
- email: string (unique)
- phone: string (optional)
- status: string (ANON | IDENTIFIED)
- createdAt: datetime
- updatedAt: datetime
```

Address
```
- technicalId: string (internal, e.g., "t_addr_001")
- addressId: string (business id, optional)
- userId: string (links User technicalId)
- line1: string
- line2: string (optional)
- city: string
- postcode: string
- country: string
- createdAt: datetime
- updatedAt: datetime
```

Order
```
- technicalId: string (internal, e.g., "t_order_001")
- orderId: string (business id, optional)
- orderNumber: string (unique human-friendly, e.g., ORD-20250820-0001)
- userId: string (technicalId of User)
- shippingAddressId: string (technicalId of Address)
- lines: array of { sku:string, name:string, unitPrice:number, qty:integer, lineTotal:number }
- totals: { items: integer, grand: number }
- status: string (WAITING_TO_FULFILL | PICKING | SENT)
- createdAt: datetime
- updatedAt: datetime
```

---

## 2. Events

The system emits domain events on key state changes. Examples:
- CartCreated (payload: cart technicalId)
- CartUpdated
- OrderRequested (emitted by CheckoutProcessor when cart is converted)
- OrderCreated
- StockInsufficient (details of failing SKUs)
- StockReserved (when inventory is decremented/reserved)
- LowStockDetected / ProductLowStockNotification
- OutOfStockDetected

Events are used to trigger asynchronous processors (e.g., placing an order from OrderRequested).

---

## 3. Workflows

Cart workflow (automatic events and explicit actions)

1. create cart -> status NEW. CreateCartProcessor runs and transitions to ACTIVE immediately after persistence.
2. add/update/remove lines (only allowed in ACTIVE) -> AddItemProcessor / UpdateLineProcessor / RemoveLineProcessor run, then RecalculateTotalsProcessor recalculates totals and persists the cart.
3. open checkout -> explicit action OpenCheckoutAction -> status CHECKING_OUT.
4. checkout -> CheckoutProcessor validates criteria (CartNotEmpty, LineQtyValid), transitions to CONVERTED, and emits OrderRequested event.

Mermaid diagram:

```mermaid
stateDiagram-v2
    [*] --> "NEW"
    "NEW" --> "ACTIVE" : CreateCartProcessor (automatic)
    "ACTIVE" --> "CHECKING_OUT" : OpenCheckoutAction (manual)
    "CHECKING_OUT" --> "CONVERTED" : CheckoutProcessor (automatic/transactional)
    "CONVERTED" --> [*]
```

Key processors and criteria for Cart:
- Processors: CreateCartProcessor, AddItemProcessor, UpdateLineProcessor, RemoveLineProcessor, RecalculateTotalsProcessor, CheckoutProcessor
- Criteria: CartNotEmptyCriterion (totalItems > 0), LineQtyValidCriterion (qty >= 1), EmailPresentCriterion (for identification flow)

User Identification workflow

1. Default user state is ANON.
2. On checkout identify, IdentifyProcessor validates form, then calls UpsertUserProcessor and CreateAddressProcessor to persist user and address. The user status is set to IDENTIFIED and the API returns a technicalId for user and address via events or synchronous response.

Mermaid diagram:

```mermaid
stateDiagram-v2
    [*] --> "ANON"
    "ANON" --> "IDENTIFIED" : IdentifyProcessor (manual)
    "IDENTIFIED" --> [*]
```

Processors: IdentifyProcessor, UpsertUserProcessor, CreateAddressProcessor

Order lifecycle

1. OrderRequested (produced by CheckoutProcessor when cart is converted) triggers the PlaceOrder flow.
2. PlaceOrderProcessor calls ValidateStockProcessor. If stock is sufficient, an Order snapshot is created with status WAITING_TO_FULFILL and OrderCreated emitted. ReserveStockProcessor then decrements quantityAvailable (atomic/transactional where possible) and emits StockReserved. If stock is insufficient, emit StockInsufficient and do not create the order.
3. Manual operations: START_PICKING -> PICKING, MARK_SENT -> SENT.

Mermaid diagram:

```mermaid
stateDiagram-v2
    [*] --> "WAITING_TO_FULFILL"
    "WAITING_TO_FULFILL" --> "PICKING" : StartPickingProcessor (manual)
    "PICKING" --> "SENT" : MarkSentProcessor (manual)
    "SENT" --> [*]
```

Processors: PlaceOrderProcessor, ValidateStockProcessor, ReserveStockProcessor, StartPickingProcessor, MarkSentProcessor
Criteria: StockAvailableCriterion, OrderValidCriterion

Product stock monitor (automatic)

On create or update of Product, StockCheckProcessor evaluates the derived stockState using thresholds and emits LowStock/OutOfStock events as appropriate. stockState values: ADDED, IN_STOCK, LOW_STOCK, OUT_OF_STOCK.

Mermaid diagram:

```mermaid
stateDiagram-v2
    [*] --> "ADDED"
    "ADDED" --> "IN_STOCK" : StockCheckProcessor (automatic)
    "IN_STOCK" --> "LOW_STOCK" : LowStockCriterion (automatic)
    "LOW_STOCK" --> "OUT_OF_STOCK" : OutOfStockCriterion (automatic)
    "OUT_OF_STOCK" --> [*]
```

Processors: StockCheckProcessor, NotifyLowStockProcessor
Criteria: LowStockCriterion, OutOfStockCriterion

---

## 4. Processor pseudocode (detailed)

- RecalculateTotalsProcessor.process(cart):
  - For each line: ensure unitPrice present; compute lineTotal = unitPrice * qty
  - totalItems = sum(qty)
  - grandTotal = sum(lineTotal)
  - updatedAt = now
  - persist cart (idempotent using requestId if provided) -> emits CartUpdated

- ValidateStockProcessor.process(cart):
  - failures = []
  - for each line in cart.lines:
      - product = loadProductBySku(line.sku)
      - if product == null -> add failure (SKU_NOT_FOUND)
      - else if product.quantityAvailable < line.qty -> add failure (INSUFFICIENT_STOCK)
  - if failures not empty:
      - emit StockInsufficient (details) and return failure
      - abort further processing
  - return success

- ReserveStockProcessor.process(order):
  - for each line in order.lines:
      - decrement product.quantityAvailable by line.qty (transactional where possible)
      - if decrement would make quantityAvailable < 0 -> rollback and emit StockInsufficient
  - persist product updates
  - emit StockReserved

- PlaceOrderProcessor.process(cart, userId, addressId):
  - call ValidateStockProcessor.process(cart)
  - if validation fails -> return error (StockInsufficient event emitted)
  - create order snapshot: copy cart.lines (use unitPrice snapshot), compute totals
  - set orderNumber = allocateSequentialOrderNumber() (see decision above)
  - set order.status = WAITING_TO_FULFILL
  - persist order -> emits OrderCreated
  - call ReserveStockProcessor.process(order)
  - return success with order technicalId

- IdentifyProcessor.process(form):
  - validate email presence (EmailPresentCriterion)
  - call UpsertUserProcessor: find by email -> update or create user; set status=IDENTIFIED
  - call CreateAddressProcessor: create address linked to user
  - persist changes and return technicalIds for user and address

---

## 5. API Endpoints & Design Rules

General rules
- All mutating POSTs require requestId for idempotency. The server stores (requestId, operation, technicalId) mapping for a duration appropriate for the system (e.g., 24-72 hours) and uses it to deduplicate repeated requests.
- All create POSTs return only { "technicalId": "<id>" } on success and trigger downstream asynchronous processing. Where synchronous data is available (e.g., Identify), the response may include an operation technicalId; clients should use GET to fetch newly created entities or listen for events.
- Use HTTP 409 for conflicts (e.g., duplicate business ids) and 422 for validation errors.
- Use camelCase for JSON.

Products
- POST /entity/Product
  Request example:
  ```json
  { "requestId":"r1","sku":"sku123","name":"Tee","price":19.99,"quantityAvailable":10 }
  ```
  Response:
  ```json
  { "technicalId":"t_prod_001" }
  ```
- GET /entity/Product/{technicalId} -> returns full Product JSON

Cart
- POST /cart
  ```json
  { "requestId":"r1","userId":"t_user_001" }
  ```
  Response:
  ```json
  { "technicalId":"t_cart_001" }
  ```
- GET /cart/{technicalId} -> returns full Cart
- POST /cart/{technicalId}/lines
  ```json
  { "requestId":"r2","sku":"sku123","qty":2 }
  ```
  Response:
  ```json
  { "technicalId":"t_op_123" }
  ```
Notes:
- Add/update/remove lines each require requestId. AddItemProcessor will snapshot the current product unitPrice and set line.unitPrice.
- Mutations are only allowed in ACTIVE carts. Attempts to mutate a cart in CHECKING_OUT or CONVERTED must be rejected (HTTP 409).

Identity / Checkout identify
- POST /checkout/identify
  ```json
  { "requestId":"r3","cartTechnicalId":"t_cart_001","name":"Alice","email":"a@x.com","phone":"","address":{"line1":"...","city":"...","postcode":"...","country":"..."} }
  ```
  Response:
  ```json
  { "technicalId":"t_ident_001" }
  ```
Notes:
- This operation upserts the User by email and creates an Address linked to the user. The IdentifyProcessor returns user and address technicalIds via emitted events and can be queried via GET.

Place Order
- POST /checkout/place-order
  ```json
  { "requestId":"r4","cartTechnicalId":"t_cart_001","userTechnicalId":"t_user_001","addressTechnicalId":"t_addr_001" }
  ```
  Response:
  ```json
  { "technicalId":"t_order_001" }
  ```
- GET /entity/Order/{technicalId} returns full Order including orderNumber and status
Notes:
- PlaceOrderProcessor enforces ValidateStock; if stock insufficient, API should return an error and StockInsufficient event will be emitted. On success an OrderCreated event is emitted and inventory is decremented (StockReserved).

Fulfillment (manual ops)
- POST /order/{technicalId}/start-picking
  Request: { "requestId":"r5" }
  Response: { "technicalId":"t_op_..." }
- POST /order/{technicalId}/mark-sent
  Request: { "requestId":"r6" }
  Response: { "technicalId":"t_op_..." }

---

## 6. Validation & Criteria

- CartNotEmptyCriterion: cart.totalItems > 0
- LineQtyValidCriterion: each line.qty >= 1
- EmailPresentCriterion: email present and RFC-compliant format
- StockAvailableCriterion: each product.quantityAvailable >= required qty
- OrderValidCriterion: order snapshot totals match cart totals and user/address exist

All criteria are enforced by processors and return explicit errors/events when failing.

---

## 7. Id formats and uniqueness

- technicalId: system assigned id for entities (prefix t_<type>_XXXX). Returned on create operations.
- business ids: sku, orderNumber, cartId, userId are business-level identifiers. SKUs are unique across products.
- orderNumber: sequential per day, unique. Generated in PlaceOrderProcessor via an atomic allocator.

---

## 8. Notes and rationale

- Idempotency using requestId prevents duplicate resource creation from client retries.
- Snapshotting prices on cart line add and on order creation avoids mid-flight price changes affecting existing carts/orders.
- ValidateStock is executed at place-order to ensure inventory availability. ReserveStock does a transactional decrement where possible.
- Events enable asynchronous, decoupled flows (e.g., fulfillment, notifications).



