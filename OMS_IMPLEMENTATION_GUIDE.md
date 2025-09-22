# Cyoda Order Management System (OMS) Implementation

## Overview

This project implements a complete Order Management System (OMS) using the Cyoda platform with Spring Boot. The system provides REST APIs for a browser UI to manage products, shopping carts, payments, orders, and shipments with workflow-driven backend processing.

## Architecture

### Core Components

1. **Entities** - Domain objects implementing CyodaEntity interface
2. **Workflows** - JSON-defined state machines for business processes
3. **Processors** - Business logic components implementing CyodaProcessor interface
4. **Controllers** - REST API endpoints for UI integration
5. **Services** - EntityService for Cyoda platform integration

### Entity Model

#### Product
- **Purpose**: Complete product catalog with full schema
- **Key Fields**: sku, name, description, price, quantityAvailable, category
- **Complex Features**: attributes, localizations, media, options, variants, bundles, inventory, compliance, relationships, events
- **Workflow**: Simple active state for product management

#### Cart
- **Purpose**: Shopping cart for anonymous users
- **Key Fields**: cartId, status, lines, totalItems, grandTotal, guestContact
- **States**: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- **Features**: Line item management, total calculation, guest checkout

#### Payment
- **Purpose**: Dummy payment processing with 3-second auto-approval
- **Key Fields**: paymentId, cartId, amount, status, provider
- **States**: INITIATED → PAID | FAILED | CANCELED
- **Features**: Automatic payment approval simulation

#### Order
- **Purpose**: Order management with fulfillment workflow
- **Key Fields**: orderId, orderNumber (ULID), status, lines, totals, guestContact
- **States**: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
- **Features**: Cart data snapshot, stock decrement, shipment creation

#### Shipment
- **Purpose**: Single shipment per order tracking
- **Key Fields**: shipmentId, orderId, status, lines
- **States**: PICKING → WAITING_TO_SEND → SENT → DELIVERED
- **Features**: Pick/ship quantity tracking

## Workflow Implementation

### Cart Workflow
```json
NEW → ACTIVE (add_first_item + RecalculateTotals)
ACTIVE → ACTIVE (add_item/update_item/remove_item + RecalculateTotals)
ACTIVE → CHECKING_OUT (open_checkout)
CHECKING_OUT → CONVERTED (checkout)
```

### Payment Workflow
```json
INITIATED → PROCESSING (start_dummy_payment + CreateDummyPayment)
PROCESSING → PAID (auto_mark_paid + AutoMarkPaidAfter3s)
```

### Order Workflow
```json
WAITING_TO_FULFILL → PICKING (create_order_from_paid + CreateOrderFromPaid)
PICKING → WAITING_TO_SEND (ready_to_send)
WAITING_TO_SEND → SENT (mark_sent)
SENT → DELIVERED (mark_delivered)
```

## Processors

### RecalculateTotals
- **Entity**: Cart
- **Purpose**: Recalculates cart totals when items are added/updated/removed
- **Logic**: Calculates line totals (price × qty) and overall totals

### CreateDummyPayment
- **Entity**: Payment
- **Purpose**: Initializes dummy payment processing
- **Logic**: Sets status to INITIATED and provider to DUMMY

### AutoMarkPaidAfter3s
- **Entity**: Payment
- **Purpose**: Simulates payment processing with 3-second delay
- **Logic**: Thread.sleep(3000) then sets status to PAID

### CreateOrderFromPaid
- **Entity**: Order
- **Purpose**: Creates order from paid cart and manages inventory
- **Logic**: 
  - Snapshots cart data to order
  - Decrements product stock for each line item
  - Creates shipment in PICKING state

## REST API Endpoints

### Products (`/ui/products`)
- `GET /ui/products` - Search products with filters (search, category, price range, pagination)
- `GET /ui/products/{sku}` - Get product detail by SKU
- `POST /ui/products` - Create new product
- `PUT /ui/products/{sku}` - Update product

### Cart (`/ui/cart`)
- `POST /ui/cart` - Create new cart
- `GET /ui/cart/{cartId}` - Get cart by ID
- `POST /ui/cart/{cartId}/lines` - Add item to cart
- `PATCH /ui/cart/{cartId}/lines` - Update cart line quantity
- `POST /ui/cart/{cartId}/open-checkout` - Open checkout

### Checkout (`/ui/checkout`)
- `POST /ui/checkout/{cartId}` - Anonymous checkout with guest contact

### Payment (`/ui/payment`)
- `POST /ui/payment/start` - Start dummy payment
- `GET /ui/payment/{paymentId}` - Poll payment status

### Order (`/ui/order`)
- `POST /ui/order/create` - Create order from paid payment
- `GET /ui/order/{orderId}` - Get order status

## Key Features

### Anonymous Checkout
- No user accounts required
- Guest contact information captured during checkout
- Address validation for order fulfillment

### Dummy Payment Processing
- 3-second auto-approval simulation
- Status polling for UI integration
- Realistic payment flow without external dependencies

### Inventory Management
- Real-time stock decrement on order creation
- Product availability tracking
- No reservation system (immediate decrement)

### Single Shipment Model
- One shipment per order for simplicity
- Pick/ship quantity tracking
- Status progression through fulfillment workflow

### Product Search & Filtering
- Free-text search on product name
- Category-based filtering
- Price range filtering
- Pagination support
- Slim DTOs for list performance, full schema for details

## Validation & Testing

### Workflow Validation
```bash
# Validate all workflow implementations
./gradlew validateWorkflowImplementations

# Validate specific workflow
./gradlew validateWorkflowImplementations -Pargs="src/main/resources/workflow/cart/version_1/Cart.json"
```

### Build & Compilation
```bash
# Full build with tests
./gradlew build

# Compile only
./gradlew compileJava
```

### Workflow Import
```bash
# Import workflows to Cyoda (requires .env configuration)
./gradlew run --main-class=com.java_template.common.tool.WorkflowImportTool
```

## Configuration

### Environment Setup
1. Copy `.env.example` to `.env`
2. Fill in Cyoda credentials:
   ```
   CYODA_CLIENT_ID=your-client-id
   CYODA_CLIENT_SECRET=your-client-secret
   CYODA_BASE_URL=https://your-cyoda-instance.com
   CYODA_GRPC_URL=your-grpc-endpoint:443
   ```

### Application Properties
- Server runs on port 8080
- Swagger UI available at `/swagger-ui/index.html`
- CORS enabled for all origins

## Deployment Workflow

1. **Development Setup**
   ```bash
   git clone <repository>
   cd java-client-template
   cp .env.example .env
   # Edit .env with your Cyoda credentials
   ```

2. **Build & Validate**
   ```bash
   ./gradlew build
   ./gradlew validateWorkflowImplementations
   ```

3. **Import Workflows**
   ```bash
   ./gradlew run --main-class=com.java_template.common.tool.WorkflowImportTool
   ```

4. **Start Application**
   ```bash
   ./gradlew bootRun
   ```

5. **Test API**
   - Access Swagger UI: http://localhost:8080/swagger-ui/index.html
   - Test product endpoints: http://localhost:8080/ui/products

## End-to-End User Flow

1. **Browse Products**: GET `/ui/products?category=electronics&search=phone`
2. **View Product**: GET `/ui/products/PHONE-001`
3. **Create Cart**: POST `/ui/cart`
4. **Add Items**: POST `/ui/cart/{cartId}/lines` with `{sku: "PHONE-001", qty: 1}`
5. **Open Checkout**: POST `/ui/cart/{cartId}/open-checkout`
6. **Submit Contact**: POST `/ui/checkout/{cartId}` with guest contact info
7. **Start Payment**: POST `/ui/payment/start` with `{cartId: "..."}`
8. **Poll Payment**: GET `/ui/payment/{paymentId}` until status is PAID
9. **Create Order**: POST `/ui/order/create` with `{paymentId: "...", cartId: "..."}`
10. **Track Order**: GET `/ui/order/{orderId}` for status updates

## Technical Notes

### Processor Limitations
- Processors can read current entity but changes may not persist
- Use processors to update OTHER entities via EntityService
- Business status vs workflow state separation

### Performance Optimizations
- Slim DTOs for product lists
- Technical UUID lookups for fastest access
- Business ID lookups for user-facing operations
- Pagination for large datasets

### Error Handling
- Comprehensive validation in entities and controllers
- Proper HTTP status codes
- Detailed logging for debugging
- Graceful degradation for missing data

## Success Criteria

✅ **Complete Implementation**
- All 5 entities implemented with full validation
- All 5 workflows defined with proper state transitions
- All 4 processors implemented and validated
- All 5 controllers with comprehensive REST APIs

✅ **Workflow Compliance**
- All processors referenced in workflows exist as Java classes
- Proper manual/automatic transition flags
- Initial state set to "initial" (not "none")

✅ **Architecture Adherence**
- No modifications to `common/` directory
- Interface-based design with CyodaEntity/CyodaProcessor
- Thin controllers with no business logic
- EntityService integration for all data operations

✅ **Build & Validation**
- Project compiles successfully: `./gradlew build`
- Workflow validation passes: `./gradlew validateWorkflowImplementations`
- All functional requirements implemented

## API Reference Quick Guide

### Product Management
```bash
# Search products
curl "http://localhost:8080/ui/products?search=phone&category=electronics&minPrice=100&maxPrice=1000"

# Get product detail
curl "http://localhost:8080/ui/products/PHONE-001"

# Create product
curl -X POST "http://localhost:8080/ui/products" \
  -H "Content-Type: application/json" \
  -d '{"sku":"PHONE-001","name":"Smartphone","description":"Latest model","price":599.99,"quantityAvailable":100,"category":"electronics"}'
```

### Cart Operations
```bash
# Create cart
curl -X POST "http://localhost:8080/ui/cart"

# Add item to cart
curl -X POST "http://localhost:8080/ui/cart/{cartId}/lines" \
  -H "Content-Type: application/json" \
  -d '{"sku":"PHONE-001","qty":1}'

# Update cart line
curl -X PATCH "http://localhost:8080/ui/cart/{cartId}/lines" \
  -H "Content-Type: application/json" \
  -d '{"sku":"PHONE-001","qty":2}'

# Open checkout
curl -X POST "http://localhost:8080/ui/cart/{cartId}/open-checkout"
```

### Checkout & Payment
```bash
# Submit guest contact
curl -X POST "http://localhost:8080/ui/checkout/{cartId}" \
  -H "Content-Type: application/json" \
  -d '{"guestContact":{"name":"John Doe","email":"john@example.com","address":{"line1":"123 Main St","city":"Anytown","postcode":"12345","country":"US"}}}'

# Start payment
curl -X POST "http://localhost:8080/ui/payment/start" \
  -H "Content-Type: application/json" \
  -d '{"cartId":"{cartId}"}'

# Check payment status
curl "http://localhost:8080/ui/payment/{paymentId}"
```

### Order Management
```bash
# Create order
curl -X POST "http://localhost:8080/ui/order/create" \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"{paymentId}","cartId":"{cartId}"}'

# Get order status
curl "http://localhost:8080/ui/order/{orderId}"
```

This OMS implementation provides a complete, production-ready foundation for e-commerce operations with the Cyoda platform, featuring comprehensive product management, cart functionality, payment processing, order fulfillment, and shipment tracking.
