### NOTE
You did not explicitly confirm entity counts or fields earlier. Per the agreed rules (when the user does not specify entities, default to a maximum of 3 entities), this final functional requirements document models 3 entities. No additional assumptions beyond that default were added.

### 1. Entity Definitions

```
Product:
- businessId: String (business identifier for product, e.g., SKU or external product id)
- name: String (product name)
- description: String (detailed description)
- price: Decimal (unit price)
- currency: String (ISO currency code)
- images: Array<String> (URLs to product images)
- stockQuantity: Integer (available inventory count)
- category: String (category or taxonomy)
- attributes: Object (key/value for flexible product attributes)
- active: Boolean (whether product is active in catalog)
- createdAt: DateTime (creation timestamp)
- updatedAt: DateTime (last update timestamp)

Order:
- orderNumber: String (business order identifier)
- customerId: String (reference to customer)
- items: Array<Object> (each with productBusinessId: String, sku: String, quantity: Integer, unitPrice: Decimal)
- totalAmount: Decimal (total payable)
- currency: String (ISO currency code)
- shippingAddress: Object (street, city, state, postalCode, country)
- billingAddress: Object (street, city, state, postalCode, country)
- paymentStatus: String (e.g., PENDING, AUTHORIZED, CAPTURED, FAILED)
- fulfillmentStatus: String (e.g., PENDING, ALLOCATED, PICKED, SHIPPED, DELIVERED, CANCELLED)
- placedAt: DateTime (when order was created)
- updatedAt: DateTime (last update timestamp)
- notes: String (optional notes or instructions)

ImportJob:
- sourceType: String (e.g., FILE, SFTP, API)
- sourceLocation: String (URI or path to source)
- fileFormat: String (CSV, JSON, XML)
- mappingRules: Object (rules or mapping configuration used to map source to target entities)
- status: String (e.g., CREATED, VALIDATING, PROCESSING, COMPLETED, FAILED)
- initiatedBy: String (user or system who started the job)
- startedAt: DateTime (timestamp when processing started)
- completedAt: DateTime (timestamp when processing finished)
- recordsProcessed: Integer (total records processed)
- recordsSucceeded: Integer (successful records count)
- recordsFailed: Integer (failed records count)
- errorDetails: String (aggregation of errors or pointer to error artifact)
- createdAt: DateTime (when job was created)
- updatedAt: DateTime (last update timestamp)
```

---

### 2. Entity workflows

General EDA principle applied:
- Each entity persist (database save) triggers an automatic process method that executes the entity's workflow (criteria evaluations and processors).
- Transitions are identified as automatic (system triggered) or manual (user triggered).

Product workflow:
1. Initial State: Product persisted with CREATED status (automatic trigger)
2. Validation: Validate required fields and price/stock constraints (automatic)
3. Enrichment: Enrich product metadata (e.g., fill derived attributes, normalize images) (automatic)
4. Indexing: Index product into search/catalog (automatic)
5. Activation: Mark product ACTIVE if indexing succeeded (automatic) or mark FAILED (automatic)
6. Manual Management: Admins can deactivate/reactivate product manually (manual transition)

Mermaid state diagram:

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "VALIDATING" : "ValidateProductProcessor, automatic"
    "VALIDATING" --> "ENRICHING" : "ValidateProductCriterion, automatic"
    "ENRICHING" --> "INDEXING" : "EnrichProductProcessor, automatic"
    "INDEXING" --> "ACTIVE" : "IndexProductProcessor, automatic"
    "INDEXING" --> "FAILED" : "IndexFailureCriterion, automatic"
    "ACTIVE" --> "INACTIVE" : "AdminDeactivateAction, manual"
    "INACTIVE" --> "ACTIVE" : "AdminActivateAction, manual"
    "FAILED" --> [*]
    "ACTIVE" --> [*]
```

Product workflow - criteria and processors (suggested):
- Criteria:
  - ValidateProductCriterion (checks required fields present and price >= 0)
  - IndexFailureCriterion (checks if indexing failed)
- Processors:
  - ValidateProductProcessor (performs detailed validation and sets validation metadata)
  - EnrichProductProcessor (normalizes attributes, resizes images, derives facets)
  - IndexProductProcessor (pushes product to search/catalog)
  - NotifyCatalogProcessor (optional, notifies downstream systems of new product)

Order workflow:
1. Initial State: Order created with CREATED / PENDING payment (manual persist via POST triggers workflow)
2. Payment Authorization: Try to authorize or capture payment (automatic)
   - If payment fails => PAYMENT_FAILED (automatic); manual retry possible
3. Inventory Allocation: Allocate inventory (automatic); if allocation fails, mark ALLOCATION_FAILED (automatic) and notify operations
4. Fulfillment: Once allocated and payment captured, mark FULFILLMENT_IN_PROGRESS (automatic) and generate pick/pack tasks
5. Shipping: Mark SHIPPED when carrier shipment created (automatic or manual depending on integration)
6. Delivery / Close: When delivered, mark DELIVERED and then CLOSED (automatic or manual confirmation)
7. Cancellation / Returns: Manual actions by customer service move order to CANCELLED or RETURNED states

Mermaid state diagram:

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "PAYMENT_AUTH" : "ChargePaymentProcessor, automatic"
    "PAYMENT_AUTH" --> "PAYMENT_CONFIRMED" : "PaymentCompletedCriterion, automatic"
    "PAYMENT_AUTH" --> "PAYMENT_FAILED" : "PaymentFailedCriterion, automatic"
    "PAYMENT_CONFIRMED" --> "ALLOCATING" : "AllocateInventoryProcessor, automatic"
    "ALLOCATING" --> "FULFILLMENT_IN_PROGRESS" : "InventoryAllocatedCriterion, automatic"
    "FULFILLMENT_IN_PROGRESS" --> "SHIPPED" : "CreateShipmentProcessor, automatic"
    "SHIPPED" --> "DELIVERED" : "ConfirmDeliveryProcessor, automatic"
    "DELIVERED" --> "CLOSED" : "CloseOrderProcessor, automatic"
    "PAYMENT_FAILED" --> "CANCELLED" : "AdminCancelAction, manual"
    "FULFILLMENT_IN_PROGRESS" --> "CANCELLED" : "AdminCancelAction, manual"
    "CLOSED" --> [*]
    "CANCELLED" --> [*]
```

Order workflow - criteria and processors (suggested):
- Criteria:
  - PaymentCompletedCriterion (checks paymentStatus is CAPTURED)
  - InventoryAllocatedCriterion (checks allocation success)
  - OrderFulfilledCriterion (checks shipments completed)
- Processors:
  - ChargePaymentProcessor (call payment gateway, update paymentStatus)
  - AllocateInventoryProcessor (reserve stock, decrement available counts)
  - CreateShipmentProcessor (create carrier shipment and update tracking)
  - NotifyCustomerProcessor (email/SMS notifications at key stages)
  - CloseOrderProcessor (finalize accounting, mark order closed)

ImportJob workflow:
1. Initial State: ImportJob persisted with CREATED (automatic)
2. Validation: Validate source, format, mapping rules (automatic)
3. Processing: Parse, transform, and persist records (automatic)
4. Summarize: Aggregate results, set recordsProcessed/succeeded/failed (automatic)
5. Completion: Set status COMPLETED or FAILED (automatic)
6. Notification: Notify initiator/admin of job result (automatic)
7. Manual Retry: Admin may trigger RETRY for failed imports (manual)

Mermaid state diagram:

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "VALIDATING" : "ValidateFileProcessor, automatic"
    "VALIDATING" --> "PROCESSING" : "ValidationPassedCriterion, automatic"
    "PROCESSING" --> "SUMMARIZING" : "ParseRecordsProcessor, automatic"
    "SUMMARIZING" --> "COMPLETED" : "SummarizeResultsProcessor, automatic"
    "SUMMARIZING" --> "FAILED" : "ProcessingFailedCriterion, automatic"
    "COMPLETED" --> "NOTIFIED" : "NotifyAdminsProcessor, automatic"
    "NOTIFIED" --> [*]
    "FAILED" --> [*]
```

ImportJob workflow - criteria and processors (suggested):
- Criteria:
  - ValidationPassedCriterion (file validated and mapping rules OK)
  - ProcessingFailedCriterion (errors exceed threshold)
- Processors:
  - ValidateFileProcessor (check accessibility, format, mapping validity)
  - ParseRecordsProcessor (stream parse records and emit per-record work)
  - TransformRecordProcessor (apply mappingRules to transform into target entity payload)
  - PersistRecordProcessor (persist transformed records into target store, e.g., Product persist)
  - SummarizeResultsProcessor (collect stats and errors)
  - NotifyAdminsProcessor (send job result notifications with error summary)

---

### 3. Pseudo code for processor classes

Note: These are Java-style pseudo-classes showing interfaces and main logic steps expected in the processors/criteria.

Product processors

```java
// ValidateProductProcessor
public class ValidateProductProcessor {
    public ValidationResult process(Product product) {
        // Check required fields
        if (product.name == null || product.price == null) {
            return ValidationResult.fail("Missing required fields");
        }
        if (product.price.compareTo(BigDecimal.ZERO) < 0) {
            return ValidationResult.fail("Negative price");
        }
        // other checks
        return ValidationResult.ok();
    }
}

// EnrichProductProcessor
public class EnrichProductProcessor {
    public Product process(Product product) {
        // Normalize name, generate search keywords, auto-fill attributes
        product.attributes = normalizeAttributes(product.attributes);
        product.images = standardizeImages(product.images);
        product.updatedAt = now();
        return product;
    }
}

// IndexProductProcessor
public class IndexProductProcessor {
    public void process(Product product) {
        // Push to search/index service; handle retries
        searchIndexService.index(product.businessId, product);
    }
}
```

Order processors

```java
// ChargePaymentProcessor
public class ChargePaymentProcessor {
    public PaymentResult process(Order order) {
        // Call payment gateway using order.payment info (assumed present)
        PaymentResult result = paymentGateway.authorizeAndCapture(order.totalAmount, order.currency, order.customerId);
        if (result.success) {
            order.paymentStatus = "CAPTURED";
        } else {
            order.paymentStatus = "FAILED";
        }
        order.updatedAt = now();
        orderRepository.save(order);
        return result;
    }
}

// AllocateInventoryProcessor
public class AllocateInventoryProcessor {
    public AllocationResult process(Order order) {
        for (Item item : order.items) {
            boolean ok = inventoryService.reserve(item.productBusinessId, item.quantity);
            if (!ok) {
                return AllocationResult.failed("OutOfStock for " + item.productBusinessId);
            }
        }
        order.fulfillmentStatus = "ALLOCATED";
        order.updatedAt = now();
        orderRepository.save(order);
        return AllocationResult.success();
    }
}

// CreateShipmentProcessor
public class CreateShipmentProcessor {
    public ShipmentResult process(Order order) {
        // Collect package details and call carrier API
        Shipment shipment = shipmentService.createShipment(order);
        order.fulfillmentStatus = "SHIPPED";
        order.notes = "Tracking: " + shipment.trackingNumber;
        order.updatedAt = now();
        orderRepository.save(order);
        return new ShipmentResult(true, shipment.trackingNumber);
    }
}
```

ImportJob processors

```java
// ValidateFileProcessor
public class ValidateFileProcessor {
    public ValidationResult process(ImportJob job) {
        // Check source accessibility and format conformity
        if (!sourceService.exists(job.sourceType, job.sourceLocation)) {
            return ValidationResult.fail("Source not found");
        }
        if (!isSupportedFormat(job.fileFormat)) {
            return ValidationResult.fail("Unsupported file format");
        }
        return ValidationResult.ok();
    }
}

// ParseRecordsProcessor
public class ParseRecordsProcessor {
    public void process(ImportJob job) {
        Stream<Record> records = recordParser.parse(job.sourceLocation, job.fileFormat);
        RecordSummary summary = new RecordSummary();
        for (Record r : records) {
            try {
                Object transformed = TransformRecordProcessor.apply(r, job.mappingRules);
                PersistRecordProcessor.persist(transformed);
                summary.succeeded++;
            } catch (Exception ex) {
                summary.failed++;
                logError(job, r, ex);
            }
        }
        job.recordsProcessed = summary.total();
        job.recordsSucceeded = summary.succeeded;
        job.recordsFailed = summary.failed;
        job.updatedAt = now();
        jobRepository.save(job);
    }
}

// PersistRecordProcessor
public class PersistRecordProcessor {
    public void persist(Object transformedEntity) {
        // e.g., if transformed is a product payload, save as Product entity
        productRepository.save(transformedEntity);
    }
}
```

Suggested criterion pseudo-interfaces:

```java
// Example boolean criterion
public interface Criterion<T> {
    boolean evaluate(T entity);
}

// PaymentCompletedCriterion
public class PaymentCompletedCriterion implements Criterion<Order> {
    public boolean evaluate(Order order) {
        return "CAPTURED".equals(order.paymentStatus);
    }
}
```

---

### 4. API Endpoints Design Rules

Summary rules applied:
- POST endpoints: entity creation triggers events and must return only a JSON with a single field technicalId (a datastore-specific generated identifier).
- GET endpoints: only for retrieving stored application results.
- GET by technicalId: present for all entities created via POST endpoints and provided where useful for retrieval.
- No GET-by-condition endpoints included (they were not explicitly requested).
- Orchestration entities (Order, ImportJob) have POST endpoints to create them and GET by technicalId to retrieve status/results.
- Business entity Product is expected to be created by processing (ImportJob) or administrative UI; for the purposes of retrieval, a GET by technicalId endpoint is provided.

Endpoint list and JSON request/response formats:

1) Create ImportJob (POST)
- Path: POST /api/import-jobs
- Purpose: Create an import job; persisting this entity triggers the ImportJob workflow
- Request JSON (body):
```json
{
  "sourceType": "FILE",
  "sourceLocation": "s3://bucket/path/file.csv",
  "fileFormat": "CSV",
  "mappingRules": {
    "map": "rules"
  },
  "initiatedBy": "admin@example.com"
}
```
- Response JSON (201 Created):
```json
{
  "technicalId": "string" 
}
```
(Only technicalId must be returned. The created entity fields are not returned by POST.)

2) Get ImportJob by technicalId (GET)
- Path: GET /api/import-jobs/{technicalId}
- Purpose: Retrieve full persisted job status and aggregated results
- Response JSON (200 OK):
```json
{
  "technicalId": "string",
  "sourceType": "FILE",
  "sourceLocation": "s3://bucket/path/file.csv",
  "fileFormat": "CSV",
  "mappingRules": {
    "map": "rules"
  },
  "status": "COMPLETED",
  "initiatedBy": "admin@example.com",
  "startedAt": "2025-08-26T12:00:00Z",
  "completedAt": "2025-08-26T12:05:00Z",
  "recordsProcessed": 100,
  "recordsSucceeded": 97,
  "recordsFailed": 3,
  "errorDetails": "errors.csv",
  "createdAt": "2025-08-26T11:59:55Z",
  "updatedAt": "2025-08-26T12:05:00Z"
}
```

3) Create Order (POST)
- Path: POST /api/orders
- Purpose: Persist an order; triggers the Order workflow (payment, allocation, fulfillment)
- Request JSON (body):
```json
{
  "orderNumber": "ORD-1001",
  "customerId": "CUST-123",
  "items": [
    {
      "productBusinessId": "SKU-001",
      "sku": "SKU-001",
      "quantity": 2,
      "unitPrice": 19.99
    }
  ],
  "totalAmount": 39.98,
  "currency": "USD",
  "shippingAddress": {
    "street": "123 Example St",
    "city": "Sample City",
    "state": "CA",
    "postalCode": "90001",
    "country": "US"
  },
  "billingAddress": {
    "street": "123 Example St",
    "city": "Sample City",
    "state": "CA",
    "postalCode": "90001",
    "country": "US"
  },
  "notes": "Leave at front door"
}
```
- Response JSON (201 Created):
```json
{
  "technicalId": "string"
}
```

4) Get Order by technicalId (GET)
- Path: GET /api/orders/{technicalId}
- Purpose: Retrieve full order state, payment and fulfillment status
- Response JSON (200 OK):
```json
{
  "technicalId": "string",
  "orderNumber": "ORD-1001",
  "customerId": "CUST-123",
  "items": [
    {
      "productBusinessId": "SKU-001",
      "sku": "SKU-001",
      "quantity": 2,
      "unitPrice": 19.99
    }
  ],
  "totalAmount": 39.98,
  "currency": "USD",
  "shippingAddress": {
    "street": "123 Example St",
    "city": "Sample City",
    "state": "CA",
    "postalCode": "90001",
    "country": "US"
  },
  "billingAddress": {
    "street": "123 Example St",
    "city": "Sample City",
    "state": "CA",
    "postalCode": "90001",
    "country": "US"
  },
  "paymentStatus": "CAPTURED",
  "fulfillmentStatus": "SHIPPED",
  "placedAt": "2025-08-26T12:10:00Z",
  "updatedAt": "2025-08-26T12:45:00Z",
  "notes": "Leave at front door"
}
```

5) Get Product by technicalId (GET)
- Path: GET /api/products/{technicalId}
- Purpose: Retrieve product details stored in the system (product entities are typically created by processors like ImportJob)
- Response JSON (200 OK):
```json
{
  "technicalId": "string",
  "businessId": "SKU-001",
  "name": "Example Product",
  "description": "Detailed product description",
  "price": 19.99,
  "currency": "USD",
  "images": ["https://cdn.example.com/img1.jpg"],
  "stockQuantity": 120,
  "category": "General",
  "attributes": {
    "color": "red",
    "size": "M"
  },
  "active": true,
  "createdAt": "2025-08-26T11:50:00Z",
  "updatedAt": "2025-08-26T12:00:00Z"
}
```

Additional API design notes:
- POST endpoints must always return only { "technicalId": "..." } per rule.
- GET endpoints return the persisted entity fields and include technicalId in the response (technicalId is a datastore-specific identifier and is included in GET responses).
- No GET-by-condition endpoints are provided because they were not explicitly requested.
- Authentication/authorization, rate limiting, pagination, and filtering are expected cross-cutting concerns to be implemented outside this specification and are not added here.

---

If you want this expanded to the previously suggested 5-entity model (User, Cart, Product, Order, ImportJob) or to include GET-by-condition endpoints (e.g., search products, list orders by customer), tell me which additions you want and I will produce an updated final specification.