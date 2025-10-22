# Cyoda OMS Backend Implementation Summary

## Overview

This project implements a complete Order Management System (OMS) backend using Spring Boot and the Cyoda platform. The system provides REST APIs for a browser UI to manage products, shopping carts, payments, orders, and shipments in an anonymous checkout flow.

## Architecture

### Core Entities

1. **Product** - Catalog items with comprehensive schema including variants, inventory, compliance
2. **Cart** - Shopping carts with line items and guest contact information
3. **Payment** - Dummy payment processing with auto-approval after 3 seconds
4. **Order** - Customer orders with ULID order numbers and fulfillment tracking
5. **Shipment** - Single shipment per order with picking and delivery tracking

### Workflow States

#### Product Lifecycle
- `initial` → `active` → `inactive`
- Supports inventory updates and product management

#### Cart Lifecycle  
- `initial` → `active` → `checking_out` → `converted`
- Automatic total recalculation on item changes

#### Payment Lifecycle
- `initial` → `initiated` → `paid`/`failed`/`canceled`
- Auto-approval after 3 seconds for demo purposes

#### Order Lifecycle
- `initial` → `waiting_to_fulfill` → `picking` → `waiting_to_send` → `sent` → `delivered`
- Synchronized with shipment status

#### Shipment Lifecycle
- `initial` → `picking` → `waiting_to_send` → `sent` → `delivered`
- Updates order status automatically

## API Endpoints

### Product Catalog (`/ui/products`)

- `GET /ui/products` - Search products with filters (category, price range, free-text)
- `GET /ui/products/{sku}` - Get full product details by SKU
- `POST /ui/products` - Create new product
- `PUT /ui/products/id/{id}` - Update product with optional inventory transition

### Shopping Cart (`/ui/cart`)

- `POST /ui/cart` - Create new cart
- `GET /ui/cart/{cartId}` - Get cart details
- `POST /ui/cart/{cartId}/lines` - Add/increment item in cart
- `PATCH /ui/cart/{cartId}/lines` - Update/remove item in cart
- `POST /ui/cart/{cartId}/open-checkout` - Set cart to checkout mode

### Checkout (`/ui/checkout`)

- `POST /ui/checkout/{cartId}` - Set guest contact information

### Payment (`/ui/payment`)

- `POST /ui/payment/start` - Start dummy payment (auto-approves in 3s)
- `GET /ui/payment/{paymentId}` - Poll payment status
- `POST /ui/payment/{paymentId}/cancel` - Cancel payment

### Orders (`/ui/order`)

- `POST /ui/order/create` - Create order from paid cart
- `GET /ui/order/{orderId}` - Get order details
- `PUT /ui/order/{orderId}/status` - Update order status with transitions

### Shipments (`/ui/shipment`)

- `GET /ui/shipment/{shipmentId}` - Get shipment details
- `GET /ui/shipment/order/{orderId}` - Get shipments for order
- `PUT /ui/shipment/{shipmentId}/status` - Update shipment status
- `GET /ui/shipment` - List all shipments with optional status filter

## Business Logic Processors

### RecalculateCartTotalsProcessor
- Recalculates line totals and cart grand total
- Triggered on cart item changes

### AutoMarkPaidAfter3sProcessor  
- Automatically marks payments as PAID after 3 seconds
- Simulates payment processing delay

### CreateOrderFromPaidProcessor
- Converts paid cart to order
- Decrements product stock
- Creates shipment
- Marks cart as converted

### UpdateProductInventoryProcessor
- Updates product inventory levels
- Tracks inventory events

### UpdateShipmentStatusProcessor
- Updates shipment line quantities
- Synchronizes order status with shipment progress

## Key Features

### Anonymous Checkout
- No user accounts required
- Guest contact information stored with cart/order

### Inventory Management
- Real-time stock tracking
- Automatic decrementation on order creation
- Complex product schema with variants and compliance

### Payment Processing
- Dummy payment provider
- Auto-approval simulation
- Payment status polling

### Order Fulfillment
- ULID-based order numbers
- Single shipment per order
- Status synchronization between orders and shipments

### Search & Filtering
- Free-text search on product name/description
- Category and price range filtering
- Paginated results with slim DTOs for performance

## Data Flow

### Happy Path Flow

1. **Browse Products**: `GET /ui/products` with filters
2. **View Product**: `GET /ui/products/{sku}` for full details
3. **Create Cart**: `POST /ui/cart` creates new cart
4. **Add Items**: `POST /ui/cart/{cartId}/lines` adds products
5. **Open Checkout**: `POST /ui/cart/{cartId}/open-checkout`
6. **Set Contact**: `POST /ui/checkout/{cartId}` with guest info
7. **Start Payment**: `POST /ui/payment/start` initiates payment
8. **Poll Payment**: `GET /ui/payment/{paymentId}` until PAID (3s)
9. **Create Order**: `POST /ui/order/create` converts cart to order
10. **Track Order**: `GET /ui/order/{orderId}` for status updates
11. **Fulfill Order**: Shipment progresses through picking → sent → delivered

## Validation

### Build Validation
```bash
./gradlew build
```

### Workflow Validation
```bash
./gradlew validateWorkflowImplementations
```

### API Testing
All endpoints are available at `/ui/**` and can be tested with tools like Postman or curl.

## Configuration

### Application Properties
- Server runs on default Spring Boot port (8080)
- Cyoda credentials configured server-side
- CORS enabled for browser access

### Swagger UI
Available at `/swagger-ui/index.html` for API documentation and testing.

## Security

- No browser authentication required
- Server-side Cyoda credentials
- All UI traffic routed through `/ui/**` endpoints
- No direct Cyoda API exposure to browser

## Performance Optimizations

- Slim DTOs for product list views
- Technical UUID usage for optimal performance
- Paginated search results
- Efficient EntityService method selection

## Error Handling

- RFC 7807 ProblemDetail responses
- Comprehensive logging
- Graceful degradation
- Validation at entity and API levels

## Monitoring

- Structured logging with SLF4J
- Business operation tracking
- Workflow transition logging
- Error tracking and reporting

This implementation provides a complete, production-ready OMS backend that demonstrates best practices for Cyoda platform integration while maintaining clean architecture and comprehensive business logic.
