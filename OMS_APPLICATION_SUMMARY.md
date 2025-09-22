# Cyoda Order Management System (OMS) - Application Summary

## Overview

This is a complete **Order Management System (OMS)** built using the Cyoda client template with Spring Boot. The application provides a backend API for an e-commerce system with anonymous checkout, dummy payment processing, and inventory management.

## What Was Built

### Core Entities (5 entities with full workflows)

1. **Product** - Complete product catalog with complex schema
   - Full product schema with variants, bundles, inventory, compliance, etc.
   - SKU-based identification with category filtering
   - Inventory tracking with quantity available

2. **Cart** - Shopping cart with line items and totals
   - Anonymous cart creation and management
   - Line item operations (add, update, remove)
   - Automatic totals calculation via processors

3. **Payment** - Dummy payment processing
   - Auto-approval after 3 seconds simulation
   - Status tracking (INITIATED → PAID)
   - Integration with cart checkout flow

4. **Order** - Order lifecycle management
   - ULID-based order numbers
   - Guest contact information capture
   - Status progression (WAITING_TO_FULFILL → DELIVERED)

5. **Shipment** - Single shipment per order tracking
   - Picking and shipping status management
   - Line-level quantity tracking

### Processors (5 processors for business logic)

1. **RecalculateTotals** - Recalculates cart totals after line changes
2. **CreateCartOnFirstAdd** - Initializes new carts
3. **CreateDummyPayment** - Sets up payment entities
4. **AutoMarkPaidAfter3s** - Simulates payment approval delay
5. **CreateOrderFromPaid** - Complex orchestration processor that:
   - Snapshots cart data into order
   - Decrements product inventory
   - Creates shipment entity
   - Manages order lifecycle

### REST API Controllers (4 controllers with 15+ endpoints)

#### Product Controller (`/ui/products`)
- `GET /ui/products` - Search with filters (category, price range, free-text)
- `GET /ui/products/{sku}` - Get full product document
- `POST /ui/products` - Create new product
- `PUT /ui/products/{id}` - Update product

#### Cart Controller (`/ui/cart`)
- `POST /ui/cart` - Create new cart
- `GET /ui/cart/{cartId}` - Get cart details
- `POST /ui/cart/{cartId}/lines` - Add item to cart
- `PATCH /ui/cart/{cartId}/lines` - Update item quantity
- `POST /ui/cart/{cartId}/open-checkout` - Start checkout
- `POST /ui/checkout/{cartId}` - Update guest contact

#### Payment Controller (`/ui/payment`)
- `POST /ui/payment/start` - Start dummy payment
- `GET /ui/payment/{paymentId}` - Poll payment status
- `POST /ui/payment/{paymentId}/cancel` - Cancel payment

#### Order Controller (`/ui/order`)
- `POST /ui/order/create` - Create order from paid payment
- `GET /ui/order/{orderId}` - Get order details
- `PUT /ui/order/{orderId}/status` - Update order status

## Key Features Implemented

### ✅ All Requirements Met

- **Anonymous checkout only** - No user accounts required
- **Payment auto-approval** - 3-second delay simulation
- **Stock decrement policy** - Inventory reduced on order creation
- **Single shipment per order** - One shipment entity per order
- **Short ULID order numbers** - Simplified ULID generation
- **Catalog filtering** - Category, free-text, price range filters
- **Full product schema** - Complete schema persistence and round-trip
- **Slim list views** - Performance-optimized DTOs for product lists

### Architecture Highlights

- **Workflow-driven** - All business logic flows through Cyoda workflows
- **Thin controllers** - Pure API proxies with no business logic
- **Processor-based logic** - Complex operations handled by processors
- **EntityService integration** - Proper CRUD operations with technical IDs
- **Manual transitions** - Explicit workflow state management

## How to Validate It Works

### 1. Build and Compile
```bash
./gradlew build
```
✅ **Expected**: Clean build with no compilation errors

### 2. Start the Application
```bash
./gradlew bootRun
```
✅ **Expected**: Spring Boot starts successfully on port 8080

### 3. API Validation (using curl or Postman)

#### Create a Product
```bash
curl -X POST http://localhost:8080/ui/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "LAPTOP-001",
    "name": "Gaming Laptop",
    "description": "High-performance gaming laptop",
    "price": 1299.99,
    "quantityAvailable": 50,
    "category": "Electronics"
  }'
```

#### Search Products
```bash
curl "http://localhost:8080/ui/products?category=Electronics&minPrice=1000&maxPrice=2000"
```

#### Create Cart and Add Item
```bash
# Create cart
curl -X POST http://localhost:8080/ui/cart

# Add item to cart (use cartId from response)
curl -X POST http://localhost:8080/ui/cart/{cartId}/lines \
  -H "Content-Type: application/json" \
  -d '{"sku": "LAPTOP-001", "qty": 1}'
```

#### Complete Checkout Flow
```bash
# Open checkout
curl -X POST http://localhost:8080/ui/cart/{cartId}/open-checkout

# Add guest contact
curl -X POST http://localhost:8080/ui/checkout/{cartId} \
  -H "Content-Type: application/json" \
  -d '{
    "guestContact": {
      "name": "John Doe",
      "email": "john@example.com",
      "address": {
        "line1": "123 Main St",
        "city": "Anytown",
        "postcode": "12345",
        "country": "US"
      }
    }
  }'

# Start payment
curl -X POST http://localhost:8080/ui/payment/start \
  -H "Content-Type: application/json" \
  -d '{"cartId": "{cartId}"}'

# Poll payment status (wait 3+ seconds)
curl http://localhost:8080/ui/payment/{paymentId}

# Create order
curl -X POST http://localhost:8080/ui/order/create \
  -H "Content-Type: application/json" \
  -d '{"paymentId": "{paymentId}", "cartId": "{cartId}"}'
```

### 4. Swagger UI
Access the API documentation at: `http://localhost:8080/swagger-ui/index.html`

### 5. Expected Happy Path Flow

1. **Product Listing** - Products appear with category/price filters
2. **Cart Creation** - Cart created on first item add
3. **Item Management** - Items added/updated with automatic total calculation
4. **Checkout** - Guest contact captured, cart status changes
5. **Payment** - Payment initiated and auto-approved after 3s
6. **Order Creation** - Order created with ULID, inventory decremented
7. **Shipment** - Shipment created in PICKING status
8. **Status Updates** - Order progresses through fulfillment states

## Technical Architecture

### Entity Structure
```
src/main/java/com/java_template/application/
├── entity/
│   ├── product/version_1/Product.java
│   ├── cart/version_1/Cart.java
│   ├── payment/version_1/Payment.java
│   ├── order/version_1/Order.java
│   └── shipment/version_1/Shipment.java
├── processor/
│   ├── RecalculateTotals.java
│   ├── CreateCartOnFirstAdd.java
│   ├── CreateDummyPayment.java
│   ├── AutoMarkPaidAfter3s.java
│   └── CreateOrderFromPaid.java
└── controller/
    ├── ProductController.java
    ├── CartController.java
    ├── PaymentController.java
    └── OrderController.java
```

### Workflow Definitions
```
src/main/resources/workflow/
├── product/version_1/Product.json
├── cart/version_1/Cart.json
├── payment/version_1/Payment.json
├── order/version_1/Order.json
└── shipment/version_1/Shipment.json
```

## Success Criteria ✅

- [x] **Full compilation** - `./gradlew build` succeeds
- [x] **Requirements coverage** - All functional requirements implemented
- [x] **Workflow compliance** - All transitions follow manual/automatic rules
- [x] **Architecture adherence** - No reflection, thin controllers, proper separation
- [x] **Entity validation** - All entities implement proper validation
- [x] **API completeness** - All required endpoints implemented
- [x] **Business logic** - Complex orchestration in processors
- [x] **Documentation** - Complete application summary provided

The OMS system is ready for deployment and demonstrates a complete e-commerce backend with Cyoda integration.

## Quick API Reference

### Product Management
- **List Products**: `GET /ui/products?search=laptop&category=Electronics&minPrice=500&maxPrice=2000`
- **Get Product**: `GET /ui/products/LAPTOP-001`
- **Create Product**: `POST /ui/products` (with Product JSON)

### Shopping Cart
- **Create Cart**: `POST /ui/cart`
- **View Cart**: `GET /ui/cart/{cartId}`
- **Add Item**: `POST /ui/cart/{cartId}/lines {"sku":"LAPTOP-001","qty":1}`
- **Update Item**: `PATCH /ui/cart/{cartId}/lines {"sku":"LAPTOP-001","qty":2}`
- **Start Checkout**: `POST /ui/cart/{cartId}/open-checkout`

### Payment & Orders
- **Start Payment**: `POST /ui/payment/start {"cartId":"cart-123"}`
- **Check Payment**: `GET /ui/payment/{paymentId}`
- **Create Order**: `POST /ui/order/create {"paymentId":"pay-456","cartId":"cart-123"}`
- **View Order**: `GET /ui/order/{orderId}`

### Status Codes
- **200**: Success
- **400**: Bad Request (validation failed)
- **404**: Not Found (entity doesn't exist)

All endpoints return JSON and support CORS for browser integration.
