# OMS Backend Implementation Summary

## Overview
This document describes the complete implementation of an Order Management System (OMS) backend built with Spring Boot and Cyoda integration. The application provides REST APIs for an e-commerce platform with product catalog, shopping cart, payment processing, and order fulfillment capabilities.

## Architecture

### Core Entities (5 total)

#### 1. **Product** (`application/entity/product/version_1/Product.java`)
- Full e-commerce product schema with comprehensive fields
- Includes: SKU, name, description, price, quantity, category
- Extended fields: attributes, localizations, media, options, variants, bundles, inventory, compliance, relationships, events
- Workflow: `initial` → `active` ↔ `inactive`
- States: `initial`, `active`, `inactive`

#### 2. **Cart** (`application/entity/cart/version_1/Cart.java`)
- Shopping cart with line items and totals
- Fields: cartId, status, lines, totalItems, grandTotal, guestContact
- Workflow: `initial` → `active` → `checking_out` → `converted`
- States: `NEW`, `ACTIVE`, `CHECKING_OUT`, `CONVERTED`
- Transitions: `create_on_first_add`, `add_item`, `decrement_item`, `remove_item`, `open_checkout`, `checkout`

#### 3. **Payment** (`application/entity/payment/version_1/Payment.java`)
- Dummy payment provider with auto-approval
- Fields: paymentId, cartId, amount, status, provider
- Workflow: `initial` → `initiated` → (`paid` | `failed` | `canceled`)
- States: `INITIATED`, `PAID`, `FAILED`, `CANCELED`
- Auto-transitions: `auto_mark_paid` (3-second delay)

#### 4. **Order** (`application/entity/order/version_1/Order.java`)
- Customer order with fulfillment tracking
- Fields: orderId, orderNumber (ULID), status, lines, totals, guestContact
- Workflow: `initial` → `waiting_to_fulfill` → `picking` → `waiting_to_send` → `sent` → `delivered`
- States: `WAITING_TO_FULFILL`, `PICKING`, `WAITING_TO_SEND`, `SENT`, `DELIVERED`

#### 5. **Shipment** (`application/entity/shipment/version_1/Shipment.java`)
- Single shipment per order with line tracking
- Fields: shipmentId, orderId, status, lines (with qtyOrdered, qtyPicked, qtyShipped)
- Workflow: `initial` → `picking` → `waiting_to_send` → `sent` → `delivered`
- States: `PICKING`, `WAITING_TO_SEND`, `SENT`, `DELIVERED`

## Processors (3 total)

### 1. **RecalculateTotals** (`application/processor/RecalculateTotals.java`)
- Triggered on cart line changes
- Calculates total items and grand total from cart lines
- Updates line totals (price × qty)
- Used by transitions: `create_on_first_add`, `add_item`, `decrement_item`, `remove_item`

### 2. **AutoMarkPaid** (`application/processor/AutoMarkPaid.java`)
- Simulates dummy payment provider auto-approval
- 3-second delay to simulate payment processing
- Marks payment status as `PAID`
- Used by transition: `auto_mark_paid`

### 3. **CreateOrderFromPaid** (`application/processor/CreateOrderFromPaid.java`)
- Creates order from paid payment
- Snapshots cart lines and guest contact into order
- Decrements product quantities based on ordered amounts
- Creates single shipment in `PICKING` state
- Generates short ULID order number
- Used by transition: `create_order_from_paid`

## REST Controllers (5 total)

### 1. **ProductController** (`/ui/products`)
- `GET /ui/products` - List products with filtering
  - Query params: `search`, `category`, `minPrice`, `maxPrice`, `page`, `pageSize`
  - Returns slim DTOs: sku, name, description, price, quantityAvailable, category, imageUrl
  - Supports free-text search on name/description, category filtering, price range
- `GET /ui/products/{sku}` - Get full product document by SKU

### 2. **CartController** (`/ui/cart`)
- `POST /ui/cart` - Create new cart
- `GET /ui/cart/{cartId}` - Get cart by ID
- `POST /ui/cart/{cartId}/lines` - Add/increment item
- `PATCH /ui/cart/{cartId}/lines` - Update/remove item (qty=0 removes)
- `POST /ui/cart/{cartId}/open-checkout` - Transition to checkout

### 3. **CheckoutController** (`/ui/checkout`)
- `POST /ui/checkout/{cartId}` - Attach guest contact and proceed to checkout
- Accepts: name, email, phone, address (line1, city, postcode, country)

### 4. **PaymentController** (`/ui/payment`)
- `POST /ui/payment/start` - Create payment and trigger auto-mark-paid
  - Request: `{ "cartId": "..." }`
  - Response: `{ "paymentId": "..." }`
- `GET /ui/payment/{paymentId}` - Poll payment status

### 5. **OrderController** (`/ui/order`)
- `POST /ui/order/create` - Create order from paid payment
  - Request: `{ "paymentId": "...", "cartId": "..." }`
  - Response: `{ "orderId": "...", "orderNumber": "...", "status": "..." }`
  - Precondition: Payment must be in PAID status
- `GET /ui/order/{orderId}` - Get order by ID

## Workflow JSON Files

All workflow definitions are located in `src/main/resources/workflow/{entity}/version_1/{Entity}.json`:
- `Product.json` - Product lifecycle
- `Cart.json` - Shopping cart with recalculation processors
- `Payment.json` - Dummy payment with auto-approval
- `Order.json` - Order fulfillment lifecycle
- `Shipment.json` - Shipment tracking

## Key Features

### 1. **Anonymous Checkout**
- No user authentication required
- Guest contact information captured at checkout

### 2. **Dummy Payment Processing**
- Auto-approval after ~3 seconds
- Simulates real payment provider behavior

### 3. **Stock Management**
- Product quantities decremented on order creation
- No reservations (immediate decrement)

### 4. **Order Fulfillment**
- Single shipment per order
- Tracking of quantities: ordered, picked, shipped
- Order status derived from shipment status

### 5. **Product Catalog**
- Full schema persistence with all fields
- Slim DTO for list views (performance optimized)
- Full document for detail views
- Multi-criteria filtering: search, category, price range

## API Usage Flow

### Happy Path Example

1. **Browse Products**
   ```
   GET /ui/products?category=Electronics&minPrice=100&maxPrice=500
   ```

2. **Create Cart**
   ```
   POST /ui/cart
   ```

3. **Add Items to Cart**
   ```
   POST /ui/cart/{cartId}/lines
   { "sku": "PROD-001", "name": "Product", "price": 299.99, "qty": 2 }
   ```

4. **Open Checkout**
   ```
   POST /ui/cart/{cartId}/open-checkout
   ```

5. **Attach Guest Contact**
   ```
   POST /ui/checkout/{cartId}
   { "guestContact": { "name": "John Doe", "email": "john@example.com", 
     "address": { "line1": "123 Main St", "city": "NYC", "postcode": "10001", "country": "US" } } }
   ```

6. **Start Payment**
   ```
   POST /ui/payment/start
   { "cartId": "{cartId}" }
   ```
   (Auto-marks as PAID after 3 seconds)

7. **Create Order**
   ```
   POST /ui/order/create
   { "paymentId": "{paymentId}", "cartId": "{cartId}" }
   ```
   (Creates order, decrements stock, creates shipment)

8. **Track Order**
   ```
   GET /ui/order/{orderId}
   ```

## Build & Deployment

### Build
```bash
./gradlew build
```

### Run
```bash
./gradlew bootRun
```

### Swagger UI
```
http://localhost:8080/swagger-ui/index.html
```

## Validation

All entities implement proper validation:
- Required fields checked in `isValid(EntityMetadata metadata)`
- Business identifiers (SKU, cartId, paymentId, orderId, shipmentId) validated
- Workflow transitions validated against JSON definitions

## Notes

- All transitions use manual mode where appropriate
- Processors use ASYNC_NEW_TX execution mode
- EntityService used for cross-entity operations (e.g., product quantity updates)
- No reflection used (framework requirement)
- Common directory untouched (framework code)
- Project compiles successfully with no errors

