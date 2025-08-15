# Final Functional Requirements (Event-Driven Architecture)

This document defines the canonical functional requirements for the prototype order-management system using an event-driven architecture. It includes entity definitions, workflows, API contracts, and business rules. The document ensures consistency between entity definitions and their workflows.

---

## 1. Entity Definitions

Notes:
- Max entities considered: 5 (User, Product, ShoppingCart, Order, ImportJob)
- Do not use enum constructs; fields that describe limited allowed values are represented as String with allowed values noted in parentheses.
- Creation and update of entities are EVENTS that trigger automated processing (persistence triggers workflows). Both create and update operations may start the entity workflow unless explicitly noted otherwise.

Entities (fields shown with types and allowed values where applicable):

User:
```
- id: UUID (primary business identifier)
- email: String (unique contact email; used for deduplication/merge)
- fullName: String (customer or admin name)
- role: String (Admin|Customer) (user role; default assigned on enrichment if missing)
- status: String (Pending|Active|Inactive|Failed) (account lifecycle status; initial state is Pending)
- createdAt: String (ISO8601 timestamp)
- updatedAt: String (ISO8601 timestamp)
- importSource: String (origin of import e.g., CSV|JSON|API)
- importBatchId: String (reference to import batch)
- importMetadata: Object (free-form metadata related to import)
```

Product:
```
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
```

ShoppingCart:
```
- id: UUID (cart identifier)
- customerId: UUID (references User.id)
- items: List of Object { productId: UUID, sku: String, quantity: Integer, unitPrice: Decimal } (cart line items)
- subtotal: Decimal (calculated subtotal before taxes/shipping)
- taxes: Decimal (calculated taxes)
- shipping: Decimal (calculated shipping cost)
- total: Decimal (subtotal + taxes + shipping)
- status: String (Open|CheckedOut|Abandoned)
- createdAt: String (ISO8601 timestamp)
- updatedAt: String (ISO8601 timestamp)
```

Order:
```
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
```

ImportJob:
```
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

---

## 2. Entity Workflows (event-driven)

General notes:
- Persistence (create or update) is an event that starts the entity workflow.
- Workflows should be resilient: processors should retry transient failures and record permanent errors in resultSummary (for imports) or entity-level status fields.
- Workflows should emit relevant domain events for downstream systems (notification, fulfillment, reporting).

### User workflow
1. Initial State: User persisted with status set to Pending (persist event triggers workflow)
2. Validation: Check required fields and email format
3. Deduplication: Search by email; if duplicate found then either merge or record a row-level error depending on import/processor configuration
4. Enrichment: Assign default role if missing; populate importMetadata
5. Activation: Based on verification and business criteria set status to Active or keep/set Inactive. On unrecoverable errors set status to Failed.
6. Post-processing: Optionally send welcome email (for Customers) or include user in bulk-import summary notifications to Admins
7. Completion: End with status (Active|Inactive|Failed) and log import/result metadata

State diagram (conceptual):

```mermaid
stateDiagram-v2
    [*] --> "Created (Pending)"
    "Created (Pending)" --> "Validation"
    "Validation" --> "Deduplication"
    "Deduplication" --> "Enrichment"
    "Enrichment" --> "Activation"
    "Activation" --> "Post-processing"
    "Post-processing" --> "Completed"
    "Validation" --> "Failed"
    "Deduplication" --> "Failed"
```

Notes:
- Deduplication behavior (merge vs error) is configurable per import/run and must be recorded in ImportJob.resultSummary.

---

### Product workflow
1. Initial State: Product persisted (event)
2. Validation: Verify SKU present, price >= 0, stockQuantity integer >= 0
3. Upsert: Deduplicate by SKU; create new product or update existing
4. Stock Processing: If stockQuantity changed then run stock-change processors (low-stock detection, reservation reconciliation, etc.)
5. Activation: Set product.active = true/false based on import flags or business rules
6. Completion: Log import results and trigger notifications if criteria met (e.g., low-stock alerts)

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

### ShoppingCart workflow
1. Initial State: ShoppingCart created with status = Open (create event triggers workflow)
2. Item Management: Add/Update/Delete items triggers recalculation processors
3. Recalculation: Recompute subtotal, taxes, shipping, and total on every change/save
4. Availability Validation: On save and on checkout, validate each item against Product.stockQuantity
5. Reservation (optional): On checkout initiation optionally reserve inventory temporarily
6. Checkout Trigger: When checkout event is invoked, mark cart.status = CheckedOut and emit Order creation event
7. Expiration: If cart idle beyond configured threshold, mark as Abandoned and release any reservations

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

Notes:
- Cart-level totals are computed deterministically by the recalculation processors (tax rules, shipping rules applied when relevant data is present).
- Item.quantity must be >= 1 (business rule enforced during validation).

---

### Order workflow
1. Initial State: Order created (event) with paymentStatus = Pending and fulfillmentStatus = Pending
2. Validation: Validate items, prices, customer identity and order totals
3. Stock Check & Reservation: Validate Product.stockQuantity for each item; if sufficient reserve/decrement stock; if insufficient mark order as Failed and notify customer
4. Payment Processing: Integrate with payment gateway or payment stub; on success set paymentStatus = Paid; on failure set paymentStatus = Failed
5. Confirmation: On payment success set fulfillmentStatus = Confirmed and notify customer/admin
6. Fulfillment: Update fulfillmentStatus through Shipped, Cancelled as downstream events occur
7. Failure Handling: On payment or stock failure release any reserved stock and set appropriate statuses

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

Notes:
- Checkout must validate stock and payment; on payment failure reserved stock is released and order remains unconfirmed.
- Payment integration can be a real gateway or a configurable stub used for testing.

---

### ImportJob workflow (orchestration entity)
1. Initial State: ImportJob created with status = Pending (creation is an event)
2. Validation: Validate file format/payload and required metadata
3. Processing: Parse file/payload and emit per-record create/update entity events (each record persisted as User or Product triggers its workflow)
4. Aggregation: Collect per-record results (created/updated/errors) and update progress/status
5. Completion: Set ImportJob.status = Completed or Failed and populate resultSummary
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

Notes:
- Processing must be able to emit per-record events idempotently and record per-row errors. The ImportJob.resultSummary should include counts and sample errors.

---

## 3. APIs (Event-Driven / EDA rules applied)

API design rules:
- POST endpoints create/persist entities and always trigger processing events.
- POST endpoints return ONLY {"technicalId": "string"} where technicalId is an internal/persistent identifier for the created job/entity save operation.
- GET endpoints retrieve stored application results (the persisted entity representation).
- GET endpoints are provided for entities created via POST endpoints (ImportJob, ShoppingCart, Order) and for admin retrieval of Products and Users.

Endpoints and JSON request/response formats (concise):

1) Import Users (creates ImportJob of type=User)
- POST /api/import/users
  - Request JSON:
    {
      "source": "CSV|JSON|API",
      "uploadedBy": "UUID",
      "fileUrl": "string (optional)" or payload: { "records": [ ... ] }
    }
  - Response JSON: { "technicalId": "string" }

- GET ImportJob by technicalId
  - GET /api/imports/{technicalId}
  - Response JSON: ImportJob persisted object with fields as defined in Entities section

2) Import Products (creates ImportJob of type=Product)
- POST /api/import/products
  - Request JSON (same structure as Import Users)
  - Response JSON: { "technicalId": "string" }

- GET ImportJob by technicalId: GET /api/imports/{technicalId}

3) Create Shopping Cart (Customer)
- POST /api/customers/{customerId}/carts
  - Request JSON:
    {
      "customerId": "UUID",
      "items": [ { "productId": "UUID", "sku": "string", "quantity": 2, "unitPrice": 9.99 } ]
    }
  - Response JSON: { "technicalId": "string" }

- GET ShoppingCart by technicalId
  - GET /api/carts/{technicalId}
  - Response JSON: ShoppingCart persisted object with computed totals and status

4) Checkout (creates Order via event)
- POST /api/customers/{customerId}/carts/{cartId}/checkout
  - Request JSON:
    {
      "customerId": "UUID",
      "cartId": "UUID",
      "payment": { "method": "card|stub", "details": { /* card token or stub flags */ } },
      "shipping": { "address": "string", "method": "standard|express" }
    }
  - Response JSON: { "technicalId": "string" }

- GET Order by technicalId
  - GET /api/orders/{technicalId}
  - Response JSON: Order persisted object with fields as defined in Entities section

5) Admin Product & User retrieval (GET-only endpoints for stored application results)
- GET /api/products (optional: GET all / paginated)
- GET /api/products/{productId}
- GET /api/admin/users/{userId}

These GET endpoints return full entity objects as persisted (same structure as entity definitions).

---

## 4. Business Rules (preserve critical logic)

- Users imported must have unique email addresses; on duplicate detection the configured behavior is either to merge records or fail the row with an error. Deduplication behavior must be recorded in ImportJob.resultSummary.
- Products must have unique SKU; price must be >= 0; stockQuantity must be integer >= 0.
- ShoppingCart item quantity must be >= 1. At checkout the cart must validate that item.quantity does not exceed available Product.stockQuantity (inventory enforcement is configurable but default is enforced).
- Checkout must validate stock and payment; if payment fails or stock is insufficient, reserved stock is released and order is not confirmed.
- Admins can manage users, products, and view all orders via admin GET endpoints.
- ImportJob parsing must emit per-record create/update events; each persisted record triggers its respective entity workflow.
- POST endpoints return only { "technicalId": "string" } and persist the entity which triggers the event-driven workflows.
- Workflows should record outcome metadata (success/failure, reasons) on the entity or ImportJob.resultSummary for observability and troubleshooting.

---

## 5. Request/Response Visualization note
- All POST responses strictly: { "technicalId": "string" }
- All GET responses return the persisted entity JSON with fields as defined in the Entity section above.

---

## 6. Questions (max 3, short)
1. For imports, do you prefer CSV, JSON, or both?
2. Should product inventory (stockQuantity) be strictly enforced at checkout by default? (Yes or No)
3. Do you want a real payment gateway integration or a payment placeholder/stub for testing?

---

If you want further updates (for example: explicit idempotency keys for POSTs, additional statuses, or expanded event contracts), tell me which area to change and I will update the requirements accordingly.