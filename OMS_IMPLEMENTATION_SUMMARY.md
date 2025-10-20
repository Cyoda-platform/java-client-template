# OMS (Order Management System) Implementation Summary

## Overview

This project implements a complete **Order Management System (OMS)** backend using Spring Boot and the Cyoda platform. The system provides REST APIs for a browser UI to manage an anonymous e-commerce checkout flow with dummy payment processing.

## What Was Built

### 🏗️ **Core Architecture**
- **5 Entities**: Product, Cart, Payment, Order, Shipment
- **3 Processors**: RecalculateTotals, AutoMarkPaidAfter3s, CreateOrderFromPaid
- **5 Controllers**: ProductController, CartController, PaymentController, OrderController, CheckoutController
- **5 Workflows**: Complete state machine definitions for each entity lifecycle

### 📦 **Entities Implemented**

#### 1. **Product** (`/ui/products/*`)
- **Full schema compliance** with the attached Product specification
- Complex nested structures: attributes, localizations, media, options, variants, bundles, inventory, compliance, relationships
- **Search & filtering**: category, free-text (name/description), price range
- **Dual views**: Slim DTO for lists, full entity for details

#### 2. **Cart** (`/ui/cart/*`)
- **Lifecycle**: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- Line item management with automatic total recalculation
- Guest contact information for anonymous checkout
- Stock validation before adding items

#### 3. **Payment** (`/ui/payment/*`)
- **Dummy payment processing** with 3-second auto-approval
- **Lifecycle**: INITIATED → PAID | FAILED | CANCELED
- Integration with cart for payment amount calculation

#### 4. **Order** (`/ui/order/*`)
- **Lifecycle**: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
- Order creation from paid payments with cart snapshot
- Short ULID order numbers for customer reference
- Complete guest contact and address information

#### 5. **Shipment**
- **Single shipment per order** (as per requirements)
- **Lifecycle**: PICKING → WAITING_TO_SEND → SENT → DELIVERED
- Automatic creation when order is processed
- Line-level quantity tracking (ordered, picked, shipped)

### ⚙️ **Business Logic Processors**

#### 1. **RecalculateTotals**
- Calculates line totals (price × quantity)
- Updates cart total items count and grand total
- Triggered on all cart line modifications

#### 2. **AutoMarkPaidAfter3s**
- Simulates payment processing with 3-second delay
- Automatically marks dummy payments as PAID
- Handles interruption gracefully (marks as FAILED)

#### 3. **CreateOrderFromPaid**
- **Snapshots cart data** into order structure
- **Decrements product stock** for each order line
- **Creates shipment** automatically in PICKING status
- Handles stock updates with proper error handling

### 🌐 **REST API Endpoints**

#### **Product Catalog** (`/ui/products`)
```
GET  /ui/products?search=&category=&minPrice=&maxPrice=&page=&size=  # Search products
GET  /ui/products/{sku}                                              # Get full product
```

#### **Cart Management** (`/ui/cart`)
```
POST   /ui/cart                    # Create new cart
GET    /ui/cart/{cartId}           # Get cart details
POST   /ui/cart/{cartId}/lines     # Add item to cart
PATCH  /ui/cart/{cartId}/lines     # Update item quantity
POST   /ui/cart/{cartId}/open-checkout  # Open checkout
```

#### **Anonymous Checkout** (`/ui/checkout`)
```
POST /ui/checkout/{cartId}         # Submit guest contact info
GET  /ui/checkout/{cartId}         # Get checkout details
```

#### **Payment Processing** (`/ui/payment`)
```
POST /ui/payment/start             # Start dummy payment
GET  /ui/payment/{paymentId}       # Poll payment status
POST /ui/payment/{paymentId}/cancel  # Cancel payment
```

#### **Order Management** (`/ui/order`)
```
POST /ui/order/create              # Create order from paid payment
GET  /ui/order/{orderId}           # Get order details
POST /ui/order/{orderId}/status    # Update order status
```

## 🔄 **Complete Workflow**

### **Happy Path Flow**
1. **Browse Products**: `GET /ui/products` with filters
2. **Create Cart**: `POST /ui/cart` (first add creates cart)
3. **Add Items**: `POST /ui/cart/{cartId}/lines` (validates stock, recalculates totals)
4. **Open Checkout**: `POST /ui/cart/{cartId}/open-checkout`
5. **Submit Contact**: `POST /ui/checkout/{cartId}` (guest information)
6. **Start Payment**: `POST /ui/payment/start` (auto-approves in 3s)
7. **Create Order**: `POST /ui/order/create` (decrements stock, creates shipment)
8. **Track Order**: `GET /ui/order/{orderId}` (status updates)

### **Key Business Rules Implemented**
- ✅ **Anonymous checkout only** (no user accounts)
- ✅ **Payment auto-approves** after ~3 seconds
- ✅ **Stock policy**: decrement Product.quantityAvailable on order creation
- ✅ **Single shipment per order**
- ✅ **Short ULID order numbers**
- ✅ **Complete product schema** persistence and round-trip

## 🧪 **How to Validate It Works**

### **1. Build and Start**
```bash
./gradlew build                    # Compile and test
./gradlew bootRun                  # Start the application
```

### **2. Workflow Validation**
```bash
./gradlew validateWorkflowImplementations  # Validates all processors/criteria exist
```

### **3. API Testing with curl**

#### **Create a Product**
```bash
curl -X POST http://localhost:8080/ui/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "LAPTOP-001",
    "name": "Gaming Laptop",
    "description": "High-performance gaming laptop",
    "price": 1299.99,
    "quantityAvailable": 10,
    "category": "Electronics"
  }'
```

#### **Test Complete Checkout Flow**
```bash
# 1. Create cart and add item
CART_RESPONSE=$(curl -s -X POST http://localhost:8080/ui/cart)
CART_ID=$(echo $CART_RESPONSE | jq -r '.entity.cartId')

curl -X POST http://localhost:8080/ui/cart/$CART_ID/lines \
  -H "Content-Type: application/json" \
  -d '{"sku": "LAPTOP-001", "qty": 1}'

# 2. Open checkout
curl -X POST http://localhost:8080/ui/cart/$CART_ID/open-checkout

# 3. Submit guest info
curl -X POST http://localhost:8080/ui/checkout/$CART_ID \
  -H "Content-Type: application/json" \
  -d '{
    "guestContact": {
      "name": "John Doe",
      "email": "john@example.com",
      "address": {
        "line1": "123 Main St",
        "city": "London",
        "postcode": "SW1A 1AA",
        "country": "UK"
      }
    }
  }'

# 4. Start payment
PAYMENT_RESPONSE=$(curl -s -X POST http://localhost:8080/ui/payment/start \
  -H "Content-Type: application/json" \
  -d "{\"cartId\": \"$CART_ID\"}")
PAYMENT_ID=$(echo $PAYMENT_RESPONSE | jq -r '.paymentId')

# 5. Wait 3+ seconds, then create order
sleep 4
curl -X POST http://localhost:8080/ui/order/create \
  -H "Content-Type: application/json" \
  -d "{\"paymentId\": \"$PAYMENT_ID\", \"cartId\": \"$CART_ID\"}"
```

### **4. Swagger UI**
Access interactive API documentation at: `http://localhost:8080/swagger-ui/index.html`

## 🏆 **Success Criteria Met**

- ✅ **Full compilation**: `./gradlew build` succeeds
- ✅ **Requirements coverage**: All user requirements implemented
- ✅ **Workflow compliance**: All transitions follow manual/automatic rules
- ✅ **Architecture adherence**: No reflection, thin controllers, proper separation
- ✅ **Complete product schema**: Full round-trip persistence
- ✅ **Anonymous checkout**: No user accounts required
- ✅ **Stock management**: Automatic decrementing on order creation
- ✅ **Dummy payments**: 3-second auto-approval
- ✅ **Order lifecycle**: Complete fulfillment workflow

## 🔧 **Technical Implementation Details**

- **Framework**: Spring Boot with Cyoda integration
- **Build Tool**: Gradle
- **Validation**: Workflow implementation validator passes
- **Error Handling**: RFC 7807 ProblemDetail responses
- **Logging**: Comprehensive business operation logging
- **Performance**: Technical ID optimization for API responses
- **Security**: CORS enabled for browser integration

The implementation provides a complete, production-ready OMS backend that can be integrated with any frontend UI for e-commerce operations.
