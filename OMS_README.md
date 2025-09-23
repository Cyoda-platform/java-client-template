# Cyoda OMS Backend

A complete **Order Management System (OMS)** backend built with Spring Boot and Cyoda's workflow-driven architecture. This application provides REST APIs for managing products, shopping carts, payments, orders, and shipments.

## 🚀 Quick Start

### Prerequisites
- Java 17 or higher
- Gradle 8.7+
- Cyoda platform access

### Build and Run
```bash
# Build the application
./gradlew build

# Run the application
./gradlew bootRun

# The application will start on http://localhost:8080
```

### Test the APIs
```bash
# Run the complete OMS flow test
./test_oms_api.sh

# Or access Swagger UI
open http://localhost:8080/swagger-ui/index.html
```

## 📋 Features

### ✅ Complete OMS Implementation
- **Product Catalog** - Full schema with variants, inventory, compliance
- **Shopping Cart** - Anonymous checkout with automatic total calculation
- **Payment Processing** - Dummy payment with 3-second auto-approval
- **Order Management** - Complete order lifecycle with fulfillment tracking
- **Shipment Tracking** - Single shipment per order with status updates

### ✅ REST API Endpoints
- `GET /ui/products` - Search products with filters
- `POST /ui/cart` - Create shopping cart
- `POST /ui/payment/start` - Process payments
- `POST /ui/order/create` - Create orders
- And many more...

### ✅ Workflow-Driven Architecture
- **5 Entities** with complete Cyoda integration
- **5 Processors** handling business logic
- **Validated Workflows** with proper state transitions
- **No Java Reflection** - Pure interface-based design

## 🏗️ Architecture

### Entities
```
Product     - Complete product catalog with full schema
Cart        - Shopping cart with line items and totals
Payment     - Dummy payment processing
Order       - Customer orders with fulfillment tracking
Shipment    - Shipment tracking per order
```

### Workflows
```
Product:  initial → active
Cart:     initial → active → checking_out → converted
Payment:  initial → initiated → paid|failed|canceled
Order:    initial → waiting_to_fulfill → picking → waiting_to_send → sent → delivered
Shipment: initial → picking → waiting_to_send → sent → delivered
```

### Processors
```
RecalculateTotals      - Cart total calculations
CreateDummyPayment     - Payment initialization
AutoMarkPaidAfter3s    - Automatic payment approval
CreateOrderFromPaid    - Order creation with stock decrement
UpdateProductInventory - Product inventory management
```

## 🔗 API Endpoints

### Product Management
```http
GET    /ui/products                    # Search products
GET    /ui/products/{sku}              # Get product detail
POST   /ui/products                    # Create product
PUT    /ui/products/id/{id}            # Update product
```

### Cart Management
```http
POST   /ui/cart                        # Create cart
GET    /ui/cart/{cartId}               # Get cart
POST   /ui/cart/{cartId}/lines         # Add item
PATCH  /ui/cart/{cartId}/lines         # Update item
POST   /ui/cart/{cartId}/open-checkout # Open checkout
POST   /ui/cart/checkout/{cartId}      # Add guest contact
```

### Payment Processing
```http
POST   /ui/payment/start               # Start payment
GET    /ui/payment/{paymentId}         # Get payment status
PUT    /ui/payment/{paymentId}/status  # Update payment status
```

### Order Management
```http
POST   /ui/order/create                # Create order
GET    /ui/order/{orderId}             # Get order
PUT    /ui/order/{orderId}/status      # Update order status
GET    /ui/order                       # List all orders
```

## 🧪 Demo Flow

The complete OMS flow demonstrates:

1. **Product Creation** - Add products to catalog
2. **Product Search** - Find products by category/text/price
3. **Cart Management** - Add items, calculate totals
4. **Anonymous Checkout** - Guest contact information
5. **Payment Processing** - Dummy payment with auto-approval
6. **Order Creation** - Snapshot cart, decrement inventory
7. **Order Fulfillment** - Track through picking → shipping → delivery

## 🛠️ Development

### Build Commands
```bash
./gradlew clean compileJava           # Compile only
./gradlew build                       # Full build with tests
./gradlew validateWorkflowImplementations  # Validate workflows
```

### Project Structure
```
src/main/java/com/java_template/
├── Application.java                  # Main Spring Boot app
├── common/                          # Framework (DO NOT MODIFY)
└── application/                     # Business logic
    ├── controller/                  # REST endpoints
    ├── entity/                      # Domain entities
    └── processor/                   # Workflow processors

src/main/resources/
└── workflow/                        # Workflow JSON definitions
```

### Key Files
- `OMS_IMPLEMENTATION_SUMMARY.md` - Detailed implementation guide
- `test_oms_api.sh` - Complete API test script
- `src/main/resources/workflow/` - Workflow definitions
- `src/main/java/com/java_template/application/` - Business logic

## 📊 Validation Results

```
✅ 5 Workflow Files validated
✅ 5 Processors implemented and validated
✅ 0 Criteria (none required)
✅ All implementations match workflow definitions
✅ Full build successful with tests
```

## 🔒 Security & Configuration

- **Anonymous Access** - No authentication required for `/ui/**` endpoints
- **CORS Enabled** - Cross-origin requests supported
- **Server-Side Credentials** - Cyoda credentials managed securely
- **Swagger Documentation** - Available at `/swagger-ui/index.html`

## 📈 Performance Features

- **Technical IDs** - UUID-based for optimal Cyoda performance
- **Slim DTOs** - Lightweight responses for list endpoints
- **Efficient Queries** - Business ID lookups and search conditions
- **Async Processors** - Non-blocking workflow execution

## 🎯 Requirements Compliance

✅ Anonymous checkout only (no user accounts)  
✅ Payment auto-approves after ~3 seconds  
✅ Stock decremented on order creation  
✅ Single shipment per order  
✅ Short ULID order numbers  
✅ Catalog filters (category, text, price range)  
✅ Full Product schema implementation  
✅ UI-only endpoints (`/ui/**`)  

## 📚 Documentation

- **Implementation Summary** - `OMS_IMPLEMENTATION_SUMMARY.md`
- **API Testing** - `test_oms_api.sh`
- **Swagger UI** - `http://localhost:8080/swagger-ui/index.html`
- **Workflow Validation** - `./gradlew validateWorkflowImplementations`

## 🚀 Next Steps

1. **Frontend Integration** - Connect UI to REST APIs
2. **Data Seeding** - Add sample products
3. **Monitoring** - Add logging and metrics
4. **Testing** - Comprehensive integration tests
5. **Production** - Deploy to production environment

---

**Built with Cyoda's workflow-driven architecture for scalable, maintainable order management.**
