#!/bin/bash

# OMS API Test Script
# This script tests the complete happy path of the OMS system

BASE_URL="http://localhost:8080"
CONTENT_TYPE="Content-Type: application/json"

echo "=== OMS API Test Script ==="
echo "Testing complete e-commerce flow..."
echo

# Function to make HTTP requests with error handling
make_request() {
    local method=$1
    local url=$2
    local data=$3
    local description=$4
    
    echo "[$method] $description"
    echo "URL: $url"
    
    if [ -n "$data" ]; then
        echo "Data: $data"
        response=$(curl -s -X $method -H "$CONTENT_TYPE" -d "$data" "$url")
    else
        response=$(curl -s -X $method "$url")
    fi
    
    echo "Response: $response"
    echo "---"
    echo
}

# Test 1: Create a sample product
echo "=== Step 1: Create Sample Product ==="
PRODUCT_DATA='{
  "sku": "LAPTOP-001",
  "name": "Gaming Laptop Pro",
  "description": "High-performance gaming laptop with RTX graphics",
  "price": 1299.99,
  "quantityAvailable": 10,
  "category": "Electronics",
  "attributes": {
    "brand": "TechCorp",
    "model": "GamePro X1"
  }
}'

make_request "POST" "$BASE_URL/ui/products" "$PRODUCT_DATA" "Create sample product"

# Test 2: Search products
echo "=== Step 2: Search Products ==="
make_request "GET" "$BASE_URL/ui/products?category=Electronics" "" "Search products by category"

# Test 3: Get product by SKU
echo "=== Step 3: Get Product Details ==="
make_request "GET" "$BASE_URL/ui/products/LAPTOP-001" "" "Get product by SKU"

# Test 4: Create cart
echo "=== Step 4: Create Cart ==="
make_request "POST" "$BASE_URL/ui/cart" "" "Create new cart"

# Extract cart ID from response (this would need to be done programmatically)
echo "Note: Extract cartId from the response above for next steps"
echo

# Test 5: Add item to cart (replace CART-XXXXXXXX with actual cart ID)
echo "=== Step 5: Add Item to Cart ==="
echo "Replace CART-XXXXXXXX with actual cart ID from step 4"
ADD_ITEM_DATA='{
  "sku": "LAPTOP-001",
  "qty": 2
}'

make_request "POST" "$BASE_URL/ui/cart/CART-XXXXXXXX/lines" "$ADD_ITEM_DATA" "Add item to cart"

# Test 6: Open checkout
echo "=== Step 6: Open Checkout ==="
make_request "POST" "$BASE_URL/ui/cart/CART-XXXXXXXX/open-checkout" "" "Open checkout process"

# Test 7: Set guest contact
echo "=== Step 7: Set Guest Contact ==="
GUEST_CONTACT_DATA='{
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1234567890",
    "address": {
      "line1": "123 Main Street",
      "city": "New York",
      "postcode": "10001",
      "country": "USA"
    }
  }
}'

make_request "POST" "$BASE_URL/ui/checkout/CART-XXXXXXXX" "$GUEST_CONTACT_DATA" "Set guest contact"

# Test 8: Start payment
echo "=== Step 8: Start Payment ==="
PAYMENT_DATA='{
  "cartId": "CART-XXXXXXXX"
}'

make_request "POST" "$BASE_URL/ui/payment/start" "$PAYMENT_DATA" "Start payment"

# Test 9: Check payment status (replace PAY-XXXXXXXX with actual payment ID)
echo "=== Step 9: Check Payment Status ==="
echo "Replace PAY-XXXXXXXX with actual payment ID from step 8"
make_request "GET" "$BASE_URL/ui/payment/PAY-XXXXXXXX" "" "Check payment status"

echo "Wait 3+ seconds for auto-approval, then check again..."
echo

# Test 10: Create order (replace with actual payment and cart IDs)
echo "=== Step 10: Create Order ==="
ORDER_DATA='{
  "paymentId": "PAY-XXXXXXXX",
  "cartId": "CART-XXXXXXXX"
}'

make_request "POST" "$BASE_URL/ui/order/create" "$ORDER_DATA" "Create order from paid payment"

# Test 11: Get order details (replace ORDER-XXXXXXXX with actual order ID)
echo "=== Step 11: Get Order Details ==="
echo "Replace ORDER-XXXXXXXX with actual order ID from step 10"
make_request "GET" "$BASE_URL/ui/order/ORDER-XXXXXXXX" "" "Get order details"

echo "=== Test Complete ==="
echo "Manual steps required:"
echo "1. Replace placeholder IDs with actual values from responses"
echo "2. Wait for payment auto-approval (3 seconds)"
echo "3. Verify product inventory was decremented"
echo "4. Check that shipment was created"
echo
echo "Expected final state:"
echo "- Product quantity: 8 (10 - 2)"
echo "- Cart status: CONVERTED"
echo "- Payment status: PAID"
echo "- Order status: WAITING_TO_FULFILL"
echo "- Shipment status: PICKING"
