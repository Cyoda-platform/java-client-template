# OMS API Testing Guide

## Quick Start

### 1. Start the Application
```bash
./gradlew bootRun
```

### 2. Access Swagger UI
Open: `http://localhost:8080/swagger-ui/index.html`

## Manual Testing Workflow

### Step 1: Create Sample Products
```bash
# Create Product 1
curl -X POST "http://localhost:8080/ui/products" \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "LAPTOP-001",
    "name": "Gaming Laptop",
    "description": "High-performance gaming laptop",
    "price": 1299.99,
    "quantityAvailable": 10,
    "category": "Electronics"
  }'

# Create Product 2
curl -X POST "http://localhost:8080/ui/products" \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "MOUSE-001",
    "name": "Gaming Mouse",
    "description": "RGB gaming mouse",
    "price": 79.99,
    "quantityAvailable": 25,
    "category": "Electronics"
  }'
```

### Step 2: Browse Products
```bash
# Search all products
curl "http://localhost:8080/ui/products"

# Search by category
curl "http://localhost:8080/ui/products?category=Electronics"

# Search with text
curl "http://localhost:8080/ui/products?search=gaming"

# Get product details
curl "http://localhost:8080/ui/products/LAPTOP-001"
```

### Step 3: Create and Manage Cart
```bash
# Create cart
CART_RESPONSE=$(curl -X POST "http://localhost:8080/ui/cart")
CART_ID=$(echo $CART_RESPONSE | jq -r '.entity.cartId')

# Add items to cart
curl -X POST "http://localhost:8080/ui/cart/$CART_ID/lines" \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "LAPTOP-001",
    "qty": 1
  }'

curl -X POST "http://localhost:8080/ui/cart/$CART_ID/lines" \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "MOUSE-001",
    "qty": 2
  }'

# View cart
curl "http://localhost:8080/ui/cart/$CART_ID"

# Update item quantity
curl -X PATCH "http://localhost:8080/ui/cart/$CART_ID/lines" \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "MOUSE-001",
    "qty": 1
  }'
```

### Step 4: Checkout Process
```bash
# Open checkout
curl -X POST "http://localhost:8080/ui/cart/$CART_ID/open-checkout"

# Add guest contact information
curl -X POST "http://localhost:8080/ui/cart/$CART_ID/checkout" \
  -H "Content-Type: application/json" \
  -d '{
    "guestContact": {
      "name": "John Doe",
      "email": "john@example.com",
      "phone": "+1234567890",
      "address": {
        "line1": "123 Main St",
        "city": "Anytown",
        "postcode": "12345",
        "country": "USA"
      }
    }
  }'
```

### Step 5: Payment Processing
```bash
# Start payment
PAYMENT_RESPONSE=$(curl -X POST "http://localhost:8080/ui/payment/start" \
  -H "Content-Type: application/json" \
  -d '{
    "cartId": "'$CART_ID'",
    "amount": 1379.98
  }')

PAYMENT_ID=$(echo $PAYMENT_RESPONSE | jq -r '.paymentId')

# Poll payment status (wait for auto-approval)
sleep 4
curl "http://localhost:8080/ui/payment/$PAYMENT_ID"
```

### Step 6: Order Creation
```bash
# Create order from paid cart
ORDER_RESPONSE=$(curl -X POST "http://localhost:8080/ui/order/create" \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "'$PAYMENT_ID'",
    "cartId": "'$CART_ID'"
  }')

ORDER_ID=$(echo $ORDER_RESPONSE | jq -r '.orderId')

# View order details
curl "http://localhost:8080/ui/order/$ORDER_ID"
```

### Step 7: Order Fulfillment
```bash
# Update order status through fulfillment workflow
curl -X POST "http://localhost:8080/ui/order/$ORDER_ID/status" \
  -H "Content-Type: application/json" \
  -d '{"status": "PICKING"}'

curl -X POST "http://localhost:8080/ui/order/$ORDER_ID/status" \
  -H "Content-Type: application/json" \
  -d '{"status": "WAITING_TO_SEND"}'

curl -X POST "http://localhost:8080/ui/order/$ORDER_ID/status" \
  -H "Content-Type: application/json" \
  -d '{"status": "SENT"}'

curl -X POST "http://localhost:8080/ui/order/$ORDER_ID/status" \
  -H "Content-Type: application/json" \
  -d '{"status": "DELIVERED"}'

# Final order status
curl "http://localhost:8080/ui/order/$ORDER_ID"
```

## Expected Results

### Product Search
- Returns slim DTOs with essential fields
- Supports filtering by category, price range, and text search
- Pagination works correctly

### Cart Management
- Totals automatically recalculated when items added/updated
- Stock availability checked before adding items
- Cart status progresses through workflow states

### Payment Processing
- Payment automatically approved after ~3 seconds
- Status polling returns current payment state
- Payment cancellation works for non-paid payments

### Order Creation
- Order created with snapshotted cart data
- Product stock decremented automatically
- Shipment created in PICKING status
- Cart marked as CONVERTED

### Order Fulfillment
- Order status progresses through workflow states
- Status updates trigger appropriate transitions
- Final DELIVERED state reached

## Validation Checklist

- [ ] Products can be created and searched
- [ ] Cart totals recalculate automatically
- [ ] Stock availability is enforced
- [ ] Payment auto-approves after 3 seconds
- [ ] Orders are created from paid carts
- [ ] Product stock is decremented
- [ ] Shipments are created automatically
- [ ] Order status can be updated through fulfillment
- [ ] All API endpoints return proper HTTP status codes
- [ ] Error handling works for invalid requests

## Common Issues & Solutions

### Build Issues
```bash
# Clean and rebuild
./gradlew clean build
```

### Port Conflicts
```bash
# Check if port 8080 is in use
lsof -i :8080
```

### Database Issues
- Cyoda handles entity persistence automatically
- No manual database setup required

### Workflow Issues
- Check workflow JSON syntax in `src/main/resources/workflow/`
- Verify processor names match class names exactly
- Ensure transitions are properly configured as manual/automatic

## Performance Testing

### Load Testing with curl
```bash
# Create multiple products
for i in {1..10}; do
  curl -X POST "http://localhost:8080/ui/products" \
    -H "Content-Type: application/json" \
    -d '{
      "sku": "PROD-'$i'",
      "name": "Product '$i'",
      "description": "Test product '$i'",
      "price": '$((i * 10))',
      "quantityAvailable": 100,
      "category": "Test"
    }'
done

# Test search performance
time curl "http://localhost:8080/ui/products?search=Product"
```

This testing guide provides comprehensive validation of all implemented features and ensures the OMS system works as expected.
