# Cyoda OMS (Order Management System) Application

## Overview

This is a complete **Order Management System (OMS)** built using the Cyoda Java Client Template. The application provides a Spring Boot backend that exposes REST APIs for a browser UI, handling the complete e-commerce flow from product catalog to order fulfillment.

## Architecture

### Core Components

1. **Entities** - Domain objects implementing `CyodaEntity`
2. **Workflows** - State machines defining business process flows
3. **Processors** - Business logic components implementing `CyodaProcessor`
4. **Controllers** - REST API endpoints under `/ui/**`

### Entity Model

#### Product Entity
- **Location**: `src/main/java/com/java_template/application/entity/product/version_1/Product.java`
- **Purpose**: Complete product catalog with full schema including attributes, localizations, media, variants, inventory, compliance, and relationships
- **Key Fields**: `sku` (unique), `name`, `description`, `price`, `quantityAvailable`, `category`

#### Cart Entity
- **Location**: `src/main/java/com/java_template/application/entity/cart/version_1/Cart.java`
- **Purpose**: Shopping cart with line items and totals
- **States**: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- **Key Fields**: `cartId`, `status`, `lines`, `totalItems`, `grandTotal`, `guestContact`

#### Payment Entity
- **Location**: `src/main/java/com/java_template/application/entity/payment/version_1/Payment.java`
- **Purpose**: Dummy payment processing with auto-approval
- **States**: INITIATED → PAID | FAILED | CANCELED
- **Key Fields**: `paymentId`, `cartId`, `amount`, `status`, `provider`

#### Order Entity
- **Location**: `src/main/java/com/java_template/application/entity/order/version_1/Order.java`
- **Purpose**: Confirmed customer order with fulfillment tracking
- **States**: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
- **Key Fields**: `orderId`, `orderNumber` (ULID), `status`, `lines`, `totals`, `guestContact`

#### Shipment Entity
- **Location**: `src/main/java/com/java_template/application/entity/shipment/version_1/Shipment.java`
- **Purpose**: Single shipment per order with picking/shipping quantities
- **States**: PICKING → WAITING_TO_SEND → SENT → DELIVERED
- **Key Fields**: `shipmentId`, `orderId`, `status`, `lines`

### Workflow Definitions

All workflows are defined in `src/main/resources/workflow/` with proper state transitions:

- **Cart Workflow**: Handles cart lifecycle and line operations
- **Payment Workflow**: Manages dummy payment processing with 3-second auto-approval
- **Order Workflow**: Controls order fulfillment process
- **Shipment Workflow**: Tracks shipment status
- **Product Workflow**: Simple product management states

### Business Logic Processors

#### RecalculateTotals
- **Purpose**: Recalculates cart totals when lines are modified
- **Triggers**: Cart line add/update/remove operations
- **Logic**: Calculates line totals, total items, and grand total

#### CreateDummyPayment & AutoMarkPaidAfter3s
- **Purpose**: Simulates payment processing with 3-second delay
- **Flow**: INITIATED → (3s delay) → PAID
- **Demo Feature**: Auto-approval for testing

#### CreateOrderFromPaid
- **Purpose**: Creates order from paid cart and manages inventory
- **Actions**:
  - Snapshots cart data to order
  - Decrements product stock (`quantityAvailable`)
  - Creates shipment in PICKING status
  - Generates short ULID order number

### REST API Endpoints

All endpoints are under `/ui/**` for browser access:

#### Product Endpoints
- `GET /ui/products` - Search products with filters (category, text, price range)
- `GET /ui/products/{sku}` - Get product details
- `POST /ui/products` - Create new product

#### Cart Endpoints
- `POST /ui/cart` - Create new cart
- `GET /ui/cart/{cartId}` - Get cart details
- `POST /ui/cart/{cartId}/lines` - Add item to cart
- `PATCH /ui/cart/{cartId}/lines` - Update item quantity
- `POST /ui/cart/{cartId}/open-checkout` - Open checkout

#### Checkout Endpoints
- `POST /ui/checkout/{cartId}` - Process anonymous checkout with guest contact

#### Payment Endpoints
- `POST /ui/payment/start` - Start dummy payment
- `GET /ui/payment/{paymentId}` - Get payment status
- `POST /ui/payment/{paymentId}/cancel` - Cancel payment

#### Order Endpoints
- `POST /ui/order/create` - Create order from paid cart
- `GET /ui/order/{orderId}` - Get order details
- `GET /ui/order/number/{orderNumber}` - Get order by number

## Key Features

### Anonymous Checkout
- No user accounts required
- Guest contact information collection
- Required fields: name, address (line1, city, postcode, country)

### Stock Management
- Real-time stock checking during cart operations
- Automatic stock decrement on order creation
- Prevents overselling

### Dummy Payment Processing
- 3-second auto-approval simulation
- Payment status polling
- Cancellation support

### Order Fulfillment
- Single shipment per order
- Picking/shipping quantity tracking
- Order status progression

## Setup and Deployment

### Prerequisites
- Java 21
- Gradle 8.7+
- Cyoda platform access (for production)

### Configuration
1. Copy `.env.template` to `.env`
2. Configure Cyoda credentials:
   ```
   CYODA_CLIENT_ID=your-client-id
   CYODA_CLIENT_SECRET=your-client-secret
   CYODA_HOST=your-cyoda-host
   ```

### Build and Run
```bash
# Build the application
./gradlew build

# Import workflows (required before starting)
./gradlew runApp -PmainClass=com.java_template.common.tool.WorkflowImportTool

# Start the application
./gradlew bootRun
```

### API Documentation
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Application runs on port 8080

## Testing the Application

### Complete E2E Flow

1. **Create Products**
   ```bash
   POST /ui/products
   {
     "sku": "LAPTOP-001",
     "name": "Gaming Laptop",
     "description": "High-performance gaming laptop",
     "price": 1299.99,
     "quantityAvailable": 10,
     "category": "Electronics"
   }
   ```

2. **Search Products**
   ```bash
   GET /ui/products?category=Electronics&minPrice=1000&maxPrice=1500
   ```

3. **Create Cart and Add Items**
   ```bash
   POST /ui/cart
   POST /ui/cart/{cartId}/lines
   {
     "sku": "LAPTOP-001",
     "qty": 1
   }
   ```

4. **Open Checkout**
   ```bash
   POST /ui/cart/{cartId}/open-checkout
   ```

5. **Process Checkout**
   ```bash
   POST /ui/checkout/{cartId}
   {
     "guestContact": {
       "name": "John Doe",
       "email": "john@example.com",
       "address": {
         "line1": "123 Main St",
         "city": "New York",
         "postcode": "10001",
         "country": "USA"
       }
     }
   }
   ```

6. **Start Payment**
   ```bash
   POST /ui/payment/start
   {
     "cartId": "{cartId}"
   }
   ```

7. **Poll Payment Status** (wait 3+ seconds)
   ```bash
   GET /ui/payment/{paymentId}
   ```

8. **Create Order**
   ```bash
   POST /ui/order/create
   {
     "paymentId": "{paymentId}",
     "cartId": "{cartId}"
   }
   ```

9. **Verify Order**
   ```bash
   GET /ui/order/{orderId}
   ```

### Validation Points

- ✅ Product stock decrements after order creation
- ✅ Cart totals recalculate automatically
- ✅ Payment auto-approves after 3 seconds
- ✅ Order gets unique ULID number
- ✅ Shipment created in PICKING status
- ✅ All workflow states transition correctly

## Production Considerations

### Security
- Server-side Cyoda credentials (never exposed to browser)
- CORS enabled for frontend integration
- No authentication required for demo endpoints

### Performance
- Slim DTOs for product list endpoints
- Full entities for detail endpoints
- Efficient search with Cyoda query conditions

### Monitoring
- Comprehensive logging throughout the flow
- Processor execution tracking
- Error handling and recovery

## Troubleshooting

### Common Issues

1. **Workflow Import Failures**
   - Ensure `.env` file has correct Cyoda credentials
   - Import workflows before starting application
   - Check entity names match exactly

2. **Stock Management**
   - Verify product exists before adding to cart
   - Check sufficient stock availability
   - Monitor stock decrement in order processor

3. **Payment Processing**
   - Wait at least 3 seconds for auto-approval
   - Check payment status polling
   - Verify cart is in CHECKING_OUT status

### Logs to Monitor
- Processor execution logs
- Cart total recalculation
- Stock decrement operations
- Payment status changes
- Order creation flow

## Extension Points

The application is designed for easy extension:

- Add new entities with workflows
- Implement additional processors
- Create custom criteria for workflow conditions
- Add new REST endpoints
- Integrate with external payment providers
- Implement real inventory management

This OMS application demonstrates the full power of the Cyoda platform for building workflow-driven business applications with complex state management and business logic processing.
