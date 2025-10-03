# Order Management System (OMS) Implementation Summary

## Overview

This document describes the complete implementation of a Cyoda-based Order Management System (OMS) backend that provides REST APIs for a browser UI. The system supports anonymous checkout, dummy payment processing, and order fulfillment workflows.

## Architecture

The application follows the Cyoda client template architecture with:
- **Entities**: Domain objects implementing `CyodaEntity`
- **Workflows**: JSON-defined state machines for business processes
- **Processors**: Business logic components implementing `CyodaProcessor`
- **Controllers**: REST API endpoints under `/ui/**`

## Implemented Entities

### 1. Product (`src/main/java/com/java_template/application/entity/product/version_1/Product.java`)
- **Business ID**: `sku` (unique product identifier)
- **Complete Schema**: Implements the full product schema with attributes, localizations, media, options, variants, bundles, inventory, compliance, relationships, and events
- **Key Fields**: sku, name, description, price, quantityAvailable, category
- **Workflow**: Simple active/inactive states

### 2. Cart (`src/main/java/com/java_template/application/entity/cart/version_1/Cart.java`)
- **Business ID**: `cartId`
- **Status Flow**: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- **Key Fields**: cartId, status, lines, totalItems, grandTotal, guestContact
- **Features**: Line items with automatic total calculation

### 3. Payment (`src/main/java/com/java_template/application/entity/payment/version_1/Payment.java`)
- **Business ID**: `paymentId`
- **Status Flow**: INITIATED → PAID/FAILED/CANCELED
- **Key Fields**: paymentId, cartId, amount, status, provider
- **Provider**: DUMMY (auto-approves after 3 seconds)

### 4. Order (`src/main/java/com/java_template/application/entity/order/version_1/Order.java`)
- **Business ID**: `orderId`
- **Status Flow**: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
- **Key Fields**: orderId, orderNumber (short ULID), status, lines, totals, guestContact
- **Features**: Snapshots cart data, includes guest contact for fulfillment

### 5. Shipment (`src/main/java/com/java_template/application/entity/shipment/version_1/Shipment.java`)
- **Business ID**: `shipmentId`
- **Status Flow**: PICKING → WAITING_TO_SEND → SENT → DELIVERED
- **Key Fields**: shipmentId, orderId, status, lines
- **Features**: Single shipment per order, tracks picked/shipped quantities

## Implemented Processors

### 1. RecalculateTotals (`src/main/java/com/java_template/application/processor/RecalculateTotals.java`)
- **Entity**: Cart
- **Purpose**: Recalculates line totals, total items count, and grand total
- **Triggers**: add_item, decrement_item, remove_item, create_on_first_add

### 2. CreateDummyPayment (`src/main/java/com/java_template/application/processor/CreateDummyPayment.java`)
- **Entity**: Payment
- **Purpose**: Initializes payment with INITIATED status and DUMMY provider
- **Triggers**: start_dummy_payment

### 3. AutoMarkPaidAfter3s (`src/main/java/com/java_template/application/processor/AutoMarkPaidAfter3s.java`)
- **Entity**: Payment
- **Purpose**: Automatically marks payment as PAID after 3-second delay
- **Triggers**: auto_mark_paid (automatic transition)

### 4. CreateOrderFromPaid (`src/main/java/com/java_template/application/processor/CreateOrderFromPaid.java`)
- **Entity**: Order
- **Purpose**: Creates order from paid payment, decrements product stock, creates shipment
- **Triggers**: create_order_from_paid
- **Side Effects**: Updates Product.quantityAvailable, creates Shipment entity

## REST API Endpoints

### Product APIs (`/ui/products`)
- `GET /ui/products` - Search products with filters (category, price range, free-text)
- `GET /ui/products/{id}` - Get product by technical UUID
- `GET /ui/products/sku/{sku}` - Get product by SKU
- `POST /ui/products` - Create new product
- `PUT /ui/products/{id}` - Update product
- `DELETE /ui/products/{id}` - Delete product

### Cart APIs (`/ui/cart`)
- `POST /ui/cart` - Create new cart
- `GET /ui/cart/{cartId}` - Get cart by ID
- `POST /ui/cart/{cartId}/lines` - Add/increment item
- `PATCH /ui/cart/{cartId}/lines` - Update/remove item
- `POST /ui/cart/{cartId}/open-checkout` - Set cart to CHECKING_OUT

### Checkout APIs (`/ui/checkout`)
- `POST /ui/checkout/{cartId}` - Submit checkout with guest contact info

### Payment APIs (`/ui/payment`)
- `POST /ui/payment/start` - Start dummy payment
- `GET /ui/payment/{paymentId}` - Poll payment status
- `POST /ui/payment/{paymentId}/cancel` - Cancel payment

### Order APIs (`/ui/order`)
- `POST /ui/order/create` - Create order from paid payment
- `GET /ui/order/{orderId}` - Get order details
- `PUT /ui/order/{orderId}/status` - Update order status

## Key Features

### Anonymous Checkout
- No user accounts required
- Guest contact information collected during checkout
- Address validation for shipping

### Dummy Payment Processing
- Auto-approval after 3 seconds
- Status polling for UI updates
- Cancellation support

### Stock Management
- Real-time stock checking during cart operations
- Automatic stock decrement on order creation
- No reservations (immediate decrement policy)

### Order Fulfillment
- Single shipment per order
- Manual status transitions for warehouse operations
- Complete audit trail through workflow states

### Performance Optimizations
- Slim DTOs for product list views
- Technical UUID usage for fast lookups
- Efficient search with Cyoda conditions

## Workflow Definitions

All workflows are defined in `src/main/resources/workflow/*/version_1/*.json`:
- **Product.json**: Simple active/inactive lifecycle
- **Cart.json**: Shopping cart with total recalculation
- **Payment.json**: Dummy payment with auto-approval
- **Order.json**: Order fulfillment lifecycle
- **Shipment.json**: Shipment tracking lifecycle

## Validation and Testing

- ✅ All entities compile successfully
- ✅ All processors implement required interfaces
- ✅ Workflow validation passes (4 processors, 0 criteria)
- ✅ Full build successful with tests
- ✅ All functional requirements implemented

## How to Validate the Implementation

1. **Build the application**:
   ```bash
   ./gradlew build
   ```

2. **Validate workflows**:
   ```bash
   ./gradlew validateWorkflowImplementations
   ```

3. **Start the application**:
   ```bash
   ./gradlew bootRun
   ```

4. **Access Swagger UI**:
   - Navigate to `http://localhost:8080/swagger-ui/index.html`
   - Test all `/ui/**` endpoints

## Happy Path Flow

1. **Browse Products**: `GET /ui/products?category=electronics`
2. **Create Cart**: `POST /ui/cart`
3. **Add Items**: `POST /ui/cart/{cartId}/lines`
4. **Open Checkout**: `POST /ui/cart/{cartId}/open-checkout`
5. **Submit Checkout**: `POST /ui/checkout/{cartId}` (with guest contact)
6. **Start Payment**: `POST /ui/payment/start`
7. **Poll Payment**: `GET /ui/payment/{paymentId}` (until PAID)
8. **Create Order**: `POST /ui/order/create`
9. **Track Order**: `GET /ui/order/{orderId}`

## Security

- No browser authentication required
- Server-side Cyoda credentials only
- CORS enabled for all origins (development setup)
- All business logic secured in backend workflows

## Next Steps

The implementation is complete and ready for:
- Integration with a frontend UI
- Connection to a Cyoda environment
- Additional business rules and validations
- Performance testing and optimization
