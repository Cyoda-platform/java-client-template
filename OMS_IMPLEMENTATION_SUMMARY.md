# OMS (Order Management System) Implementation Summary

## Overview
This project implements a complete Order Management System (OMS) using the Cyoda platform with Spring Boot. The system provides a backend API for an e-commerce application with anonymous checkout, dummy payment processing, and order fulfillment tracking.

## Architecture
- **Framework**: Spring Boot with Cyoda integration
- **Build Tool**: Gradle
- **Entity Management**: Cyoda workflow-driven entities
- **API Design**: RESTful endpoints under `/ui/**` for browser consumption
- **Authentication**: Anonymous access (no user accounts required)

## Implemented Entities

### 1. Product (`/ui/products`)
- **Full Schema**: Complete product catalog with complex nested structures
- **Features**: Attributes, variants, inventory, compliance, relationships
- **Search**: Free-text search on name/description, category filtering, price range
- **Performance**: Slim DTOs for list views, full documents for detail views

### 2. Cart (`/ui/cart`)
- **Workflow States**: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- **Features**: Line items management, automatic totals calculation
- **Guest Support**: Anonymous cart with guest contact information

### 3. Payment (`/ui/payment`)
- **Dummy Processing**: Auto-approval after 3 seconds for demo
- **Workflow States**: INITIATED → PAID | FAILED | CANCELED
- **Provider**: Always "DUMMY" for demonstration purposes

### 4. Order (`/ui/order`)
- **Lifecycle States**: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
- **Features**: Order creation from paid cart, stock decrement, short ULID order numbers
- **Integration**: Automatic shipment creation

### 5. Shipment (`/ui/shipment`)
- **Single Shipment**: One shipment per order for demo simplicity
- **Tracking States**: PICKING → WAITING_TO_SEND → SENT → DELIVERED
- **Line Items**: Quantity tracking (ordered, picked, shipped)

## Core Processors

### 1. RecalculateCartTotalsProcessor
- **Purpose**: Recalculates cart line totals, item count, and grand total
- **Triggers**: On cart item add/remove/update operations
- **Logic**: Automatic calculation of line totals and cart totals

### 2. AutoMarkPaidAfter3sProcessor
- **Purpose**: Simulates payment processing with 3-second delay
- **Triggers**: On payment initiation
- **Logic**: Thread.sleep(3000) then auto-approve payment

### 3. CreateOrderFromPaidProcessor
- **Purpose**: Creates order from paid cart and manages inventory
- **Triggers**: On order creation from paid payment
- **Logic**: 
  - Snapshots cart data to order
  - Decrements product inventory
  - Creates shipment for order

### 4. UpdateProductInventoryProcessor
- **Purpose**: Validates and manages product inventory levels
- **Triggers**: On product inventory updates
- **Logic**: Prevents negative inventory, logs inventory events

## REST API Endpoints

### Product Catalog
```
GET    /ui/products                    # Search products with filters
GET    /ui/products/{id}               # Get product by technical UUID
GET    /ui/products/sku/{sku}          # Get product by SKU
POST   /ui/products                    # Create new product
PUT    /ui/products/{id}               # Update product
DELETE /ui/products/{id}               # Delete product
```

### Shopping Cart
```
POST   /ui/cart                        # Create new cart
GET    /ui/cart/{id}                   # Get cart by technical UUID
GET    /ui/cart/business/{cartId}      # Get cart by business ID
POST   /ui/cart/{cartId}/lines         # Add item to cart
PATCH  /ui/cart/{cartId}/lines         # Update item quantity
POST   /ui/cart/{cartId}/open-checkout # Open checkout process
PUT    /ui/cart/{cartId}/contact       # Update guest contact
```

### Anonymous Checkout
```
POST   /ui/checkout/{cartId}           # Process checkout with guest info
GET    /ui/checkout/{cartId}           # Get checkout information
GET    /ui/checkout/{cartId}/validate  # Validate checkout readiness
```

### Payment Processing
```
POST   /ui/payment/start               # Start dummy payment
GET    /ui/payment/{paymentId}         # Get payment status
GET    /ui/payment/id/{id}             # Get payment by technical UUID
POST   /ui/payment/{paymentId}/cancel  # Cancel payment
```

### Order Management
```
POST   /ui/order/create                # Create order from paid cart
GET    /ui/order/{id}                  # Get order by technical UUID
GET    /ui/order/business/{orderId}    # Get order by business ID
PUT    /ui/order/{orderId}/status      # Update order status
GET    /ui/order                       # Get all orders (admin)
```

## Workflow Definitions

All entities have corresponding JSON workflow definitions in `src/main/resources/workflow/`:
- Product workflow: Basic CRUD with inventory management
- Cart workflow: Shopping cart lifecycle with totals calculation
- Payment workflow: Dummy payment processing with auto-approval
- Order workflow: Order fulfillment lifecycle
- Shipment workflow: Shipment tracking states

## Key Features Implemented

### 1. Anonymous Checkout
- No user accounts required
- Guest contact information collection
- Address validation for shipping

### 2. Inventory Management
- Real-time stock decrement on order creation
- Inventory validation and tracking
- Product availability checking

### 3. Dummy Payment Processing
- 3-second auto-approval simulation
- Payment status polling
- Payment cancellation support

### 4. Order Fulfillment
- Automatic order creation from paid cart
- Single shipment per order
- Order status progression tracking

### 5. Product Catalog
- Complex product schema support
- Advanced search and filtering
- Performance-optimized list views

## Validation and Testing

### Build Status
- ✅ Full compilation successful
- ✅ All entities implement CyodaEntity correctly
- ✅ All processors implement CyodaProcessor correctly
- ✅ All controllers follow REST best practices
- ✅ Workflow definitions are valid JSON

### Functional Requirements Coverage
- ✅ Product catalog with full schema
- ✅ Anonymous shopping cart
- ✅ Guest checkout process
- ✅ Dummy payment processing
- ✅ Order creation and tracking
- ✅ Inventory management
- ✅ Single shipment per order

## How to Validate the Implementation

### 1. Start the Application
```bash
./gradlew bootRun
```

### 2. Test the Happy Path
1. **Create Products**: POST to `/ui/products` with product data
2. **Search Products**: GET `/ui/products?category=electronics&minPrice=100`
3. **Create Cart**: POST to `/ui/cart`
4. **Add Items**: POST to `/ui/cart/{cartId}/lines` with SKU and quantity
5. **Open Checkout**: POST to `/ui/cart/{cartId}/open-checkout`
6. **Add Guest Info**: POST to `/ui/checkout/{cartId}` with contact details
7. **Start Payment**: POST to `/ui/payment/start` with cartId
8. **Poll Payment**: GET `/ui/payment/{paymentId}` (wait for PAID status)
9. **Create Order**: POST to `/ui/order/create` with paymentId and cartId
10. **Track Order**: GET `/ui/order/business/{orderId}` to see order status

### 3. Access Swagger UI
Visit `http://localhost:8080/swagger-ui/index.html` for interactive API documentation.

## Next Steps
The implementation is complete and ready for:
- Frontend integration
- Additional business logic customization
- Production deployment configuration
- Enhanced error handling and validation
- Performance optimization for large catalogs
