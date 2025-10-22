# OMS (Order Management System) Implementation Summary

## Overview
This document describes the complete implementation of a Spring Boot-based Order Management System (OMS) client application that integrates with Cyoda for workflow-driven backend operations. The application exposes REST APIs for a browser UI to manage products, shopping carts, payments, and orders without requiring browser-side authentication.

## Architecture

### Core Principle
- **UI-facing APIs**: All endpoints are under `/ui/**` paths
- **Server-side Credentials**: Cyoda credentials are stored server-side; never exposed to browser
- **Workflow-driven**: All business logic flows through Cyoda workflows
- **Thin Controllers**: Controllers act as pure proxies to EntityService with no embedded business logic

## Implemented Entities

### 1. Product
**Location**: `src/main/java/com/java_template/application/entity/product/version_1/Product.java`

Complete product catalog entity with full schema including:
- Core fields: sku (unique), name, description, price, quantityAvailable, category
- Complex nested structures: attributes, localizations, media, options, variants, bundles, inventory, compliance, relationships, events
- Workflow: `src/main/resources/workflow/product/version_1/Product.json`
  - States: initial → active ↔ inactive
  - Transitions: create_product, update_product, deactivate_product, reactivate_product

### 2. Cart
**Location**: `src/main/java/com/java_template/application/entity/cart/version_1/Cart.java`

Shopping cart entity for anonymous checkout:
- Fields: cartId, lines (CartLine[]), totalItems, grandTotal, guestContact
- Workflow: `src/main/resources/workflow/cart/version_1/Cart.json`
  - States: initial → active → checking_out → converted
  - Transitions: create_on_first_add, add_item, decrement_item, remove_item, open_checkout, checkout
  - All item modifications trigger RecalculateTotals processor

### 3. Payment
**Location**: `src/main/java/com/java_template/application/entity/payment/version_1/Payment.java`

Dummy payment entity with auto-approval:
- Fields: paymentId, cartId, amount, provider ("DUMMY")
- Workflow: `src/main/resources/workflow/payment/version_1/Payment.json`
  - States: initial → initiated → paid | failed | canceled
  - Transitions: start_dummy_payment, auto_mark_paid (auto-approves after ~3s), mark_failed, mark_canceled

### 4. Order
**Location**: `src/main/java/com/java_template/application/entity/order/version_1/Order.java`

Order entity representing a customer order:
- Fields: orderId, orderNumber (short ULID), lines (OrderLine[]), totals, guestContact
- Workflow: `src/main/resources/workflow/order/version_1/Order.json`
  - States: initial → waiting_to_fulfill → picking → waiting_to_send → sent → delivered
  - Transitions: create_order_from_paid, start_picking, ready_to_send, mark_sent, mark_delivered

### 5. Shipment
**Location**: `src/main/java/com/java_template/application/entity/shipment/version_1/Shipment.java`

Single shipment per order with line-level tracking:
- Fields: shipmentId, orderId, lines (ShipmentLine[] with qtyOrdered, qtyPicked, qtyShipped)
- Workflow: `src/main/resources/workflow/shipment/version_1/Shipment.json`
  - States: initial → picking → waiting_to_send → sent → delivered
  - Transitions: create_shipment, ready_to_send, mark_sent, mark_delivered

## Implemented Processors

### 1. RecalculateTotals
**Location**: `src/main/java/com/java_template/application/processor/RecalculateTotals.java`

Recalculates cart totals (totalItems and grandTotal) based on current line items.
- Triggered on: create_on_first_add, add_item, decrement_item, remove_item transitions
- Logic: Sums quantities and prices from all cart lines

### 2. CreateDummyPayment
**Location**: `src/main/java/com/java_template/application/processor/CreateDummyPayment.java`

Initializes a dummy payment with INITIATED status and timestamps.
- Triggered on: start_dummy_payment transition
- Logic: Sets createdAt and updatedAt timestamps

### 3. AutoMarkPaidAfter3s
**Location**: `src/main/java/com/java_template/application/processor/AutoMarkPaidAfter3s.java`

Simulates 3-second delay and marks payment as PAID.
- Triggered on: auto_mark_paid transition
- Logic: Thread.sleep(3000) then updates updatedAt timestamp

### 4. CreateOrderFromPaid
**Location**: `src/main/java/com/java_template/application/processor/CreateOrderFromPaid.java`

Complex processor that orchestrates order creation from paid payment:
1. Snapshots cart lines and guest contact into Order
2. Decrements Product.quantityAvailable for each ordered item
3. Creates a single Shipment in PICKING state
- Triggered on: create_order_from_paid transition
- Uses EntityService to interact with Product and Shipment entities

## Implemented Controllers

### 1. ProductController
**Location**: `src/main/java/com/java_template/application/controller/ProductController.java`

Endpoints:
- `GET /ui/products` - Search with filters (category, free-text, price range), returns slim DTOs
- `GET /ui/products/{sku}` - Get full product details by SKU

### 2. CartController
**Location**: `src/main/java/com/java_template/application/controller/CartController.java`

Endpoints:
- `POST /ui/cart` - Create new cart
- `GET /ui/cart/{cartId}` - Get cart by ID
- `POST /ui/cart/{cartId}/lines` - Add/increment item
- `PATCH /ui/cart/{cartId}/lines` - Update/remove item (qty=0 removes)
- `POST /ui/cart/{cartId}/open-checkout` - Transition to CHECKING_OUT

### 3. PaymentController
**Location**: `src/main/java/com/java_template/application/controller/PaymentController.java`

Endpoints:
- `POST /ui/payment/start` - Start dummy payment (auto-triggers auto_mark_paid)
- `GET /ui/payment/{paymentId}` - Poll payment status

### 4. OrderController
**Location**: `src/main/java/com/java_template/application/controller/OrderController.java`

Endpoints:
- `POST /ui/order/create` - Create order from paid payment (validates payment state, snapshots cart)
- `GET /ui/order/{orderId}` - Get order details

## How to Validate the Implementation

### 1. Build Verification
```bash
./gradlew clean build
```
Expected: BUILD SUCCESSFUL

### 2. Happy Path Workflow
1. **Create Product**: POST to Cyoda with full Product schema
2. **Create Cart**: `POST /ui/cart` → returns cartId
3. **Add Items**: `POST /ui/cart/{cartId}/lines` with sku, name, price, qty
4. **Verify Totals**: `GET /ui/cart/{cartId}` → totalItems and grandTotal recalculated
5. **Add Guest Contact**: Update cart with guestContact via PUT
6. **Open Checkout**: `POST /ui/cart/{cartId}/open-checkout` → state = CHECKING_OUT
7. **Start Payment**: `POST /ui/payment/start` with cartId and amount → returns paymentId
8. **Poll Payment**: `GET /ui/payment/{paymentId}` → after ~3s, state = PAID
9. **Create Order**: `POST /ui/order/create` with paymentId and cartId → returns orderId, orderNumber
10. **Verify Order**: `GET /ui/order/{orderId}` → order created with snapshotted lines and contact
11. **Verify Stock**: Product.quantityAvailable decremented by ordered quantities
12. **Verify Shipment**: Shipment created in PICKING state with line items

### 3. Search Functionality
- `GET /ui/products?search=laptop&category=electronics&minPrice=500&maxPrice=2000&page=0&pageSize=10`
- Returns slim DTOs with sku, name, description, price, quantityAvailable, category, imageUrl

### 4. Workflow Transitions
- Cart: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- Payment: INITIATED → PAID (auto after 3s)
- Order: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
- Shipment: PICKING → WAITING_TO_SEND → SENT → DELIVERED

## Key Design Decisions

1. **Manual Transitions Only**: All updates use manual transitions (never automatic for updates)
2. **Processor Isolation**: CreateOrderFromPaid creates Shipment via EntityService (not current entity)
3. **Stock Decrement**: Happens in processor, not controller (business logic separation)
4. **Slim DTOs**: Product list returns slim DTO for performance; detail returns full schema
5. **Order Number**: Generated as "ORD-" + System.currentTimeMillis() (short ULID-like format)
6. **Anonymous Checkout**: No user authentication; cart and payment tied by cartId

## Files Created

### Entities (5 files)
- Product.java
- Cart.java
- Payment.java
- Order.java
- Shipment.java

### Workflows (5 JSON files)
- Product.json
- Cart.json
- Payment.json
- Order.json
- Shipment.json

### Processors (4 files)
- RecalculateTotals.java
- CreateDummyPayment.java
- AutoMarkPaidAfter3s.java
- CreateOrderFromPaid.java

### Controllers (4 files)
- ProductController.java
- CartController.java
- PaymentController.java
- OrderController.java

## Build Status
✅ All code compiles successfully
✅ All tests pass
✅ Ready for deployment

