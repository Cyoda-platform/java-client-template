### 1. Entity Definitions
```
Product:
- sku: string (unique product code)
- name: string (display name)
- description: string (optional)
- price: number (unit price)
- quantityAvailable: number (current stock)
- category: string (optional)

Cart:
- cartId: string (business id)
- userId: string (optional, links User)
- status: string (NEW -> ACTIVE -> CHECKING_OUT -> CONVERTED)
- lines: array of objects (sku:string, name:string, price:number, qty:number)
- totalItems: number
- grandTotal: number
- createdAt: datetime
- updatedAt: datetime

User:
- userId: string
- name: string
- email: string (unique)
- phone: string (optional)
- status: string (ANON -> IDENTIFIED)

Address:
- addressId: string
- userId: string
- line1: string
- city: string
- postcode: string
- country: string

Order:
- orderId: string
- orderNumber: string (unique)
- userId: string
- shippingAddressId: string
- lines: array of objects (sku:string, name:string, unitPrice:number, qty:number, lineTotal:number)
- totals: object (items:number, grand:number)
- status: string (WAITING_TO_FULFILL -> PICKING -> SENT)
- createdAt: datetime
```

### 2. Entity workflows

Cart workflow (automatic events on persistence; mutations are events)
1. create cart -> NEW then system moves to ACTIVE
2. add/update/remove lines (ACTIVE) -> recalc totals
3. open checkout -> CHECKING_OUT
4. checkout -> CONVERTED and emit OrderRequested event

```mermaid
stateDiagram-v2
    [*] --> "NEW"
    "NEW" --> "ACTIVE" : onCreateProcessor, automatic
    "ACTIVE" --> "CHECKING_OUT" : OpenCheckoutAction, manual
    "CHECKING_OUT" --> "CONVERTED" : CheckoutProcessor, automatic
    "CONVERTED" --> [*]
```

Processors: RecalculateTotalsProcessor, AddItemProcessor, CheckoutProcessor, CreateCartProcessor  
Criteria: CartNotEmptyCriterion, LineQtyValidCriterion

User Identification workflow
1. ANON (default)
2. submit checkout details -> upsert user + create address -> IDENTIFIED (return ids)

```mermaid
stateDiagram-v2
    [*] --> "ANON"
    "ANON" --> "IDENTIFIED" : IdentifyProcessor, manual
    "IDENTIFIED" --> [*]
```

Processors: IdentifyProcessor, UpsertUserProcessor, CreateAddressProcessor  
Criteria: EmailPresentCriterion

Order lifecycle
1. PLACE_ORDER (triggered by OrderRequested) -> create Order snapshot status WAITING_TO_FULFILL after stock validation
2. START_PICKING -> PICKING (manual)
3. MARK_SENT -> SENT (manual)

```mermaid
stateDiagram-v2
    [*] --> "WAITING_TO_FULFILL"
    "WAITING_TO_FULFILL" --> "PICKING" : StartPickingProcessor, manual
    "PICKING" --> "SENT" : MarkSentProcessor, manual
    "SENT" --> [*]
```

Processors: PlaceOrderProcessor, ValidateStockProcessor, ReserveStockProcessor, StartPickingProcessor, MarkSentProcessor  
Criteria: StockAvailableCriterion, OrderValidCriterion

Product stock monitor (automatic)
1. on create/update -> evaluate stock level and set derived flag/emit LowStock event

```mermaid
stateDiagram-v2
    [*] --> "ADDED"
    "ADDED" --> "IN_STOCK" : StockCheckProcessor, automatic
    "IN_STOCK" --> "LOW_STOCK" : LowStockCriterion, automatic
    "LOW_STOCK" --> "OUT_OF_STOCK" : OutOfStockCriterion, automatic
    "OUT_OF_STOCK" --> [*]
```

Processors: StockCheckProcessor, NotifyLowStockProcessor  
Criteria: LowStockCriterion, OutOfStockCriterion

### 3. Pseudo code for processor classes (short)

- RecalculateTotalsProcessor.process(cart):
  - sum qty and price, set totalItems, grandTotal, updatedAt
  - persist cart (emits CartUpdated)

- ValidateStockProcessor.process(cart):
  - for each line check product.quantityAvailable >= qty
  - if any fail emit StockInsufficient event with details and abort

- PlaceOrderProcessor.process(cart, userId, addressId):
  - call ValidateStockProcessor
  - snapshot order (create Order entity) -> persists Order (emits OrderCreated)
  - call ReserveStockProcessor to decrement product.quantityAvailable

- IdentifyProcessor.process(form):
  - UpsertUserProcessor: find by email or create user
  - CreateAddressProcessor: create address linked to user
  - mark user status IDENTIFIED and return ids

### 4. API Endpoints Design Rules (JSON examples)

Notes: every POST that creates an entity returns only {"technicalId":"<id>"} and triggers Cyoda processing.

Products
- POST /entity/Product
```json
{ "sku":"sku123","name":"Tee","price":19.99,"quantityAvailable":10 }
```
Response:
```json
{ "technicalId":"t_prod_001" }
```
- GET /entity/Product/{technicalId} -> returns full Product JSON

Cart
- POST /cart
```json
{ "requestId":"r1","userId":"u_001" }
```
Response:
```json
{ "technicalId":"t_cart_001" }
```
- GET /cart/{technicalId} -> full Cart

- POST /cart/{technicalId}/lines
```json
{ "requestId":"r2","sku":"sku123","qty":2 }
```
Response:
```json
{ "technicalId":"t_op_123" }
```

Identity / Checkout identify
- POST /checkout/identify
```json
{ "requestId":"r3","name":"Alice","email":"a@x.com","phone":"", "address":{"line1":"...","city":"...","postcode":"...","country":"..."} }
```
Response:
```json
{ "technicalId":"t_ident_001" }
```
(Note: processing will upsert User and Address and return userId/addressId via subsequent GETs or emitted events)

Place Order
- POST /checkout/place-order
```json
{ "requestId":"r4","cartTechnicalId":"t_cart_001","userTechnicalId":"t_user_001","addressTechnicalId":"t_addr_001" }
```
Response:
```json
{ "technicalId":"t_order_001" }
```
(GET /entity/Order/{technicalId} returns full Order including orderNumber and status)

Fulfillment (manual ops)
- POST /order/{technicalId}/start-picking -> returns {"technicalId":"t_op_..."}
- POST /order/{technicalId}/mark-sent -> returns {"technicalId":"t_op_..."}

---

Questions to finalize:
1) Keep camelCase fields as above?  
2) Should orderNumber be sequential human-friendly? (decide to enforce uniqueness)  
3) Confirm idempotency requirement: requestId required on all mutating POSTs?