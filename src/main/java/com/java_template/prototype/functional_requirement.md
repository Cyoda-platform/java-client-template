# Functional Requirements for Order Management System Based on Cyoda’s Event-Driven Architecture

---

## 1. Entity Definitions

```
UserImportJob:
- jobId: String (unique identifier for the import job)
- importData: String (raw user data in JSON or CSV format)
- status: String (job processing status: PENDING, PROCESSING, COMPLETED, FAILED)
- createdAt: DateTime (timestamp of job creation)

User:
- userId: String (unique identifier for user)
- name: String (user's full name)
- email: String (user email)
- role: String (user role: Admin or Customer)
- createdAt: DateTime (user creation timestamp)

Cart:
- cartId: String (unique identifier for the shopping cart)
- customerId: String (reference to Customer userId)
- items: List of CartItem (list of current products in cart)
- status: String (cart status: ACTIVE, CHECKED_OUT)
- createdAt: DateTime (cart creation timestamp)

CartItem:
- productId: String (reference to product)
- quantity: Integer (number of product units added)

Order:
- orderId: String (unique identifier for the order)
- customerId: String (reference to Customer userId)
- items: List of OrderItem (products and quantities purchased)
- status: String (order status: PENDING, COMPLETED, FAILED)
- createdAt: DateTime (order creation timestamp)

OrderItem:
- productId: String (reference to product)
- quantity: Integer (number of product units ordered)
- price: Decimal (price per unit at order time)
```

---

## 2. Entity Workflows

### UserImportJob workflow
```
1. Initial State: UserImportJob created with PENDING status
2. Validation: Validate importData format and required fields
3. Processing: Parse importData and create immutable User entities with roles
4. Completion: Update UserImportJob status to COMPLETED or FAILED
5. Notification: (Optional) Notify Admin of import results
```
```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> PROCESSING : processUserImportJob()
    PROCESSING --> COMPLETED : all users imported
    PROCESSING --> FAILED : import errors
    COMPLETED --> [*]
    FAILED --> [*]
```

---

### Cart workflow
```
1. Triggered on Cart creation or update (add/remove items)
2. Validation: Check product availability for CartItems
3. Processing: Update Cart entity with new items or changes (mutable updates allowed)
4. Completion: Save updated Cart with status ACTIVE
```
```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> PROCESSING : processCart()
    PROCESSING --> ACTIVE : stock validated
    PROCESSING --> FAILED : validation error
    ACTIVE --> [*]
    FAILED --> [*]
```

---

### Order workflow
```
1. Triggered on Order entity creation (checkout)
2. Validation: Embedded checkOrderStockAvailability to ensure stock sufficiency
3. Processing:
   - Deduct product stock based on OrderItems
   - Update Cart status to CHECKED_OUT
   - Update Order status to COMPLETED on success, FAILED on error
4. Completion: Persist Order and related stock adjustments
5. Notification: (Optional) Notify Customer or Admin of order outcome
```
```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> PROCESSING : processOrder()
    PROCESSING --> COMPLETED : stock deducted, cart updated
    PROCESSING --> FAILED : stock validation failed or other error
    COMPLETED --> [*]
    FAILED --> [*]
```

---

## 3. API Endpoints

| Method | Endpoint                                  | Description                         | Request Body                                                                                 | Response                      |
|--------|-------------------------------------------|-----------------------------------|----------------------------------------------------------------------------------------------|-------------------------------|
| POST   | /api/admin/users/import                    | Create UserImportJob to import users (triggers user import event) | `{ "importData": "string" }`                                                                 | `{ "technicalId": "jobId" }`  |
| GET    | /api/admin/users/import/{jobId}            | Get UserImportJob status by jobId | -                                                                                            | Full UserImportJob entity data|
| GET    | /api/users/{userId}                         | Get User by userId                | -                                                                                            | Full User entity data         |
| POST   | /api/customers/{customerId}/cart            | Create or update Cart for customer (triggers Cart processing) | `{ "items": [ { "productId": "string", "quantity": int } ] }`                                | `{ "technicalId": "cartId" }` |
| GET    | /api/customers/{customerId}/cart            | Retrieve current Cart for customer | -                                                                                            | Full Cart entity data         |
| POST   | /api/customers/{customerId}/orders          | Create Order (checkout, triggers Order processing) | `{ "cartId": "string" }`                                                                     | `{ "technicalId": "orderId" }`|
| GET    | /api/customers/{customerId}/orders/{orderId}| Get Order by orderId              | -                                                                                            | Full Order entity data        |

---

## 4. Request/Response JSON Examples

### User Import Job Request

```json
{
  "importData": "[{\"name\":\"Alice\", \"email\":\"alice@example.com\", \"role\":\"Admin\"}, {\"name\":\"Bob\", \"email\":\"bob@example.com\", \"role\":\"Customer\"}]"
}
```

---

### Cart Add/Update Request

```json
{
  "items": [
    { "productId": "prod-123", "quantity": 2 },
    { "productId": "prod-456", "quantity": 1 }
  ]
}
```

---

### Order Checkout Request

```json
{
  "cartId": "cart-789"
}
```

---

## 5. Mermaid Diagram for API Request/Response Flow

```mermaid
sequenceDiagram
    participant Customer
    participant API
    participant UserImportJobEntity as UserImportJob
    participant UserEntity as User
    participant CartEntity as Cart
    participant OrderEntity as Order
    participant StockService

    Customer->>API: POST /api/admin/users/import\n{ "importData": "..." }
    API->>UserImportJobEntity: create UserImportJob entity
    UserImportJobEntity->>UserImportJobEntity: processUserImportJob()
    UserImportJobEntity->>UserEntity: create immutable User entities
    UserImportJobEntity-->>API: return { "technicalId": "jobId" }

    Customer->>API: POST /api/customers/{customerId}/cart\n{ "items": [...] }
    API->>CartEntity: create/update Cart entity (mutable)
    CartEntity->>CartEntity: processCart()
    CartEntity->>StockService: validate stock
    CartEntity-->>API: return { "technicalId": "cartId" }

    Customer->>API: POST /api/customers/{customerId}/orders\n{ "cartId": "cart-789" }
    API->>OrderEntity: create Order entity (immutable)
    OrderEntity->>OrderEntity: processOrder()
    OrderEntity->>StockService: checkOrderStockAvailability
    StockService-->>OrderEntity: validation result
    OrderEntity->>StockService: deduct stock
    OrderEntity->>CartEntity: update Cart status CHECKED_OUT
    OrderEntity-->>API: return { "technicalId": "orderId" }
```

---

This completes the finalized functional requirements for the order management system following Cyoda’s Event-Driven Architecture principles.  
You can use this directly for documentation or implementation purposes.