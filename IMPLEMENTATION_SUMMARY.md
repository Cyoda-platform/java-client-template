# Cyoda OMS Backend Implementation Summary

## Overview
This document describes the implementation of a Spring Boot client application that exposes REST APIs for a browser UI to interact with a Cyoda-based Order Management System (OMS). The application handles product catalog, shopping cart, payment processing, and order fulfillment workflows.

## Architecture

### Core Components Implemented

#### 1. **Entities** (5 total)
All entities implement the `CyodaEntity` interface and are located in `src/main/java/com/java_template/application/entity/`:

- **Product** (`product/version_1/Product.java`)
  - Full schema with attributes, localizations, media, options, variants, bundles, inventory, compliance, relationships, and events
  - Required fields: sku (unique), name, description, price, quantityAvailable, category
  - Supports complex nested structures for comprehensive product management

- **Cart** (`cart/version_1/Cart.java`)
  - Business ID: cartId
  - Contains cart lines with sku, name, price, qty
  - Tracks totalItems and grandTotal
  - Supports guest contact information

- **Payment** (`payment/version_1/Payment.java`)
  - Business ID: paymentId
  - Links to cart via cartId
  - Tracks amount and provider (DUMMY for demo)
  - State-driven workflow: INITIATED → PAID/FAILED/CANCELED

- **Order** (`order/version_1/Order.java`)
  - Business ID: orderId
  - Contains orderNumber (short ULID format)
  - Snapshots cart lines and guest contact
  - Tracks totals (items count and grand total)

- **Shipment** (`shipment/version_1/Shipment.java`)
  - Business ID: shipmentId
  - Links to order via orderId
  - Tracks shipment lines with qty ordered/picked/shipped
  - Single shipment per order (demo requirement)

#### 2. **Workflows** (5 total)
All workflows are located in `src/main/resources/workflow/{entity}/version_1/{Entity}.json`:

- **Product Workflow**
  - States: initial → active ↔ inactive
  - Supports product lifecycle management

- **Cart Workflow**
  - States: initial → active → checking_out → converted
  - Transitions: create_on_first_add, add_item, decrement_item, remove_item, open_checkout, checkout
  - RecalculateTotals processor on all item modifications

- **Payment Workflow**
  - States: initial → initiated → paid/failed/canceled
  - AutoMarkPaidAfter3s processor simulates dummy payment approval
  - 3-second delay before auto-marking as PAID

- **Order Workflow**
  - States: initial → waiting_to_fulfill → picking → waiting_to_send → sent → delivered
  - CreateOrderFromPaid processor handles order creation from paid payments
  - Decrements product quantities and creates shipment

- **Shipment Workflow**
  - States: initial → picking → waiting_to_send → sent → delivered
  - Mirrors order fulfillment stages

#### 3. **Processors** (3 total)
Located in `src/main/java/com/java_template/application/processor/`:

- **RecalculateTotals**
  - Recalculates cart totals (totalItems, grandTotal) from cart lines
  - Triggered on: add_item, decrement_item, remove_item transitions

- **AutoMarkPaidAfter3s**
  - Simulates dummy payment provider auto-approval
  - Sleeps for 3 seconds then marks payment as PAID
  - Triggered on: auto_mark_paid transition

- **CreateOrderFromPaid**
  - Creates order from paid payment
  - Snapshots cart lines and guest contact into order
  - Decrements Product.quantityAvailable for each ordered item
  - Creates single Shipment in PICKING state
  - Triggered on: create_order_from_paid transition

#### 4. **REST Controllers** (4 total)
Located in `src/main/java/com/java_template/application/controller/`:

- **ProductController** (`/ui/products`)
  - GET `/ui/products/{sku}` - Get full product document
  - GET `/ui/products` - List products with search, category filter, price range filter
  - Returns slim DTO for list (sku, name, description, price, quantityAvailable, category, imageUrl)
  - Returns full document for detail view

- **CartController** (`/ui/cart`)
  - POST `/ui/cart` - Create new cart
  - GET `/ui/cart/{cartId}` - Get cart
  - POST `/ui/cart/{cartId}/lines` - Add/increment item
  - PATCH `/ui/cart/{cartId}/lines` - Update/remove item (qty=0 removes)
  - POST `/ui/cart/{cartId}/open-checkout` - Transition to CHECKING_OUT

- **CheckoutController** (`/ui/checkout`)
  - POST `/ui/checkout/{cartId}` - Attach guest contact and proceed with checkout

- **PaymentController** (`/ui/payment`)
  - POST `/ui/payment/start` - Create payment (INITIATED state)
  - GET `/ui/payment/{paymentId}` - Get payment status

- **OrderController** (`/ui/order`)
  - POST `/ui/order/create` - Create order from paid payment
  - GET `/ui/order/{orderId}` - Get order details

## Key Features

### 1. **Product Management**
- Full schema persistence with all complex nested structures
- Search by name/description (free-text CONTAINS)
- Filter by category (EQUALS)
- Filter by price range (GREATER_OR_EQUAL, LESS_OR_EQUAL)
- Pagination support for list endpoint

### 2. **Shopping Cart**
- Anonymous checkout (no user accounts)
- Dynamic line item management (add, update, remove)
- Automatic total recalculation
- Guest contact attachment before checkout

### 3. **Payment Processing**
- Dummy payment provider with auto-approval
- 3-second delay simulates processing
- State tracking: INITIATED → PAID/FAILED/CANCELED

### 4. **Order Fulfillment**
- Order creation from paid payments
- Automatic stock decrement on order creation
- Single shipment per order
- Fulfillment workflow: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED

## Validation & Testing

### Build Status
✅ **BUILD SUCCESSFUL** - All code compiles without errors
- Java 21 compatible
- Spring Boot 3.5.3
- Gradle 8.7

### Workflow Validation
✅ **ALL WORKFLOW IMPLEMENTATIONS VALIDATED SUCCESSFULLY**
- 5 workflow files checked
- 3 processors referenced and found
- 0 criteria referenced
- All transitions properly configured

## How to Validate

### 1. **Compile the Project**
```bash
./gradlew clean compileJava
```

### 2. **Run Full Build**
```bash
./gradlew build
```

### 3. **Validate Workflow Implementations**
```bash
./gradlew validateWorkflowImplementations
```

### 4. **Run Tests**
```bash
./gradlew test
```

## API Usage Examples

### Create Cart
```bash
POST /ui/cart
Response: { "entity": { "cartId": "...", "lines": [], "totalItems": 0, "grandTotal": 0 }, "metadata": {...} }
```

### List Products
```bash
GET /ui/products?search=laptop&category=electronics&minPrice=100&maxPrice=2000&page=0&size=20
Response: Page of ProductSlimDTO objects
```

### Add Item to Cart
```bash
POST /ui/cart/{cartId}/lines
Body: { "sku": "SKU-001", "name": "Product Name", "price": 99.99, "qty": 1 }
```

### Start Payment
```bash
POST /ui/payment/start
Body: { "cartId": "..." }
Response: { "paymentId": "..." }
```

### Create Order
```bash
POST /ui/order/create
Body: { "paymentId": "...", "cartId": "..." }
Response: { "orderId": "...", "orderNumber": "ORD-...", "status": "waiting_to_fulfill" }
```

## Technical Decisions

1. **Order Number Generation**: Uses timestamp-based format (ORD-{timestamp}) for simplicity
2. **Slim DTO Pattern**: Product list returns lightweight DTO for performance; detail returns full document
3. **Processor Design**: Processors handle business logic (totals, payment approval, order creation)
4. **State Management**: Entity state is managed by workflow; business fields are separate
5. **Error Handling**: Uses RFC 7807 ProblemDetail for consistent error responses

## Completion Status

✅ All entities implemented with full schema
✅ All workflows configured with proper states and transitions
✅ All processors implemented and validated
✅ All REST controllers with required endpoints
✅ Project compiles successfully
✅ Workflow implementations validated
✅ Ready for integration with Cyoda backend

