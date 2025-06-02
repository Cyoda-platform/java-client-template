```markdown
# Functional Requirements and API Design

## Main Entity: Order

---

## API Endpoints

### 1. Create Order / Trigger Workflow  
`POST /orders`  
- Description: Create a new order and trigger associated workflows (e.g., validation, external data fetch, calculations)  
- Request Body (JSON):  
```json
{
  "customerId": "string",
  "items": [
    {
      "productId": "string",
      "quantity": "integer"
    }
  ],
  "orderDate": "string (ISO8601 date-time)"
}
```  
- Response Body (JSON):  
```json
{
  "orderId": "string",
  "status": "string",
  "workflowStatus": "string"
}
```

---

### 2. Update Order / Trigger Workflow  
`POST /orders/{orderId}`  
- Description: Update existing order details and trigger workflows (e.g., re-calculation, status update)  
- Request Body (JSON):  
```json
{
  "items": [
    {
      "productId": "string",
      "quantity": "integer"
    }
  ],
  "status": "string"
}
```  
- Response Body (JSON):  
```json
{
  "orderId": "string",
  "status": "string",
  "workflowStatus": "string"
}
```

---

### 3. Retrieve Order Details  
`GET /orders/{orderId}`  
- Description: Retrieve order details and current status (read-only, no external data fetch or calculations)  
- Response Body (JSON):  
```json
{
  "orderId": "string",
  "customerId": "string",
  "items": [
    {
      "productId": "string",
      "quantity": "integer"
    }
  ],
  "orderDate": "string (ISO8601 date-time)",
  "status": "string",
  "workflowStatus": "string"
}
```

---

### 4. List Orders by Customer  
`GET /customers/{customerId}/orders`  
- Description: List all orders for a customer (read-only)  
- Response Body (JSON):  
```json
[
  {
    "orderId": "string",
    "orderDate": "string (ISO8601 date-time)",
    "status": "string"
  }
]
```

---

## Business Logic Notes  
- All external data retrieval or calculations triggered by order creation or updates are handled asynchronously in the POST endpoints.  
- GET endpoints are strictly for retrieving stored results without triggering workflows or external calls.

---

## User-App Interaction Sequence Diagram

```mermaid
sequenceDiagram
  participant User
  participant App
  participant WorkflowEngine
  participant ExternalService

  User->>App: POST /orders (create order)
  App->>WorkflowEngine: Trigger workflow (validate, enrich)
  WorkflowEngine->>ExternalService: Request external data
  ExternalService-->>WorkflowEngine: Return data
  WorkflowEngine-->>App: Workflow complete with status
  App-->>User: Return orderId and status

  User->>App: GET /orders/{orderId}
  App-->>User: Return order details and status
```

---

## Order Update and Workflow Trigger

```mermaid
sequenceDiagram
  participant User
  participant App
  participant WorkflowEngine
  participant ExternalService

  User->>App: POST /orders/{orderId} (update order)
  App->>WorkflowEngine: Trigger workflow (recalculate, update status)
  WorkflowEngine->>ExternalService: Request updated data
  ExternalService-->>WorkflowEngine: Return updated data
  WorkflowEngine-->>App: Workflow complete with updated status
  App-->>User: Return updated order status
```

---

## Order Retrieval by Customer

```mermaid
sequenceDiagram
  participant User
  participant App

  User->>App: GET /customers/{customerId}/orders
  App-->>User: Return list of orders
```
```
