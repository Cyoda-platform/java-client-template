# OMS (Order Management System) Backend - Implementation Summary

## Overview

This project implements a complete Order Management System (OMS) backend using Spring Boot and the Cyoda workflow platform. The system provides REST APIs for a browser UI to manage the complete e-commerce order lifecycle from product catalog to delivery.

## System Architecture

The OMS system is built using a workflow-driven architecture with the following core components:

### Entities (5 total)
1. **Product** - Catalog management with comprehensive schema
2. **Cart** - Shopping cart with line items and totals
3. **Payment** - Dummy payment processing with auto-approval
4. **Order** - Order fulfillment and tracking
5. **Shipment** - Shipping and delivery management

### Workflows
Each entity has its own workflow definition that manages state transitions and business logic:

- **Product**: `initial → active → inactive`
- **Cart**: `initial → active → checking_out → converted`
- **Payment**: `initial → initiated → paid/failed/canceled`
- **Order**: `initial → waiting_to_fulfill → picking → waiting_to_send → sent → delivered`
- **Shipment**: `initial → picking → waiting_to_send → sent → delivered`

## Key Features Implemented

### 1. Product Catalog
- **Full schema support** with attributes, localizations, media, variants, bundles, inventory, compliance
- **Search and filtering** by category, free-text (name/description), price range
- **Inventory management** with automatic quantity tracking
- **Slim DTO** for list views, full document for detail views

### 2. Shopping Cart
- **Anonymous cart creation** with unique cart IDs
- **Line item management** (add, update, remove items)
- **Automatic totals calculation** via processors
- **Guest contact information** for checkout
- **State management** through workflow transitions

### 3. Dummy Payment Processing
- **Auto-approval after 3 seconds** using scheduled processors
- **Payment status polling** for UI integration
- **Failure simulation** for testing scenarios
- **Integration with cart checkout** flow

### 4. Order Fulfillment
- **Order creation from paid carts** with data snapshotting
- **Inventory decrements** on order creation
- **Order lifecycle management** (picking → shipping → delivery)
- **Short ULID order numbers** for customer reference

### 5. Shipping Management
- **Single shipment per order** as per requirements
- **Picking progress tracking** with quantity validation
- **Tracking number generation** and carrier assignment
- **Delivery confirmation** workflow

## REST API Endpoints

### Product APIs (`/ui/products`)
- `GET /ui/products` - Search products with filters
- `GET /ui/products/{sku}` - Get product details
- `POST /ui/products` - Create product
- `PUT /ui/products/{id}` - Update product

### Cart APIs (`/ui/cart`)
- `POST /ui/cart` - Create cart
- `GET /ui/cart/{cartId}` - Get cart
- `POST /ui/cart/{cartId}/lines` - Add item to cart
- `PATCH /ui/cart/{cartId}/lines` - Update item quantity
- `POST /ui/cart/{cartId}/open-checkout` - Open checkout

### Checkout APIs (`/ui/checkout`)
- `POST /ui/checkout/{cartId}` - Checkout with guest contact

### Payment APIs (`/ui/payment`)
- `POST /ui/payment/start` - Start payment
- `GET /ui/payment/{paymentId}` - Get payment status
- `POST /ui/payment/{paymentId}/cancel` - Cancel payment

### Order APIs (`/ui/order`)
- `POST /ui/order/create` - Create order from payment
- `GET /ui/order/{orderId}` - Get order details
- `POST /ui/order/{orderId}/start-picking` - Start picking
- `POST /ui/order/{orderId}/ready-to-send` - Mark ready to send
- `POST /ui/order/{orderId}/mark-sent` - Mark as sent
- `POST /ui/order/{orderId}/mark-delivered` - Mark as delivered

### Shipment APIs (`/ui/shipment`)
- `GET /ui/shipment/{shipmentId}` - Get shipment
- `GET /ui/shipment/order/{orderId}` - Get shipment by order
- `PATCH /ui/shipment/{shipmentId}/picking` - Update picking progress
- `POST /ui/shipment/{shipmentId}/complete-picking` - Complete picking
- `POST /ui/shipment/{shipmentId}/ship` - Ship package
- `POST /ui/shipment/{shipmentId}/confirm-delivery` - Confirm delivery

## Business Logic Processors

### Product Processors
- `UpdateProductProcessor` - Handles product updates and validation
- `UpdateInventoryProcessor` - Manages inventory calculations and decrements

### Cart Processors
- `CreateCartProcessor` - Initializes new carts
- `RecalculateTotalsProcessor` - Calculates cart totals and line totals
- `CheckoutProcessor` - Validates checkout and guest contact

### Payment Processors
- `StartDummyPaymentProcessor` - Initiates payment and schedules auto-approval
- `AutoMarkPaidProcessor` - Automatically marks payments as paid after 3 seconds
- `MarkPaymentFailedProcessor` - Handles payment failures

### Order Processors
- `CreateOrderFromPaidProcessor` - Creates orders from paid carts, decrements inventory, creates shipments
- `ReadyToSendProcessor` - Marks orders ready for shipping
- `MarkSentProcessor` - Updates order and shipment for shipping
- `MarkDeliveredProcessor` - Completes order lifecycle

### Shipment Processors
- `CreateShipmentProcessor` - Initializes shipments
- `UpdatePickingProgressProcessor` - Tracks picking progress
- `ShipPackageProcessor` - Generates tracking and ships packages
- `ConfirmDeliveryProcessor` - Confirms delivery completion

## Demo Workflow (Happy Path)

1. **Browse Products**: `GET /ui/products?category=electronics`
2. **Create Cart**: `POST /ui/cart`
3. **Add Items**: `POST /ui/cart/{cartId}/lines`
4. **Open Checkout**: `POST /ui/cart/{cartId}/open-checkout`
5. **Checkout**: `POST /ui/checkout/{cartId}` with guest contact
6. **Start Payment**: `POST /ui/payment/start`
7. **Wait for Auto-Payment**: Payment auto-approves after 3 seconds
8. **Create Order**: `POST /ui/order/create`
9. **Order Fulfillment**: Progress through picking → shipping → delivery
10. **Track Shipment**: Monitor via shipment APIs

## Technical Implementation Details

### Framework Integration
- **Spring Boot** application with Cyoda workflow integration
- **EntityService** for all data operations (no direct database access)
- **Workflow-driven** business logic with manual transitions
- **Technical UUIDs** for performance, business IDs for user reference

### Data Consistency
- **Inventory decrements** on order creation (no reservations)
- **Cart totals** automatically calculated via processors
- **Order data snapshotting** from carts for immutability
- **Shipment tracking** synchronized with order status

### Error Handling
- **Validation** at entity and processor levels
- **Business rule enforcement** via workflow constraints
- **Graceful error responses** with RFC 7807 Problem Details
- **Comprehensive logging** for debugging and monitoring

## Validation and Testing

### Build Status
- ✅ **Full compilation successful**: `./gradlew build`
- ✅ **All entities implement CyodaEntity** with proper validation
- ✅ **All workflows use manual transitions** as required
- ✅ **All processors and controllers** implemented per requirements

### Requirements Coverage
- ✅ **Anonymous checkout** - No user accounts required
- ✅ **Dummy payment auto-approval** - 3-second delay implemented
- ✅ **Stock policy** - Inventory decremented on order creation
- ✅ **Single shipment per order** - One shipment created per order
- ✅ **Short ULID order numbers** - Generated for customer reference
- ✅ **Catalog filters** - Category, free-text, price range supported
- ✅ **Full Product schema** - Complete schema implementation with round-trip support

## Next Steps for Production

1. **Security**: Add authentication and authorization
2. **Database**: Configure production database connection
3. **Monitoring**: Add metrics and health checks
4. **Testing**: Implement comprehensive integration tests
5. **Documentation**: Generate OpenAPI/Swagger documentation
6. **Deployment**: Configure production deployment pipeline

## How to Run

1. **Start the application**: `./gradlew bootRun`
2. **Access Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
3. **Test APIs**: Use the provided endpoints to test the complete workflow
4. **Monitor logs**: Check application logs for workflow execution details

The system is now ready for integration with a frontend UI and can handle the complete e-commerce order lifecycle as specified in the requirements.
