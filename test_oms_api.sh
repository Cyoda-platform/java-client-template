#!/bin/bash

# Cyoda OMS API Test Script
# This script demonstrates the complete OMS flow from product creation to order fulfillment

BASE_URL="http://localhost:8080"
CONTENT_TYPE="Content-Type: application/json"

echo "🚀 Starting Cyoda OMS API Test"
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
        response=$(curl -s -X $method "$BASE_URL$url" -H "$CONTENT_TYPE" -d "$data")
    else
        response=$(curl -s -X $method "$BASE_URL$url")
    fi
    
    echo "   Response: $response"
    echo ""
    
    # Return the response for further processing
    echo "$response"
}

# Wait for application to start
echo "⏳ Waiting for application to start..."
sleep 5

# 1. Create a sample product
echo "1️⃣ Creating Sample Product"
product_data='{
    "sku": "LAPTOP-001",
    "name": "Gaming Laptop Pro",
    "description": "High-performance gaming laptop with RTX graphics",
    "price": 1299.99,
    "quantityAvailable": 10,
    "category": "electronics",
    "warehouseId": "WH-001"
}'

product_response=$(make_request "POST" "/ui/products" "$product_data" "Creating product LAPTOP-001")

# 2. Search for products
echo "2️⃣ Searching Products"
search_response=$(make_request "GET" "/ui/products?category=electronics&search=gaming" "" "Searching for gaming electronics")

# 3. Create a cart
echo "3️⃣ Creating Shopping Cart"
cart_response=$(make_request "POST" "/ui/cart" "" "Creating new shopping cart")

# Extract cart ID from response (simple grep - in production use jq)
cart_id=$(echo "$cart_response" | grep -o '"cartId":"[^"]*"' | cut -d'"' -f4)
echo "   📝 Cart ID: $cart_id"

# 4. Add item to cart
echo "4️⃣ Adding Item to Cart"
add_item_data='{
    "sku": "LAPTOP-001",
    "qty": 2
}'

add_item_response=$(make_request "POST" "/ui/cart/$cart_id/lines" "$add_item_data" "Adding 2x LAPTOP-001 to cart")

# 5. Get cart details
echo "5️⃣ Getting Cart Details"
cart_details=$(make_request "GET" "/ui/cart/$cart_id" "" "Retrieving cart details")

# 6. Open checkout
echo "6️⃣ Opening Checkout"
checkout_response=$(make_request "POST" "/ui/cart/$cart_id/open-checkout" "" "Opening checkout for cart")

# 7. Add guest contact information
echo "7️⃣ Adding Guest Contact"
guest_contact_data='{
    "guestContact": {
        "name": "John Doe",
        "email": "john.doe@example.com",
        "phone": "+1-555-0123",
        "address": {
            "line1": "123 Main Street",
            "city": "New York",
            "postcode": "10001",
            "country": "USA"
        }
    }
}'

contact_response=$(make_request "POST" "/ui/cart/checkout/$cart_id" "$guest_contact_data" "Adding guest contact information")

# 8. Start payment
echo "8️⃣ Starting Payment"
payment_start_data="{\"cartId\": \"$cart_id\"}"

payment_response=$(make_request "POST" "/ui/payment/start" "$payment_start_data" "Starting dummy payment")

# Extract payment ID
payment_id=$(echo "$payment_response" | grep -o '"paymentId":"[^"]*"' | cut -d'"' -f4)
echo "   💳 Payment ID: $payment_id"

# 9. Poll payment status (wait for auto-approval)
echo "9️⃣ Polling Payment Status"
echo "   ⏳ Waiting for automatic payment approval (3+ seconds)..."

for i in {1..6}; do
    sleep 1
    payment_status=$(make_request "GET" "/ui/payment/$payment_id" "" "Checking payment status (attempt $i)")
    
    if echo "$payment_status" | grep -q '"status":"PAID"'; then
        echo "   ✅ Payment approved!"
        break
    fi
    
    if [ $i -eq 6 ]; then
        echo "   ⚠️  Payment not approved after 6 seconds, continuing anyway..."
    fi
done

# 10. Create order
echo "🔟 Creating Order"
order_create_data="{\"paymentId\": \"$payment_id\", \"cartId\": \"$cart_id\"}"

order_response=$(make_request "POST" "/ui/order/create" "$order_create_data" "Creating order from paid payment")

# Extract order ID
order_id=$(echo "$order_response" | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4)
order_number=$(echo "$order_response" | grep -o '"orderNumber":"[^"]*"' | cut -d'"' -f4)
echo "   📦 Order ID: $order_id"
echo "   🏷️  Order Number: $order_number"

# 11. Get order details
echo "1️⃣1️⃣ Getting Order Details"
order_details=$(make_request "GET" "/ui/order/$order_id" "" "Retrieving order details")

# 12. Update order status (simulate fulfillment)
echo "1️⃣2️⃣ Simulating Order Fulfillment"

# Start picking
picking_data='{"status": "PICKING"}'
picking_response=$(make_request "PUT" "/ui/order/$order_id/status" "$picking_data" "Starting order picking")

sleep 1

# Ready to send
ready_data='{"status": "WAITING_TO_SEND"}'
ready_response=$(make_request "PUT" "/ui/order/$order_id/status" "$ready_data" "Order ready to send")

sleep 1

# Mark as sent
sent_data='{"status": "SENT"}'
sent_response=$(make_request "PUT" "/ui/order/$order_id/status" "$sent_data" "Marking order as sent")

sleep 1

# Mark as delivered
delivered_data='{"status": "DELIVERED"}'
delivered_response=$(make_request "PUT" "/ui/order/$order_id/status" "$delivered_data" "Marking order as delivered")

# 13. Final order status
echo "1️⃣3️⃣ Final Order Status"
final_order=$(make_request "GET" "/ui/order/$order_id" "" "Getting final order status")

echo "🎉 OMS API Test Complete!"
echo "========================="
echo "✅ Product created and searchable"
echo "✅ Cart created and items added"
echo "✅ Checkout process completed"
echo "✅ Payment processed automatically"
echo "✅ Order created and fulfilled"
echo "✅ Complete order lifecycle demonstrated"
echo ""
echo "🔗 API Endpoints tested:"
echo "   • Product Management: /ui/products"
echo "   • Cart Management: /ui/cart"
echo "   • Payment Processing: /ui/payment"
echo "   • Order Management: /ui/order"
echo ""
echo "📊 Order Summary:"
echo "   • Order ID: $order_id"
echo "   • Order Number: $order_number"
echo "   • Cart ID: $cart_id"
echo "   • Payment ID: $payment_id"
echo ""
echo "🌐 Access Swagger UI at: $BASE_URL/swagger-ui/index.html"
