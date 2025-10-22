# OMS (Order Management System) Implementation Summary

## Overview
This document describes the complete implementation of a Spring Boot client application that exposes REST APIs for an Order Management System (OMS) with Cyoda backend integration. The application supports anonymous checkout, dummy payment auto-approval, inventory management, and order fulfillment workflows.

## Architecture

### Technology Stack
- **Framework**: Spring Boot 3.5.3
- **Language**: Java 21
- **Build Tool**: Gradle 8.7
- **Backend**: Cyoda Cloud API (gRPC)
- **ORM Pattern**: EntityService with Cyoda REST API integration

### Design Principles
- **Interface-based Design**: All entities implement `CyodaEntity` interface
- **Workflow-driven**: Business logic flows through Cyoda workflows
- **Thin Controllers**: Controllers are pure proxies to EntityService with no business logic
- **Manual Transitions**: Entity updates use explicit manual transitions
- **No Reflection**: Pure Java without reflection

## Implemented Entities

### 1. Product
**Location**: `src/main/java/com/java_template/application/entity/product/version_1/Product.java`

**Purpose**: Represents catalog items with comprehensive schema including attributes, localizations, media, options, variants, bundles, inventory, compliance, relationships, and events.

**Key Fields**:
- `sku` (unique identifier)
- `name`, `description`, `price`, `quantityAvailable`, `category`
- Complex nested structures for full e-commerce product data

**Workflow**: `src/main/resources/workflow/product/version_1/Product.json`
- States: `initial` → `active`
- Transitions: `create_product`, `update_product`, `decrement_quantity`

**Controller**: `ProductController` at `/ui/products`
- GET `/ui/products` - List with filtering (category, search, price range)
- GET `/ui/products/sku/{sku}` - Get full product document
- POST `/ui/products` - Create product
- PUT `/ui/products/{id}` - Update product

### 2. Cart
**Location**: `src/main/java/com/java_template/application/entity/cart/version_1/Cart.java`

**Purpose**: Shopping cart for anonymous users with line items and guest contact information.

**Key Fields**:
- `cartId` (business identifier)
- `lines` (array of CartLine items)
- `totalItems`, `grandTotal` (calculated by processor)
- `guestContact` (optional guest information)

**Workflow**: `src/main/resources/workflow/cart/version_1/Cart.json`
- States: `initial` → `active` → `checking_out` → `converted`
- Transitions: `create_on_first_add`, `add_item`, `decrement_item`, `remove_item`, `open_checkout`, `checkout`

**Processor**: `RecalculateTotals`
- Recalculates `totalItems` and `grandTotal` based on line items
- Triggered on all cart modifications

**Controller**: `CartController` at `/ui/cart`
- POST `/ui/cart` - Create or get cart
- GET `/ui/cart/{cartId}` - Get cart
- POST `/ui/cart/{cartId}/lines` - Add item
- PATCH `/ui/cart/{cartId}/lines` - Update/remove item
- POST `/ui/cart/{cartId}/open-checkout` - Open checkout

### 3. Payment
**Location**: `src/main/java/com/java_template/application/entity/payment/version_1/Payment.java`

**Purpose**: Dummy payment with auto-approval after ~3 seconds.

**Key Fields**:
- `paymentId` (business identifier)
- `cartId` (reference to cart)
- `amount` (payment amount)
- `provider` (always "DUMMY")

**Workflow**: `src/main/resources/workflow/payment/version_1/Payment.json`
- States: `initial` → `initiated` → `paid` | `failed` | `canceled`
- Transitions: `start_dummy_payment`, `auto_mark_paid`

**Processors**:
- `CreateDummyPayment`: Initializes payment in INITIATED state
- `AutoMarkPaidAfter3s`: Simulates 3-second delay then marks as PAID

**Controller**: `PaymentController` at `/ui/payment`
- POST `/ui/payment/start` - Start dummy payment
- GET `/ui/payment/{paymentId}` - Get payment status

### 4. Order
**Location**: `src/main/java/com/java_template/application/entity/order/version_1/Order.java`

**Purpose**: Customer order with line items, totals, and guest contact information.

**Key Fields**:
- `orderId` (business identifier)
- `orderNumber` (short ULID)
- `lines` (array of OrderLine items)
- `totals` (items count and grand total)
- `guestContact` (guest information)

**Workflow**: `src/main/resources/workflow/order/version_1/Order.json`
- States: `initial` → `waiting_to_fulfill` → `picking` → `waiting_to_send` → `sent` → `delivered`
- Transitions: `create_order_from_paid`, `ready_to_send`, `mark_sent`, `mark_delivered`

**Processor**: `CreateOrderFromPaid`
- Snapshots cart lines and guest contact into Order
- Decrements Product.quantityAvailable for each ordered item
- Creates single Shipment in PICKING state

**Controller**: `OrderController` at `/ui/order`
- POST `/ui/order/create` - Create order from paid payment
- GET `/ui/order/{orderId}` - Get order
- GET `/ui/order/number/{orderNumber}` - Get order by number

### 5. Shipment
**Location**: `src/main/java/com/java_template/application/entity/shipment/version_1/Shipment.java`

**Purpose**: Single shipment per order tracking quantities ordered, picked, and shipped.

**Key Fields**:
- `shipmentId` (business identifier)
- `orderId` (reference to order)
- `lines` (array of ShipmentLine items)

**Workflow**: `src/main/resources/workflow/shipment/version_1/Shipment.json`
- States: `initial` → `picking` → `waiting_to_send` → `sent` → `delivered`
- Transitions: `start_picking`, `ready_to_send`, `mark_sent`, `mark_delivered`

**Controller**: `ShipmentController` at `/ui/shipment`
- GET `/ui/shipment/{shipmentId}` - Get shipment
- GET `/ui/shipment/order/{orderId}` - Get shipment by order
- PUT `/ui/shipment/{shipmentId}` - Update shipment

## API Endpoints Summary

### Products
- `GET /ui/products?search=&category=&minPrice=&maxPrice=&page=0&pageSize=20` - List with filters
- `GET /ui/products/sku/{sku}` - Get full product
- `POST /ui/products` - Create product
- `PUT /ui/products/{id}` - Update product
- `DELETE /ui/products/{id}` - Delete product

### Cart
- `POST /ui/cart` - Create cart
- `GET /ui/cart/{cartId}` - Get cart
- `POST /ui/cart/{cartId}/lines` - Add item
- `PATCH /ui/cart/{cartId}/lines` - Update/remove item
- `POST /ui/cart/{cartId}/open-checkout` - Open checkout

### Payment
- `POST /ui/payment/start` - Start payment
- `GET /ui/payment/{paymentId}` - Get payment status

### Order
- `POST /ui/order/create` - Create order from paid payment
- `GET /ui/order/{orderId}` - Get order
- `GET /ui/order/number/{orderNumber}` - Get order by number

### Shipment
- `GET /ui/shipment/{shipmentId}` - Get shipment
- `GET /ui/shipment/order/{orderId}` - Get shipment by order
- `PUT /ui/shipment/{shipmentId}` - Update shipment

## Key Features Implemented

### 1. Anonymous Checkout
- No user authentication required
- Cart creation with guest contact information
- Guest contact attached to order

### 2. Dummy Payment Auto-Approval
- Payment created in INITIATED state
- AutoMarkPaidAfter3s processor simulates 3-second delay
- Payment automatically transitions to PAID state

### 3. Inventory Management
- Product quantity decremented on order creation
- Stock policy: decrement on order creation (no reservations)
- Quantity available tracked in Product entity

### 4. Order Fulfillment
- Single shipment per order
- Shipment created automatically with order
- Order and shipment progress through workflow states

### 5. Product Catalog Filtering
- Free-text search on name and description
- Category filtering
- Price range filtering (minPrice, maxPrice)
- Slim DTO for list views, full document for detail views

## Build and Deployment

### Build
```bash
./gradlew build
```

### Compilation
```bash
./gradlew clean compileJava
```

### Run
```bash
./gradlew bootRun
```

### Access
- Application: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Testing the Happy Path

1. **Create Product**: POST `/ui/products` with product data
2. **List Products**: GET `/ui/products?category=electronics`
3. **Create Cart**: POST `/ui/cart`
4. **Add Item**: POST `/ui/cart/{cartId}/lines` with SKU and quantity
5. **Open Checkout**: POST `/ui/cart/{cartId}/open-checkout`
6. **Start Payment**: POST `/ui/payment/start` with cartId and amount
7. **Wait 3 seconds**: Payment auto-approves
8. **Create Order**: POST `/ui/order/create` with paymentId and cartId
9. **Verify Stock**: GET `/ui/products/sku/{sku}` - quantityAvailable decremented
10. **Check Shipment**: GET `/ui/shipment/order/{orderId}`

## Project Structure

```
src/main/java/com/java_template/
├── Application.java
├── application/
│   ├── entity/
│   │   ├── product/version_1/Product.java
│   │   ├── cart/version_1/Cart.java
│   │   ├── payment/version_1/Payment.java
│   │   ├── order/version_1/Order.java
│   │   └── shipment/version_1/Shipment.java
│   ├── processor/
│   │   ├── RecalculateTotals.java
│   │   ├── CreateDummyPayment.java
│   │   ├── AutoMarkPaidAfter3s.java
│   │   └── CreateOrderFromPaid.java
│   └── controller/
│       ├── ProductController.java
│       ├── CartController.java
│       ├── PaymentController.java
│       ├── OrderController.java
│       └── ShipmentController.java
└── common/ (Framework - DO NOT MODIFY)

src/main/resources/
└── workflow/
    ├── product/version_1/Product.json
    ├── cart/version_1/Cart.json
    ├── payment/version_1/Payment.json
    ├── order/version_1/Order.json
    └── shipment/version_1/Shipment.json
```

## Compliance with Requirements

✅ Anonymous checkout only (no user accounts)
✅ Dummy payment auto-approves after ~3 seconds
✅ Stock policy: decrement Product.quantityAvailable on order creation
✅ Single shipment per order
✅ Order number: short ULID
✅ Catalog filters: category, free-text (name/description), price range
✅ Product schema: full persistence and round-trip
✅ All REST APIs under /ui/** prefix
✅ Server-side Cyoda credentials (never exposed to browser)
✅ Swagger UI available at /swagger-ui/index.html

## Build Status

✅ **BUILD SUCCESSFUL** - All compilation and tests pass
- Java 21 compilation successful
- All entities properly implement CyodaEntity interface
- All processors implement CyodaProcessor interface
- All controllers follow thin proxy pattern
- No reflection used
- No modifications to common/ directory

