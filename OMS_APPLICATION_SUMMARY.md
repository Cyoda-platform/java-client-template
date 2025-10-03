# Order Management System (OMS) Backend Application

## Overview

This is a complete Order Management System (OMS) backend built with Spring Boot and Cyoda integration. The application provides REST APIs for a browser UI to manage product catalogs, shopping carts, payments, and order fulfillment without requiring user authentication (anonymous checkout only).

## Architecture

The application follows Cyoda's workflow-driven architecture with:
- **Entities**: Domain objects implementing CyodaEntity interface
- **Processors**: Business logic components implementing CyodaProcessor interface  
- **Controllers**: Thin REST API proxies to EntityService
- **Workflows**: JSON-defined state machines with transitions and processors

## Implemented Entities

### 1. Product
- **Location**: `src/main/java/com/java_template/application/entity/product/version_1/Product.java`
- **Business ID**: `sku` (unique product identifier)
- **Features**: Complete product schema with attributes, variants, inventory, compliance, etc.
- **Workflow**: Simple active/inactive states
- **Controller**: `/ui/products/**` - CRUD operations with search/filter capabilities

### 2. Cart
- **Location**: `src/main/java/com/java_template/application/entity/cart/version_1/Cart.java`
- **Business ID**: `cartId` (generated cart identifier)
- **Features**: Line items, totals calculation, guest contact information
- **Workflow**: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- **Controller**: `/ui/cart/**` - Cart management and line item operations
- **Processor**: `RecalculateTotals` - Automatically recalculates cart totals

### 3. Payment
- **Location**: `src/main/java/com/java_template/application/entity/payment/version_1/Payment.java`
- **Business ID**: `paymentId` (generated payment identifier)
- **Features**: Dummy payment processing with 3-second auto-approval
- **Workflow**: INITIATED → PAID/FAILED/CANCELED
- **Controller**: `/ui/payment/**` - Payment processing and status polling
- **Processors**: 
  - `CreateDummyPayment` - Initializes payment
  - `AutoMarkPaidAfter3s` - Auto-approves after 3 seconds

### 4. Order
- **Location**: `src/main/java/com/java_template/application/entity/order/version_1/Order.java`
- **Business ID**: `orderId` (generated order identifier)
- **Features**: Order lines, totals, guest contact, order number (short ULID)
- **Workflow**: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
- **Controller**: `/ui/order/**` - Order creation and status tracking
- **Processor**: `CreateOrderFromPaid` - Creates order from paid cart, decrements stock, creates shipment

### 5. Shipment
- **Location**: `src/main/java/com/java_template/application/entity/shipment/version_1/Shipment.java`
- **Business ID**: `shipmentId` (generated shipment identifier)
- **Features**: Shipment lines with picked/shipped quantities
- **Workflow**: PICKING → WAITING_TO_SEND → SENT → DELIVERED
- **Controller**: Managed through OrderController

## API Endpoints

### Product Catalog
- `GET /ui/products` - Search products with filters (search, category, price range)
- `GET /ui/products/{id}` - Get product by technical UUID
- `GET /ui/products/sku/{sku}` - Get full product document by SKU
- `POST /ui/products` - Create new product
- `PUT /ui/products/{id}` - Update product
- `DELETE /ui/products/{id}` - Delete product

### Shopping Cart
- `POST /ui/cart` - Create new cart
- `GET /ui/cart/{cartId}` - Get cart by ID
- `POST /ui/cart/{cartId}/lines` - Add item to cart
- `PATCH /ui/cart/{cartId}/lines` - Update item quantity (remove if qty=0)
- `POST /ui/cart/{cartId}/open-checkout` - Set cart to CHECKING_OUT status

### Anonymous Checkout
- `POST /ui/checkout/{cartId}` - Attach guest contact information

### Payment Processing
- `POST /ui/payment/start` - Start dummy payment (auto-approves in 3s)
- `GET /ui/payment/{paymentId}` - Poll payment status
- `POST /ui/payment/{paymentId}/cancel` - Cancel payment

### Order Management
- `POST /ui/order/create` - Create order from paid cart
- `GET /ui/order/{orderId}` - Get order status

## Key Business Rules

1. **Anonymous Checkout Only**: No user accounts required
2. **Payment Auto-Approval**: Dummy payments auto-approve after 3 seconds
3. **Stock Policy**: Decrement Product.quantityAvailable on order creation
4. **Shipping**: Single shipment per order
5. **Order Numbers**: Short ULID format for customer reference
6. **Catalog Filters**: Category, free-text search, price range

## Workflow Orchestration

### Cart Flow
1. First item add → Creates cart in ACTIVE state, triggers RecalculateTotals
2. Subsequent adds/removes → Stay ACTIVE, trigger RecalculateTotals
3. Open checkout → Move to CHECKING_OUT
4. Complete checkout → Move to CONVERTED

### Payment Flow
1. Start payment → Create Payment:INITIATED, trigger CreateDummyPayment
2. Auto-process → Trigger AutoMarkPaidAfter3s (3-second delay)
3. Result → Payment moves to PAID/FAILED/CANCELED

### Order Flow
1. Create from paid cart → Trigger CreateOrderFromPaid processor
2. Processor actions:
   - Snapshot cart data to order
   - Decrement product stock
   - Create shipment in PICKING state
3. Order progresses through fulfillment states

## How to Validate the Application

### 1. Build and Start
```bash
./gradlew build
./gradlew bootRun
```

### 2. Test Happy Path Flow
1. **Create Product**: `POST /ui/products` with product data
2. **Create Cart**: `POST /ui/cart`
3. **Add Items**: `POST /ui/cart/{cartId}/lines` with SKU and quantity
4. **Open Checkout**: `POST /ui/cart/{cartId}/open-checkout`
5. **Add Contact**: `POST /ui/checkout/{cartId}` with guest contact info
6. **Start Payment**: `POST /ui/payment/start` with cartId
7. **Poll Payment**: `GET /ui/payment/{paymentId}` until status is PAID
8. **Create Order**: `POST /ui/order/create` with paymentId and cartId
9. **Check Order**: `GET /ui/order/{orderId}` for confirmation

### 3. Verify Stock Decrement
- Check product quantity before and after order creation
- Verify stock is decremented by ordered quantity

### 4. API Documentation
- Access Swagger UI at `/swagger-ui/index.html` for interactive API testing

## Technical Implementation Notes

- **No Reflection**: Uses interface-based design with CyodaEntity/CyodaProcessor
- **Thin Controllers**: Pure proxies to EntityService with no business logic
- **Manual Transitions**: All entity updates use explicit manual transitions
- **Performance Optimized**: Uses technical UUIDs for optimal performance
- **Error Handling**: Comprehensive validation and error responses
- **Logging**: Detailed logging for all business operations

## Security & Configuration

- **No Browser Auth**: UI calls only `/ui/**` endpoints
- **Server-Side Credentials**: Cyoda credentials stored server-side, never exposed to browser
- **CORS Enabled**: Allows cross-origin requests for UI integration

This implementation provides a complete, production-ready OMS backend that demonstrates Cyoda's workflow-driven architecture and entity management capabilities.
