# Order Controller Requirements

## Base Path
All order endpoints are mapped to `/ui/order/**`

## CRUD Operations

### 1. Create Order
- **Endpoint**: `POST /ui/order`
- **Request Body**: Order entity
- **Response**: EntityWithMetadata<Order> with 201 Created
- **Business Logic**: 
  - Check for duplicate orderId
  - Validate customer and pet exist
  - Validate pet is available
  - Return 409 Conflict if order already exists

### 2. Get Order by Technical ID
- **Endpoint**: `GET /ui/order/{id}`
- **Parameters**: 
  - `id` (UUID) - Technical ID
  - `pointInTime` (OffsetDateTime, optional) - Historical query
- **Response**: EntityWithMetadata<Order> or 404 Not Found

### 3. Get Order by Business ID
- **Endpoint**: `GET /ui/order/business/{orderId}`
- **Parameters**: 
  - `orderId` (String) - Business ID
  - `pointInTime` (OffsetDateTime, optional) - Historical query
- **Response**: EntityWithMetadata<Order> or 404 Not Found

### 4. Update Order
- **Endpoint**: `PUT /ui/order/{id}`
- **Parameters**: 
  - `id` (UUID) - Technical ID
  - `transition` (String, optional) - Workflow transition
- **Request Body**: Order entity
- **Response**: EntityWithMetadata<Order>

### 5. Delete Order
- **Endpoint**: `DELETE /ui/order/{id}`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: 204 No Content

## Search and Filter Operations

### 6. List All Orders
- **Endpoint**: `GET /ui/order`
- **Parameters**: 
  - Pagination: `page`, `size`
  - Filters: `status`, `customerId`, `petId`
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: Page<EntityWithMetadata<Order>>

### 7. Get Orders by Customer
- **Endpoint**: `GET /ui/order/customer/{customerId}`
- **Parameters**: 
  - `customerId` (String) - Customer business ID
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: List<EntityWithMetadata<Order>>

### 8. Get Orders by Pet
- **Endpoint**: `GET /ui/order/pet/{petId}`
- **Parameters**: 
  - `petId` (String) - Pet business ID
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: List<EntityWithMetadata<Order>>

### 9. Get Orders by Status
- **Endpoint**: `GET /ui/order/status/{status}`
- **Parameters**: 
  - `status` (String) - Order status
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: List<EntityWithMetadata<Order>>

## Workflow Transition Operations

### 10. Confirm Order
- **Endpoint**: `POST /ui/order/{id}/confirm`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: EntityWithMetadata<Order>
- **Business Logic**: Triggers "confirm_order" transition

### 11. Prepare Order
- **Endpoint**: `POST /ui/order/{id}/prepare`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: EntityWithMetadata<Order>
- **Business Logic**: Triggers "prepare_order" transition

### 12. Ship Order
- **Endpoint**: `POST /ui/order/{id}/ship`
- **Parameters**: `id` (UUID) - Technical ID
- **Request Body**: ShippingInfo (optional)
- **Response**: EntityWithMetadata<Order>
- **Business Logic**: Triggers "ship_order" transition

### 13. Deliver Order
- **Endpoint**: `POST /ui/order/{id}/deliver`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: EntityWithMetadata<Order>
- **Business Logic**: Triggers "deliver_order" transition

### 14. Cancel Order
- **Endpoint**: `POST /ui/order/{id}/cancel`
- **Parameters**: `id` (UUID) - Technical ID
- **Request Body**: CancellationReason (optional)
- **Response**: EntityWithMetadata<Order>
- **Business Logic**: Triggers "cancel_order" transition

### 15. Return Order
- **Endpoint**: `POST /ui/order/{id}/return`
- **Parameters**: `id` (UUID) - Technical ID
- **Request Body**: ReturnReason (optional)
- **Response**: EntityWithMetadata<Order>
- **Business Logic**: Triggers "return_order" transition

## Additional Operations

### 16. Get Order Change History
- **Endpoint**: `GET /ui/order/{id}/changes`
- **Parameters**: 
  - `id` (UUID) - Technical ID
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: List<EntityChangeMeta>

### 17. Advanced Order Search
- **Endpoint**: `POST /ui/order/search/advanced`
- **Request Body**: OrderSearchRequest
- **Response**: List<EntityWithMetadata<Order>>
- **Search Criteria**: customer, pet, date range, amount range, status, etc.
