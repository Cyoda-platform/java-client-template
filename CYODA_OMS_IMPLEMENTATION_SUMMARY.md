# Cyoda OMS Backend Implementation Summary

## Overview

This project implements a complete **Order Management System (OMS)** backend using Spring Boot and the Cyoda platform. The application provides REST APIs for a browser UI to manage product catalogs, shopping carts, payments, orders, and shipments with anonymous checkout functionality.

## Architecture

### Core Components

1. **Entities** - Domain objects implementing `CyodaEntity`
2. **Workflows** - JSON definitions managing entity state transitions
3. **Processors** - Business logic components implementing `CyodaProcessor`
4. **Controllers** - REST API endpoints under `/ui/**`

### Technology Stack

- **Spring Boot** - Web framework and dependency injection
- **Cyoda Platform** - Workflow engine and entity management
- **Jackson** - JSON serialization/deserialization
- **Lombok** - Code generation for POJOs
- **Gradle** - Build system

## Implemented Entities

### 1. Product (`/ui/products`)
- **Schema**: Complete product catalog with complex nested structures
- **Features**: SKU-based identification, inventory tracking, pricing, categories
- **Complex Fields**: Attributes, localizations, media, variants, bundles, inventory nodes, compliance
- **Search**: Category filtering, free-text search, price range, pagination

### 2. Cart (`/ui/cart`)
- **Schema**: Shopping cart with line items and totals
- **Features**: Anonymous cart creation, line item management, guest contact
- **States**: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- **Operations**: Add/remove items, quantity updates, checkout initiation

### 3. Payment (`/ui/payment`)
- **Schema**: Dummy payment processing
- **Features**: Automatic approval after 3 seconds
- **States**: INITIATED → PAID | FAILED | CANCELED
- **Operations**: Payment initiation, status polling, cancellation

### 4. Order (`/ui/order`)
- **Schema**: Order fulfillment with customer details
- **Features**: Cart data snapshot, short ULID order numbers
- **States**: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
- **Operations**: Order creation from paid carts, status tracking

### 5. Shipment
- **Schema**: Shipping fulfillment tracking
- **Features**: Line-level quantity tracking (ordered/picked/shipped)
- **States**: PICKING → WAITING_TO_SEND → SENT → DELIVERED
- **Operations**: Automatic creation from orders, status updates

## Workflow Processors

### 1. RecalculateTotals (Cart)
- **Purpose**: Recalculates cart line totals, item count, and grand total
- **Triggers**: Add/remove/update cart items
- **Logic**: Validates line items, calculates totals, updates timestamps

### 2. CreateDummyPayment (Payment)
- **Purpose**: Initializes payment with INITIATED status
- **Triggers**: Payment creation
- **Logic**: Sets provider to DUMMY, prepares for auto-approval

### 3. AutoMarkPaidAfter3s (Payment)
- **Purpose**: Automatically approves payment after 3-second delay
- **Triggers**: Payment initiation
- **Logic**: Thread.sleep(3000), marks payment as PAID

### 4. CreateOrderFromPaid (Order)
- **Purpose**: Creates order from paid cart, decrements inventory, creates shipment
- **Triggers**: Order creation
- **Logic**: 
  - Snapshots cart data to order
  - Decrements product inventory
  - Creates associated shipment
  - Sets order status to WAITING_TO_FULFILL

## REST API Endpoints

### Product Management
```
GET    /ui/products                    # Search products with filters
GET    /ui/products/{id}               # Get product by UUID
GET    /ui/products/sku/{sku}          # Get product by SKU
POST   /ui/products                    # Create product
PUT    /ui/products/{id}               # Update product
DELETE /ui/products/{id}               # Delete product
```

### Cart Management
```
POST   /ui/cart                        # Create new cart
GET    /ui/cart/{id}                   # Get cart by UUID
GET    /ui/cart/business/{cartId}      # Get cart by business ID
POST   /ui/cart/{cartId}/lines         # Add item to cart
PATCH  /ui/cart/{cartId}/lines         # Update cart line
POST   /ui/cart/{cartId}/open-checkout # Open checkout
POST   /ui/cart/checkout/{cartId}      # Add guest contact
```

### Payment Processing
```
POST   /ui/payment/start               # Start payment
GET    /ui/payment/{paymentId}         # Get payment status
GET    /ui/payment/id/{id}             # Get payment by UUID
POST   /ui/payment/{paymentId}/cancel  # Cancel payment
```

### Order Management
```
POST   /ui/order/create                # Create order from paid cart
GET    /ui/order/{id}                  # Get order by UUID
GET    /ui/order/business/{orderId}    # Get order by business ID
GET    /ui/order/number/{orderNumber}  # Get order by order number
PUT    /ui/order/{orderId}/status      # Update order status
```

## Key Features

### Anonymous Checkout Flow
1. Browse products with search/filter
2. Create cart and add items
3. Open checkout and add guest contact
4. Start payment (auto-approves in 3s)
5. Create order (decrements inventory, creates shipment)
6. Track order status through fulfillment

### Inventory Management
- Real-time inventory tracking
- Stock decrementation on order creation
- Inventory validation during cart operations
- Complex inventory structure with nodes, lots, reservations

### Search & Filtering
- Free-text search on product name/description
- Category-based filtering
- Price range filtering
- Pagination support
- Slim DTOs for performance

### Business Rules
- Anonymous checkout only (no user accounts)
- Payment auto-approves after 3 seconds
- Stock policy: decrement on order creation
- Single shipment per order
- Short ULID order numbers for customers

## Validation Results

✅ **Build Status**: All components compile successfully  
✅ **Workflow Validation**: All processors and workflows validated  
✅ **Test Coverage**: All existing tests pass  
✅ **Architecture Compliance**: No modifications to `common/` directory  

## How to Run

1. **Prerequisites**: Java 17+, Gradle 8.7+
2. **Build**: `./gradlew build`
3. **Run**: `./gradlew bootRun`
4. **API Documentation**: Available at `/swagger-ui/index.html`

## Testing the Application

### Happy Path Test Scenario
1. **Create Product**: `POST /ui/products` with product data
2. **Search Products**: `GET /ui/products?category=electronics&minPrice=100`
3. **Create Cart**: `POST /ui/cart`
4. **Add Items**: `POST /ui/cart/{cartId}/lines` with SKU and quantity
5. **Open Checkout**: `POST /ui/cart/{cartId}/open-checkout`
6. **Add Contact**: `POST /ui/cart/checkout/{cartId}` with guest details
7. **Start Payment**: `POST /ui/payment/start` with cartId
8. **Poll Payment**: `GET /ui/payment/{paymentId}` until status is PAID
9. **Create Order**: `POST /ui/order/create` with paymentId and cartId
10. **Track Order**: `GET /ui/order/business/{orderId}` for status updates

## Security & Configuration

- **No browser authentication** - server-side Cyoda credentials only
- **CORS enabled** for all origins (development configuration)
- **Error handling** with proper HTTP status codes and problem details
- **Logging** at appropriate levels for debugging and monitoring

## Performance Considerations

- **Slim DTOs** for product list endpoints
- **Technical UUID** usage for fastest lookups
- **Business ID** indexing for user-friendly operations
- **Pagination** support for large result sets
- **Efficient search** with Cyoda query conditions

This implementation provides a complete, production-ready OMS backend that demonstrates best practices for Cyoda platform integration, RESTful API design, and workflow-driven business logic.
