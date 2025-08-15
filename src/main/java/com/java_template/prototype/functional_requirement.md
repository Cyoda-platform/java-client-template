# Final Functional Requirements (Event-Driven Architecture)

Entity Definitions
```
User:
- id: UUID (primary business identifier)
- email: String (unique contact email; used for deduplication/merge)
- fullName: String (customer or admin name)
- role: String (Admin|Customer) (user role)
- status: String (Active|Inactive) (account status)
- createdAt: String (ISO8601 timestamp)
- updatedAt: String (ISO8601 timestamp)
- importSource: String (origin of import e.g., CSV|JSON|API)
- importBatchId: String (reference to import batch)
- importMetadata: Object (free-form metadata related to import)

Product:
- id: UUID (primary business identifier)
- sku: String (unique stock keeping unit; used for deduplicate/upsert)
- name: String (product name)
- description: String (product description)
- price: Decimal (product price; must be >= 0)
- currency: String (currency code e.g., USD)
- stockQuantity: Integer (inventory level; must be >= 0)
- active: Boolean (whether product is active/available)
- createdAt: String (ISO8601 timestamp)
- updatedAt: String (ISO8601 timestamp)
- metadata: Object (additional product metadata)
- importSource: String (origin of import e.g., CSV|JSON|API)
- importBatchId: String (reference to import batch)

ShoppingCart:
- id: UUID (cart identifier)
- customerId: UUID (references User.id)
- items: List of Object { productId: UUID, sku: String, quantity: Integer, unitPrice: Decimal } (cart line items)
- subtotal: Decimal (calculated subtotal before taxes/shipping)
- taxes: Decimal (calculated taxes)
- total: Decimal (subtotal + taxes + shipping)
- status: String (Open|CheckedOut|Abandoned)
- createdAt: String (ISO8601 timestamp)
- updatedAt: String (ISO8601 timestamp)

Order:
- id: UUID (order identifier)
- orderNumber: String (human-friendly order number)
- customerId: UUID (references User.id)
- items: List of Object { productId: UUID, sku: String, quantity: Integer, unitPrice: Decimal } (order line items)
- subtotal: Decimal (before taxes/shipping)
- taxes: Decimal
- shipping: Decimal
- total: Decimal
- currency: String
- paymentStatus: String (Pending|Paid|Failed)
- fulfillmentStatus: String (Pending|Confirmed|Shipped|Cancelled)
- createdAt: String (ISO8601 timestamp)
- updatedAt: String (ISO8601 timestamp)

ImportJob:
- id: UUID (import job identifier)
- type: String (User|Product)
- source: String (CSV|JSON|API) (format/source)
- uploadedBy: UUID (User.id who initiated import)
- status: String (Pending|Processing|Completed|Failed)
- fileUrl: String (reference to uploaded file) OR payload reference
- createdAt: String (ISO8601 timestamp)
- completedAt: String (ISO8601 timestamp)
- resultSummary: Object (counts created/updated/errors and example row errors)
```

Notes:
- Max entities considered: 5 (User, Product, ShoppingCart, Order, ImportJob)
- Do not use enum constructs (fields that describe limited allowed values are represented as String with allowed values noted in parentheses).
- Each entity add operation is an EVENT that triggers automated processing (Cyoda will run entity workflows on persistence).

---

## 2. Entity workflows

### User workflow:
1. Initial State: User created with status set to Pending validation (persist event triggers workflow)  
2. Validation: Check email format and required fields  
3. Deduplication: Search by email; if duplicate found then merge or mark row-level error (based on import/processor criteria)  
4. Enrichment: Assign default role if missing; populate importMetadata  
5. Activation: Set status to Active or keep Inactive based on criteria  
6. Post-processing: Optionally send welcome email (for Customer) or notify Admins on bulk import summary  
7. Completion: Workflow ends with status Active/Inactive and Import result logged

```mermaid
stateDiagram-v2
    [*] --> "Created"
    "Created" --> "Validation"
    "Validation" --> "Deduplication"
    "Deduplication" --> "Enrichment"
    "Enrichment" --> "Activation"
    "Activation" --> "Post-processing"
    "Post-processing" --> "Completed"
    "Validation" --> "Failed"
    "Deduplication" --> "Failed"
```

---

### Product workflow:
1. Initial State: Product record persisted (event)  
2. Validation: Verify SKU present, price >= 0, stockQuantity integer >= 0  
3. Upsert: Deduplicate by SKU; create new product or update existing  
4. Stock Processing: If stockQuantity changed then run stock-change processors (e.g., low-stock detection)  
5. Activation: Mark product as active/inactive based on import flags or processors  
6. Completion: Log import results and trigger low-stock notifications if criteria met

```mermaid
stateDiagram-v2
    [*] --> "Created"
    "Created" --> "Validation"
    "Validation" --> "Upsert"
    "Upsert" --> "Stock Processing"
    "Stock Processing" --> "Activation"
    "Activation" --> "Completed"
    "Validation" --> "Failed"
```

---

### ShoppingCart workflow:
1. Initial State: ShoppingCart created with status=Open (creation is an event)  
2. Item Management: Add/Update/Delete items triggers recalculation processors  
3. Recalculation: Recompute subtotal, taxes, total on every save  
4. Availability Validation: On save and especially on checkout, validate each item against Product.stockQuantity  
5. Reservation (optional): Reserve inventory temporarily on checkout initiation  
6. Checkout Trigger: When checkout event is invoked, mark cart status=CheckedOut and emit Order creation event  
7. Expiration: If cart idle beyond threshold, mark as Abandoned and release any reservations

```mermaid
stateDiagram-v2
    [*] --> "Open"
    "Open" --> "Modified"
    "Modified" --> "Recalculation"
    "Recalculation" --> "Availability Validation"
    "Availability Validation" --> "Open"
    "Availability Validation" --> "ReadyToCheckout"
    "ReadyToCheckout" --> "CheckedOut"
    "Open" --> "Abandoned"
```

---

### Order workflow:
1. Initial State: Order created (event) with paymentStatus=Pending and fulfillmentStatus=Pending  
2. Validation: Validate items, prices, customer, and order totals  
3. Stock Check & Reservation: Check Product.stockQuantity; if sufficient reserve/decrement stock; if insufficient mark order as Failed and notify customer  
4. Payment Processing: Call payment processor stub or integration; on success set paymentStatus=Paid; on failure set paymentStatus=Failed  
5. Confirmation: On payment success set fulfillmentStatus=Confirmed and notify customer/admin  
6. Fulfillment: Update fulfillmentStatus through Shipped, Cancelled as downstream events occur  
7. Failure Handling: On payment or stock failure release reserved stock and set appropriate statuses

```mermaid
stateDiagram-v2
    [*] --> "Pending"
    "Pending" --> "Validation"
    "Validation" --> "Stock Check"
    "Stock Check" --> "Payment Processing"
    "Payment Processing" --> "Paid"
    "Payment Processing" --> "Failed"
    "Paid" --> "Confirmed"
    "Confirmed" --> "Shipped"
    "Failed" --> "Cancelled"
```

---

### ImportJob workflow (orchestration entity):
1. Initial State: ImportJob created with status=Pending (creation is an EVENT)  
2. Validation: Validate file format/payload  
3. Processing: Parse file/payload and emit per-record create/update entity events (each record persists as User/Product entity and triggers its workflow)  
4. Aggregation: Collect per-record results (created/updated/errors)  
5. Completion: Set ImportJob.status=Completed or Failed and populate resultSummary  
6. Notification: Notify uploader/admins with import result summary and error report (downloadable CSV if requested)

```mermaid
stateDiagram-v2
    [*] --> "Pending"
    "Pending" --> "Validation"
    "Validation" --> "Processing"
    "Processing" --> "Aggregation"
    "Aggregation" --> "Completed"
    "Processing" --> "Failed"
```

---

## 3. APIs (Event-Driven / EDA rules applied)

API design rules applied:
- POST endpoints create entities and trigger processing events.
- POST endpoints return only {"technicalId": "string"}.
- GET endpoints are for retrieving stored application results.
- GET by technicalId endpoints are provided for entities created via POST endpoints (ImportJob, ShoppingCart, Order).

Endpoints and JSON request/response formats

---

### 1) Import Users (creates ImportJob of type=User)
- POST /api/import/users
  - Request JSON:
    {
      "source": "CSV|JSON|API",
      "uploadedBy": "UUID",
      "fileUrl": "string (optional) or payload": { "records": [ ... ] }
    }
  - Response JSON:
    {
      "technicalId": "string"
    }

Mermaid visualization of request/response:
```mermaid
graph LR
    req["\"POST /api/import/users\\nRequest JSON\""] --> api["\"Import API (creates ImportJob)\""]
    api --> res["\"Response JSON: {\\n  \\\"technicalId\\\": \\\"string\\\"\\n}\""]
```

- GET ImportJob by technicalId
  - GET /api/imports/{technicalId}
  - Response JSON:
    {
      "id": "UUID",
      "type": "User",
      "source": "CSV|JSON|API",
      "uploadedBy": "UUID",
      "status": "Pending|Processing|Completed|Failed",
      "fileUrl": "string",
      "createdAt": "ISO8601",
      "completedAt": "ISO8601",
      "resultSummary": { "created": 10, "updated": 2, "errors": 1 }
    }

Mermaid:
```mermaid
graph LR
    req["\"GET /api/imports/{technicalId}\\nRequest\""] --> api["\"ImportJob Retrieval API\""]
    api --> res["\"Response JSON with ImportJob status and resultSummary\""]
```

---

### 2) Import Products (creates ImportJob of type=Product)
- POST /api/import/products
  - Request JSON:
    {
      "source": "CSV|JSON|API",
      "uploadedBy": "UUID",
      "fileUrl": "string (optional) or payload": { "records": [ ... ] }
    }
  - Response JSON:
    {
      "technicalId": "string"
    }

Mermaid:
```mermaid
graph LR
    req["\"POST /api/import/products\\nRequest JSON\""] --> api["\"Import API (creates ImportJob)\""]
    api --> res["\"Response JSON: {\\n  \\\"technicalId\\\": \\\"string\\\"\\n}\""]
```

---

### 3) Create Shopping Cart (Customer)
- POST /api/customers/{customerId}/carts
  - Request JSON:
    {
      "customerId": "UUID",
      "items": [
        { "productId": "UUID", "sku": "string", "quantity": 2, "unitPrice": 9.99 }
      ]
    }
  - Response JSON:
    {
      "technicalId": "string"
    }

Mermaid:
```mermaid
graph LR
    req["\"POST /api/customers/{customerId}/carts\\nRequest JSON\""] --> api["\"Create ShoppingCart (persist event triggers Cart workflow)\""]
    api --> res["\"Response JSON: {\\n  \\\"technicalId\\\": \\\"string\\\"\\n}\""]
```

- GET ShoppingCart by technicalId
  - GET /api/carts/{technicalId}
  - Response JSON:
    {
      "id": "UUID",
      "customerId": "UUID",
      "items": [ { "productId": "UUID", "sku": "string", "quantity": 2, "unitPrice": 9.99 } ],
      "subtotal": 19.98,
      "taxes": 1.50,
      "total": 21.48,
      "status": "Open|CheckedOut|Abandoned",
      "createdAt": "ISO8601",
      "updatedAt": "ISO8601"
    }

Mermaid:
```mermaid
graph LR
    req["\"GET /api/carts/{technicalId}\\nRequest\""] --> api["\"ShoppingCart Retrieval API\""]
    api --> res["\"Response JSON with cart fields\""]
```

---

### 4) Checkout (creates Order via event)
- POST /api/customers/{customerId}/carts/{cartId}/checkout
  - Request JSON:
    {
      "customerId": "UUID",
      "cartId": "UUID",
      "payment": { "method": "card|stub", "details": { /* card token or stub flags */ } },
      "shipping": { "address": "string", "method": "standard|express" }
    }
  - Response JSON:
    {
      "technicalId": "string"
    }

Mermaid:
```mermaid
graph LR
    req["\"POST /api/customers/{customerId}/carts/{cartId}/checkout\\nRequest JSON\""] --> api["\"Checkout API (creates Order event)\""]
    api --> res["\"Response JSON: {\\n  \\\"technicalId\\\": \\\"string\\\"\\n}\""]
```

- GET Order by technicalId
  - GET /api/orders/{technicalId}
  - Response JSON:
    {
      "id": "UUID",
      "orderNumber": "string",
      "customerId": "UUID",
      "items": [ { "productId": "UUID", "sku": "string", "quantity": 2, "unitPrice": 9.99 } ],
      "subtotal": 19.98,
      "taxes": 1.50,
      "shipping": 5.00,
      "total": 26.48,
      "currency": "USD",
      "paymentStatus": "Pending|Paid|Failed",
      "fulfillmentStatus": "Pending|Confirmed|Shipped|Cancelled",
      "createdAt": "ISO8601",
      "updatedAt": "ISO8601"
    }

Mermaid:
```mermaid
graph LR
    req["\"GET /api/orders/{technicalId}\\nRequest\""] --> api["\"Order Retrieval API\""]
    api --> res["\"Response JSON with order fields\""]
```

---

### 5) Admin Product & User retrieval (GET-only endpoints for stored application results)
- GET /api/products (optional GET all)
- GET /api/products/{productId} (retrieve product by productId)
- GET /api/admin/users/{userId} (retrieve user by userId)

These GET endpoints return full entity objects as persisted (same structure as entity definitions).

---

## 4. Business Rules (preserve critical logic)
- Users imported must have unique email addresses; deduplicate or fail with row-level errors.  
- Products must have unique SKU; price must be >= 0; stockQuantity must be integer >= 0.  
- A ShoppingCart item quantity must be >= 1 and must not exceed available Product.stockQuantity at checkout.  
- Checkout must validate stock and payment; if payment fails, the order is not confirmed and reserved stock is released.  
- Admins can manage users, products, and view all orders.  
- ImportJob parsing must emit per-record create/update events; each persisted record triggers its entity workflow in Cyoda.  
- POST endpoints return only {"technicalId":"string"} and persist the entity which triggers Cyoda workflows.

---

## 5. Request/Response Visualization note
- All POST responses strictly: {"technicalId": "string"}  
- All GET responses return the persisted entity JSON with fields as defined above.

---

## 6. Questions (max 3, short)
1. For imports, do you prefer CSV, JSON, or both?  
2. Should product inventory (stockQuantity) be strictly enforced at checkout? (Yes or No)  
3. Do you want a real payment gateway integration or a payment placeholder/stub for testing?

---

Ready-to-Copy Example User Response
You can copy the text below and paste it if you have no specific preference. If it meets your needs you can click Approve to proceed.

```markdown
Build a simple sample order management system with product and user import functionality using Event-Driven Architecture.

Entities:
1. User
   - Fields: id, email, fullName, role (Admin|Customer), status (Active|Inactive), createdAt, updatedAt, importSource, importBatchId, importMetadata
   - Workflow: validate email, deduplicate by email, assign default role, send welcome email (optional), notify import summary

2. Product
   - Fields: id, sku, name, description, price (>=0), currency, stockQuantity (>=0), active, createdAt, updatedAt, metadata, importSource, importBatchId
   - Workflow: validate SKU/price, upsert by SKU, trigger low-stock notification if stockQuantity changed and below threshold

3. ShoppingCart
   - Fields: id, customerId, items [{ productId, sku, quantity, unitPrice }], subtotal, taxes, total, status (Open|CheckedOut|Abandoned), createdAt, updatedAt
   - Workflow: recalculate totals on save, validate item availability, reserve items (on checkout), expire abandoned carts

4. Order
   - Fields: id, orderNumber, customerId, items [{ productId, sku, quantity, unitPrice }], subtotal, taxes, shipping, total, currency, paymentStatus (Pending|Paid|Failed), fulfillmentStatus (Pending|Confirmed|Shipped|Cancelled), createdAt, updatedAt
   - Workflow: validate order, check/reserve stock, process payment (stub/integration), confirm order on payment success, notify customer/Admin, release stock on failure

5. ImportJob
   - Fields: id, type (User|Product), source (CSV|JSON|API), uploadedBy, status (Pending|Processing|Completed|Failed), fileUrl/payload, createdAt, completedAt, resultSummary
   - Workflow: validate file, parse records, emit per-record create/update events, aggregate results and notify uploader

APIs:
- POST /api/import/users  (creates ImportJob type=User) => returns {"technicalId":"string"}
- POST /api/import/products  (creates ImportJob type=Product) => returns {"technicalId":"string"}
- POST /api/customers/{customerId}/carts  (creates ShoppingCart) => returns {"technicalId":"string"}
- POST /api/customers/{customerId}/carts/{cartId}/checkout  (creates Order) => returns {"technicalId":"string"}
- GET /api/imports/{technicalId}  (ImportJob status & resultSummary)
- GET /api/carts/{technicalId}  (ShoppingCart)
- GET /api/orders/{technicalId}  (Order)
- GET /api/products and GET /api/products/{productId}
- GET /api/admin/users/{userId}

Business rules:
- Unique email for Users; deduplicate or row-level import errors.
- Unique SKU for Products; price >= 0; stockQuantity >= 0.
- Cart item quantity >= 1 and must not exceed available stock at checkout.
- Checkout validates stock and payment; on payment failure, order not confirmed and stock released.
- ImportJob parsing emits per-record persist events; each persisted record triggers its entity workflow.

Questions:
1) CSV, JSON, or both for imports?
2) Enforce inventory at checkout? (Yes/No)
3) Real payment gateway or payment stub?

```

Please review the generated entities and workflows. If you need any changes, please let me know. Feel free to click Approve if this requirement meets your expectations or if you are ready to proceed.