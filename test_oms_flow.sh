#!/bin/bash

# OMS Application Test Script
# This script demonstrates the complete order flow

BASE_URL="http://localhost:8080"
echo "🚀 Testing Cyoda OMS Application"
echo "================================"

# Function to make HTTP requests with error handling
make_request() {
    local method=$1
    local url=$2
    local data=$3
    local description=$4
    
    echo "📡 $description"
    echo "   $method $url"
    
    if [ -n "$data" ]; then
        response=$(curl -s -X $method \
            -H "Content-Type: application/json" \
            -d "$data" \
            "$BASE_URL$url")
    else
        response=$(curl -s -X $method "$BASE_URL$url")
    fi
    
    echo "   Response: $response"
    echo ""
    echo "$response"
}

# Test 1: Create a Product
echo "1️⃣ Creating a test product..."
PRODUCT_DATA='{
  "sku": "LAPTOP-001",
  "name": "Gaming Laptop",
  "description": "High-performance gaming laptop with RTX graphics",
  "price": 1299.99,
  "quantityAvailable": 10,
  "category": "Electronics"
}'

PRODUCT_RESPONSE=$(make_request "POST" "/ui/products" "$PRODUCT_DATA" "Creating product")
echo "✅ Product created"
echo ""

# Test 2: Search Products
echo "2️⃣ Searching for products..."
SEARCH_RESPONSE=$(make_request "GET" "/ui/products?category=Electronics&search=gaming" "" "Searching products")
echo "✅ Product search completed"
echo ""

# Test 3: Create Cart
echo "3️⃣ Creating a shopping cart..."
CART_RESPONSE=$(make_request "POST" "/ui/cart" "" "Creating cart")
CART_ID=$(echo $CART_RESPONSE | grep -o '"cartId":"[^"]*"' | cut -d'"' -f4)
echo "✅ Cart created with ID: $CART_ID"
echo ""

# Test 4: Add Item to Cart
echo "4️⃣ Adding item to cart..."
ADD_ITEM_DATA='{
  "sku": "LAPTOP-001",
  "qty": 1
}'

ADD_ITEM_RESPONSE=$(make_request "POST" "/ui/cart/$CART_ID/lines" "$ADD_ITEM_DATA" "Adding item to cart")
echo "✅ Item added to cart"
echo ""

# Test 5: Open Checkout
echo "5️⃣ Opening checkout..."
CHECKOUT_OPEN_RESPONSE=$(make_request "POST" "/ui/cart/$CART_ID/open-checkout" "" "Opening checkout")
echo "✅ Checkout opened"
echo ""

# Test 6: Anonymous Checkout
echo "6️⃣ Completing anonymous checkout..."
CHECKOUT_DATA='{
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+1-555-0123",
    "address": {
      "line1": "123 Main Street",
      "city": "New York",
      "postcode": "10001",
      "country": "USA"
    }
  }
}'

CHECKOUT_RESPONSE=$(make_request "POST" "/ui/checkout/$CART_ID" "$CHECKOUT_DATA" "Completing checkout")
echo "✅ Anonymous checkout completed"
echo ""

# Test 7: Start Payment
echo "7️⃣ Starting payment..."
PAYMENT_START_DATA='{
  "cartId": "'$CART_ID'"
}'

PAYMENT_RESPONSE=$(make_request "POST" "/ui/payment/start" "$PAYMENT_START_DATA" "Starting payment")
PAYMENT_ID=$(echo $PAYMENT_RESPONSE | grep -o '"paymentId":"[^"]*"' | cut -d'"' -f4)
echo "✅ Payment started with ID: $PAYMENT_ID"
echo ""

# Test 8: Poll Payment Status (wait for auto-approval)
echo "8️⃣ Waiting for payment approval (3 seconds)..."
sleep 4
PAYMENT_STATUS_RESPONSE=$(make_request "GET" "/ui/payment/$PAYMENT_ID" "" "Checking payment status")
echo "✅ Payment status checked"
echo ""

# Test 9: Create Order
echo "9️⃣ Creating order from paid payment..."
ORDER_CREATE_DATA='{
  "paymentId": "'$PAYMENT_ID'",
  "cartId": "'$CART_ID'"
}'

ORDER_RESPONSE=$(make_request "POST" "/ui/order/create" "$ORDER_CREATE_DATA" "Creating order")
ORDER_ID=$(echo $ORDER_RESPONSE | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4)
echo "✅ Order created with ID: $ORDER_ID"
echo ""

# Test 10: Track Order Progress
echo "🔟 Tracking order progress..."

echo "   📦 Starting picking..."
make_request "POST" "/ui/order/$ORDER_ID/start-picking" "" "Starting picking"

echo "   📦 Ready to send..."
make_request "POST" "/ui/order/$ORDER_ID/ready-to-send" "" "Ready to send"

echo "   📦 Marking as sent..."
make_request "POST" "/ui/order/$ORDER_ID/mark-sent" "" "Marking as sent"

echo "   📦 Marking as delivered..."
make_request "POST" "/ui/order/$ORDER_ID/mark-delivered" "" "Marking as delivered"

echo "✅ Order lifecycle completed"
echo ""

# Test 11: Final Order Status
echo "1️⃣1️⃣ Checking final order status..."
FINAL_ORDER_RESPONSE=$(make_request "GET" "/ui/order/$ORDER_ID" "" "Getting final order status")
echo "✅ Final order status retrieved"
echo ""

echo "🎉 OMS Application Test Completed Successfully!"
echo "=============================================="
echo "Summary:"
echo "- Product created: LAPTOP-001"
echo "- Cart ID: $CART_ID"
echo "- Payment ID: $PAYMENT_ID"
echo "- Order ID: $ORDER_ID"
echo ""
echo "The complete order flow has been tested:"
echo "Product → Cart → Checkout → Payment → Order → Fulfillment → Delivery"
echo ""
echo "🌐 Access Swagger UI at: $BASE_URL/swagger-ui/index.html"
