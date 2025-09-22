# Order Management System (OMS) - Implementation Documentation

## Overview

This is a comprehensive **Order Management System (OMS)** built using the **Cyoda platform** with Spring Boot. The system provides a complete e-commerce backend with workflow-driven business logic, supporting anonymous checkout, product management, cart functionality, payment processing, and order fulfillment.

## System Architecture

### Core Principles
- **Workflow-driven architecture** - All business logic flows through Cyoda workflows
- **Anonymous checkout** - No user accounts required
- **Interface-based design** - Uses CyodaEntity/CyodaProcessor interfaces
- **Thin controllers** - Pure proxies to EntityService with no business logic
- **Server-side authentication** - Cyoda credentials managed server-side

### Technology Stack
- **Spring Boot 3.5.3** - Web framework
- **Cyoda Platform** - Workflow engine and data persistence
- **Java 21** - Programming language
- **Gradle** - Build system
- **Jackson** - JSON serialization

## Entities and Workflows

### 1. Product Entity
**Location**: `application/entity/product/version_1/Product.java`

**Features**:
- Complete e-commerce product schema with variants, inventory, compliance
- Complex nested structures (attributes, localizations, media, options, bundles)
- Inventory management with lot tracking and reservations
- Category-based organization

**Workflow States**:
- `initial` → `active` (on creation)
- `active` → `active` (on updates/inventory changes)
- `active` → `inactive` (on deactivation)
- `inactive` → `active` (on reactivation)

**Key Processors**:
- `UpdateProductInventoryProcessor` - Recalculates quantityAvailable from inventory nodes

### 2. Cart Entity
**Location**: `application/entity/cart/version_1/Cart.java`

**Features**:
- Shopping cart with line items and totals
- Guest contact information for anonymous checkout
- Automatic total calculation

**Workflow States**:
- `initial` → `active` (on first item add)
- `active` → `active` (on item add/update/remove)
- `active` → `checking_out` (on checkout initiation)
- `checking_out` → `converted` (on order creation)
- `checking_out` → `active` (on checkout cancellation)

**Key Processors**:
- `RecalculateCartTotalsProcessor` - Calculates line totals, item count, and grand total

### 3. Payment Entity
**Location**: `application/entity/payment/version_1/Payment.java`

**Features**:
- Dummy payment processing with auto-approval
- 3-second delay for payment approval simulation
- Support for payment retry and cancellation

**Workflow States**:
- `initial` → `initiated` (on payment start)
- `initiated` → `paid` (on auto-approval)
- `initiated` → `failed` (on failure)
- `initiated` → `canceled` (on cancellation)
- `failed` → `initiated` (on retry)

**Key Processors**:
- `AutoMarkPaidAfter3sProcessor` - Automatically approves payments after 3 seconds

### 4. Order Entity
**Location**: `application/entity/order/version_1/Order.java`

**Features**:
- Order management with ULID order numbers
- Snapshot of cart data at time of order creation
- Guest contact information preservation
- Order lifecycle tracking

**Workflow States**:
- `initial` → `waiting_to_fulfill` (on order creation)
- `waiting_to_fulfill` → `picking` (manual transition)
- `picking` → `waiting_to_send` (manual transition)
- `waiting_to_send` → `sent` (manual transition)
- `sent` → `delivered` (manual transition)

**Key Processors**:
- `CreateOrderFromPaidProcessor` - Creates order from paid cart, decrements inventory, creates shipment

### 5. Shipment Entity
**Location**: `application/entity/shipment/version_1/Shipment.java`

**Features**:
- Single shipment per order (for demo purposes)
- Line-level tracking with picked/shipped quantities
- Shipment lifecycle management

**Workflow States**:
- `initial` → `picking` (on creation)
- `picking` → `waiting_to_send` (manual transition)
- `waiting_to_send` → `sent` (manual transition)
- `sent` → `delivered` (manual transition)

## REST API Endpoints

### Product Management
- `GET /ui/products` - Search products with filters (category, search, price range)
- `GET /ui/products/{sku}` - Get product by SKU
- `POST /ui/products` - Create new product
- `PUT /ui/products/id/{id}?transition=TRANSITION_NAME` - Update product
- `GET /ui/products/categories` - Get all product categories

### Cart Management
- `POST /ui/cart` - Create new cart
- `GET /ui/cart/{cartId}` - Get cart by ID
- `POST /ui/cart/{cartId}/lines` - Add item to cart
- `PATCH /ui/cart/{cartId}/lines` - Update/remove cart item
- `POST /ui/cart/{cartId}/open-checkout` - Open checkout process

### Checkout Process
- `POST /ui/checkout/{cartId}` - Set guest contact information

### Payment Processing
- `POST /ui/payment/start` - Start dummy payment
- `GET /ui/payment/{paymentId}` - Get payment status
- `POST /ui/payment/{paymentId}/cancel` - Cancel payment

### Order Management
- `POST /ui/order/create` - Create order from paid payment
- `GET /ui/order/{orderId}` - Get order by ID
- `GET /ui/order/number/{orderNumber}` - Get order by order number
- `PUT /ui/order/{orderId}/status?transition=TRANSITION_NAME` - Update order status

## Complete E-commerce Flow

### 1. Product Setup
```bash
# Create a product
POST /ui/products
{
  "sku": "LAPTOP-001",
  "name": "Gaming Laptop Pro",
  "description": "High-performance gaming laptop",
  "price": 1299.99,
  "quantityAvailable": 10,
  "category": "Electronics"
}
```

### 2. Shopping Cart
```bash
# Create cart
POST /ui/cart

# Add items to cart
POST /ui/cart/{cartId}/lines
{
  "sku": "LAPTOP-001",
  "qty": 2
}

# Open checkout
POST /ui/cart/{cartId}/open-checkout
```

### 3. Checkout Process
```bash
# Set guest contact
POST /ui/checkout/{cartId}
{
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "address": {
      "line1": "123 Main Street",
      "city": "New York",
      "postcode": "10001",
      "country": "USA"
    }
  }
}
```

### 4. Payment Processing
```bash
# Start payment
POST /ui/payment/start
{
  "cartId": "{cartId}"
}

# Check payment status (auto-approved after 3 seconds)
GET /ui/payment/{paymentId}
```

### 5. Order Creation
```bash
# Create order from paid payment
POST /ui/order/create
{
  "paymentId": "{paymentId}",
  "cartId": "{cartId}"
}
```

## Key Business Logic

### Inventory Management
- Product inventory is decremented when orders are created
- Inventory calculation considers lots, reservations, and quality status
- Stock validation prevents overselling

### Payment Processing
- Dummy payment system with 3-second auto-approval
- Supports payment cancellation and retry
- Payment status polling for frontend integration

### Order Fulfillment
- Orders automatically create shipments in PICKING status
- Manual transitions through fulfillment states
- Complete audit trail of order lifecycle

## Configuration and Setup

### Environment Variables (.env file)
```bash
CYODA_HOST=your-cyoda-host
CYODA_CLIENT_ID=your-client-id
CYODA_CLIENT_SECRET=your-client-secret
CYODA_API_URL=https://your-cyoda-host/api
GRPC_ADDRESS=grpc-your-cyoda-host
GRPC_SERVER_PORT=443
```

### Running the Application
```bash
# Build the application
./gradlew build

# Run the application
./gradlew bootRun

# Application will start on http://localhost:8080
```

## Validation and Testing

### Manual Testing
1. Use the provided `test-oms-api.sh` script for complete flow testing
2. Replace placeholder IDs with actual values from API responses
3. Verify inventory decrements after order creation
4. Confirm payment auto-approval after 3 seconds

### Expected Behavior
- **Product creation** → Product available for search
- **Cart operations** → Totals automatically calculated
- **Payment processing** → Auto-approval after 3 seconds
- **Order creation** → Inventory decremented, shipment created
- **Workflow transitions** → Proper state management

### Success Criteria
- ✅ Application compiles and starts successfully
- ✅ All REST endpoints respond correctly
- ✅ Workflow transitions work as designed
- ✅ Business logic executes properly (inventory, totals, etc.)
- ✅ Complete e-commerce flow functional

## Architecture Benefits

1. **Scalability** - Cyoda workflow engine handles complex business logic
2. **Maintainability** - Clear separation between controllers, entities, and processors
3. **Flexibility** - Easy to add new workflow states and transitions
4. **Reliability** - Built-in retry mechanisms and error handling
5. **Performance** - Optimized with slim DTOs and technical UUID returns

This OMS system provides a solid foundation for e-commerce operations with the flexibility to extend and customize based on specific business requirements.

## Quick Developer Reference

### Adding New Entities
1. Create entity class in `application/entity/{name}/version_1/`
2. Implement `CyodaEntity` interface
3. Create workflow JSON in `src/main/resources/workflow/{name}/version_1/`
4. Create controller in `application/controller/`
5. Add processors if needed in `application/processor/`

### Workflow Best Practices
- Use "initial" as initial state (not "none")
- All transitions must have explicit `manual: true/false` flags
- Processor names must match Spring component class names exactly
- Keep workflows simple unless requirements demand complexity

### Common Patterns
- Controllers are thin proxies to EntityService
- Processors handle business logic and can update OTHER entities
- Use `findByBusinessId()` for business ID lookups
- Return technical UUIDs for performance
- Use `QueryCondition` for search operations

### Troubleshooting
- Check Cyoda connection configuration in `.env` file
- Verify workflow JSON syntax and processor names
- Ensure all required fields are validated in entity `isValid()` methods
- Use `./gradlew clean compileJava` to verify compilation after changes
