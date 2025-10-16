# Customer Controller Requirements

## Base Path
All customer endpoints are mapped to `/ui/customer/**`

## CRUD Operations

### 1. Create Customer
- **Endpoint**: `POST /ui/customer`
- **Request Body**: Customer entity
- **Response**: EntityWithMetadata<Customer> with 201 Created
- **Business Logic**: 
  - Check for duplicate customerId, username, and email
  - Validate required fields
  - Return 409 Conflict if customer already exists

### 2. Get Customer by Technical ID
- **Endpoint**: `GET /ui/customer/{id}`
- **Parameters**: 
  - `id` (UUID) - Technical ID
  - `pointInTime` (OffsetDateTime, optional) - Historical query
- **Response**: EntityWithMetadata<Customer> or 404 Not Found

### 3. Get Customer by Business ID
- **Endpoint**: `GET /ui/customer/business/{customerId}`
- **Parameters**: 
  - `customerId` (String) - Business ID
  - `pointInTime` (OffsetDateTime, optional) - Historical query
- **Response**: EntityWithMetadata<Customer> or 404 Not Found

### 4. Get Customer by Username
- **Endpoint**: `GET /ui/customer/username/{username}`
- **Parameters**: 
  - `username` (String) - Username
  - `pointInTime` (OffsetDateTime, optional) - Historical query
- **Response**: EntityWithMetadata<Customer> or 404 Not Found

### 5. Update Customer
- **Endpoint**: `PUT /ui/customer/{id}`
- **Parameters**: 
  - `id` (UUID) - Technical ID
  - `transition` (String, optional) - Workflow transition
- **Request Body**: Customer entity
- **Response**: EntityWithMetadata<Customer>

### 6. Delete Customer
- **Endpoint**: `DELETE /ui/customer/{id}`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: 204 No Content

## Search and Filter Operations

### 7. List All Customers
- **Endpoint**: `GET /ui/customer`
- **Parameters**: 
  - Pagination: `page`, `size`
  - Filters: `status`, `city`, `state`
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: Page<EntityWithMetadata<Customer>>

### 8. Search Customers by Name
- **Endpoint**: `GET /ui/customer/search`
- **Parameters**: 
  - `name` (String) - First or last name to search
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: List<EntityWithMetadata<Customer>>

### 9. Search Customers by Email
- **Endpoint**: `GET /ui/customer/search/email`
- **Parameters**: 
  - `email` (String) - Email to search
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: List<EntityWithMetadata<Customer>>

## Workflow Transition Operations

### 10. Deactivate Customer
- **Endpoint**: `POST /ui/customer/{id}/deactivate`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: EntityWithMetadata<Customer>
- **Business Logic**: Triggers "deactivate_customer" transition

### 11. Reactivate Customer
- **Endpoint**: `POST /ui/customer/{id}/reactivate`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: EntityWithMetadata<Customer>
- **Business Logic**: Triggers "reactivate_customer" transition

### 12. Suspend Customer
- **Endpoint**: `POST /ui/customer/{id}/suspend`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: EntityWithMetadata<Customer>
- **Business Logic**: Triggers "suspend_customer" transition

### 13. Unsuspend Customer
- **Endpoint**: `POST /ui/customer/{id}/unsuspend`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: EntityWithMetadata<Customer>
- **Business Logic**: Triggers "unsuspend_customer" transition

## Additional Operations

### 14. Get Customer Change History
- **Endpoint**: `GET /ui/customer/{id}/changes`
- **Parameters**: 
  - `id` (UUID) - Technical ID
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: List<EntityChangeMeta>

### 15. Advanced Customer Search
- **Endpoint**: `POST /ui/customer/search/advanced`
- **Request Body**: CustomerSearchRequest
- **Response**: List<EntityWithMetadata<Customer>>
- **Search Criteria**: name, email, city, state, loyaltyPoints range, etc.

### 16. Customer Login
- **Endpoint**: `POST /ui/customer/login`
- **Request Body**: LoginRequest (username, password)
- **Response**: Customer authentication result
- **Business Logic**: Validates credentials and updates last login
