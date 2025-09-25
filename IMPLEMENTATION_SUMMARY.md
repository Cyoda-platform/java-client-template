# Cyoda OMS Backend Implementation Summary

## Overview
This project implements a complete Order Management System (OMS) backend using Spring Boot and the Cyoda platform. The application provides REST APIs for a browser UI to manage products, shopping carts, payments, orders, and shipments without requiring browser authentication.

## Architecture

### Core Entities
1. **Product** - Complete product catalog with complex schema including attributes, localizations, media, options, variants, bundles, inventory, compliance, relationships, and events
2. **Cart** - Shopping cart with line items, totals, and guest contact information
3. **Payment** - Dummy payment processing with auto-approval after 3 seconds
4. **Order** - Order management with ULID order numbers and guest contact
5. **Shipment** - Single shipment per order for demo purposes

### Workflow States
- **Product**: initial → active ↔ inactive
- **Cart**: initial → active → checking_out → converted
- **Payment**: initial → initiated → paid/failed/canceled
- **Order**: initial → waiting_to_fulfill → picking → waiting_to_send → sent → delivered
- **Shipment**: initial → picking → waiting_to_send → sent → delivered

### Processors
- **RecalculateTotals** - Recalculates cart totals when items are added/removed
- **CreateDummyPayment** - Initializes dummy payment with provider
- **AutoMarkPaidAfter3s** - Auto-approves payment after 3-second delay
- **CreateOrderFromPaid** - Creates order from paid cart, decrements stock, creates shipment

## API Endpoints

### Products (`/ui/products`)
- `GET /ui/products` - Search products with filters (search, category, price range, pagination)
- `GET /ui/products/{sku}` - Get full product document by SKU
- `POST /ui/products` - Create new product
- `PUT /ui/products/{id}` - Update product
- `DELETE /ui/products/{id}` - Delete product

### Cart (`/ui/cart`)
- `POST /ui/cart` - Create new cart
- `GET /ui/cart/{cartId}` - Get cart by ID
- `POST /ui/cart/{cartId}/lines` - Add item to cart
- `PATCH /ui/cart/{cartId}/lines` - Update item quantity (remove if qty=0)
- `POST /ui/cart/{cartId}/open-checkout` - Set cart to checking out state
- `POST /ui/checkout/{cartId}` - Complete checkout with guest contact

### Payment (`/ui/payment`)
- `POST /ui/payment/start` - Start dummy payment
- `GET /ui/payment/{paymentId}` - Get payment status (for polling)
- `POST /ui/payment/{paymentId}/cancel` - Cancel payment

### Order (`/ui/order`)
- `POST /ui/order/create` - Create order from paid cart
- `GET /ui/order/{orderId}` - Get order status and details
- `POST /ui/order/{orderId}/transition/{transitionName}` - Update order status

## Key Features

### Anonymous Checkout
- No user accounts required
- Guest contact information captured during checkout
- Required fields: name, address (line1, city, postcode, country)

### Product Search & Filtering
- Free-text search on name and description
- Category filtering
- Price range filtering
- Pagination support
- Slim DTOs for list views, full documents for detail views

### Stock Management
- Automatic stock decrement on order creation
- No reservations (immediate decrement)
- Quantity validation during cart operations

### Payment Processing
- Dummy payment provider
- Auto-approval after 3 seconds
- Payment status polling

### Order Management
- Short ULID order numbers for customer reference
- Single shipment per order
- Order status tracking through workflow states

## Technical Implementation

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

## Validation & Testing

### Build Status
- ✅ Project compiles successfully (`./gradlew clean compileJava`)
- ✅ All tests pass (`./gradlew build`)
- ✅ No compilation errors or warnings

### Compliance
- ✅ All entities implement CyodaEntity interface
- ✅ All processors implement CyodaProcessor interface
- ✅ All workflows use "initial" state (not "none")
- ✅ All transitions have explicit manual flags
- ✅ Controllers are thin proxies with no business logic
- ✅ No modifications to `common/` directory
- ✅ No Java reflection used

## How to Test

### 1. Start the Application
```bash
./gradlew bootRun
```

### 2. Access Swagger UI
Navigate to `http://localhost:8080/swagger-ui/index.html` for API documentation and testing.

### 3. Happy Path Testing
1. **Create Products**: POST `/ui/products` with product data
2. **Search Products**: GET `/ui/products?category=electronics&search=phone`
3. **Create Cart**: POST `/ui/cart`
4. **Add Items**: POST `/ui/cart/{cartId}/lines` with SKU and quantity
5. **Open Checkout**: POST `/ui/cart/{cartId}/open-checkout`
6. **Complete Checkout**: POST `/ui/checkout/{cartId}` with guest contact
7. **Start Payment**: POST `/ui/payment/start` with cartId
8. **Poll Payment**: GET `/ui/payment/{paymentId}` until status is "PAID"
9. **Create Order**: POST `/ui/order/create` with paymentId and cartId
10. **Check Order**: GET `/ui/order/{orderId}` for status

### 4. Verify Stock Decrement
Check product quantity before and after order creation to verify stock decrement.

## Security & Configuration
- Server-side Cyoda credentials (never exposed to browser)
- CORS enabled for all origins (development configuration)
- No browser authentication required
- All UI traffic goes through `/ui/**` endpoints

## Performance Optimizations
- Technical UUIDs used for optimal performance
- Slim DTOs for product list views
- Business ID lookups for user-facing operations
- Efficient search conditions using Cyoda query API

## Demo Rules Implemented
- ✅ Anonymous checkout only
- ✅ Payment auto-approves after ~3 seconds
- ✅ Stock decrement on order creation (no reservations)
- ✅ Single shipment per order
- ✅ Short ULID order numbers
- ✅ Catalog filters: category, free-text, price range
- ✅ Full Product schema persistence and round-trip

The implementation is complete and ready for demonstration!
