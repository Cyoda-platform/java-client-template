# OMS API Testing Guide

## Quick Start Testing

### Prerequisites
1. Start the application: `./gradlew bootRun`
2. Application runs on `http://localhost:8080`
3. Swagger UI available at: `http://localhost:8080/swagger-ui/index.html`

## Complete Happy Path Test

### 1. Create a Product
```bash
curl -X POST http://localhost:8080/ui/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "LAPTOP-001",
    "name": "Gaming Laptop",
    "description": "High-performance gaming laptop",
    "price": 1299.99,
    "quantityAvailable": 50,
    "category": "Electronics"
  }'
```

### 2. Search Products
```bash
# Search all products
curl "http://localhost:8080/ui/products"

# Search with filters
curl "http://localhost:8080/ui/products?category=Electronics&minPrice=1000&maxPrice=1500"

# Free-text search
curl "http://localhost:8080/ui/products?search=gaming"
```

### 3. Get Product Details
```bash
curl "http://localhost:8080/ui/products/LAPTOP-001"
```

### 4. Create Cart
```bash
curl -X POST http://localhost:8080/ui/cart
```
*Save the cartId from response*

### 5. Add Item to Cart
```bash
curl -X POST http://localhost:8080/ui/cart/{cartId}/lines \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "LAPTOP-001",
    "name": "Gaming Laptop",
    "price": 1299.99,
    "qty": 1
  }'
```

### 6. View Cart
```bash
curl "http://localhost:8080/ui/cart/{cartId}"
```

### 7. Open Checkout
```bash
curl -X POST "http://localhost:8080/ui/cart/{cartId}/open-checkout"
```

### 8. Set Guest Contact
```bash
curl -X POST http://localhost:8080/ui/checkout/{cartId} \
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

### 9. Start Payment
```bash
curl -X POST http://localhost:8080/ui/payment/start \
  -H "Content-Type: application/json" \
  -d '{
    "cartId": "{cartId}",
    "amount": 1299.99
  }'
```
*Save the paymentId from response*

### 10. Poll Payment Status (wait 3+ seconds)
```bash
curl "http://localhost:8080/ui/payment/{paymentId}"
```
*Status should be "PAID" after 3 seconds*

### 11. Create Order
```bash
curl -X POST http://localhost:8080/ui/order/create \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "{paymentId}",
    "cartId": "{cartId}"
  }'
```
*Save the orderId and orderNumber from response*

### 12. View Order
```bash
curl "http://localhost:8080/ui/order/{orderId}"
```

### 13. Check Product Stock (should be decremented)
```bash
curl "http://localhost:8080/ui/products/LAPTOP-001"
```
*quantityAvailable should now be 49*

### 14. View Shipments for Order
```bash
curl "http://localhost:8080/ui/shipment/order/{orderId}"
```
*Save the shipmentId from response*

### 15. Progress Order Through Fulfillment
```bash
# Start picking
curl -X PUT "http://localhost:8080/ui/order/{orderId}/status?transition=start_picking"

# Complete picking
curl -X PUT "http://localhost:8080/ui/shipment/{shipmentId}/status?transition=complete_picking"

# Mark as sent
curl -X PUT "http://localhost:8080/ui/shipment/{shipmentId}/status?transition=mark_sent"

# Mark as delivered
curl -X PUT "http://localhost:8080/ui/shipment/{shipmentId}/status?transition=mark_delivered"
```

### 16. Verify Final Order Status
```bash
curl "http://localhost:8080/ui/order/{orderId}"
```
*Status should be "DELIVERED"*

## Additional Test Scenarios

### Cart Management
```bash
# Update item quantity
curl -X PATCH http://localhost:8080/ui/cart/{cartId}/lines \
  -H "Content-Type: application/json" \
  -d '{"sku": "LAPTOP-001", "qty": 2}'

# Remove item (set qty to 0)
curl -X PATCH http://localhost:8080/ui/cart/{cartId}/lines \
  -H "Content-Type: application/json" \
  -d '{"sku": "LAPTOP-001", "qty": 0}'
```

### Payment Scenarios
```bash
# Cancel payment (only works if status is INITIATED)
curl -X POST "http://localhost:8080/ui/payment/{paymentId}/cancel"
```

### Shipment Tracking
```bash
# List all shipments
curl "http://localhost:8080/ui/shipment"

# Filter shipments by status
curl "http://localhost:8080/ui/shipment?status=PICKING"
```

## Error Testing

### Invalid Product Creation
```bash
curl -X POST http://localhost:8080/ui/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "",
    "name": "",
    "price": -10
  }'
```
*Should return validation errors*

### Duplicate Product SKU
```bash
# Try to create product with existing SKU
curl -X POST http://localhost:8080/ui/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "LAPTOP-001",
    "name": "Another Laptop",
    "description": "Different laptop",
    "price": 999.99,
    "quantityAvailable": 10,
    "category": "Electronics"
  }'
```
*Should return 409 Conflict*

### Order Creation with Unpaid Payment
```bash
# Try to create order with INITIATED payment
curl -X POST http://localhost:8080/ui/order/create \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "{unpaidPaymentId}",
    "cartId": "{cartId}"
  }'
```
*Should return error about payment status*

## Performance Testing

### Bulk Product Creation
Create multiple products to test search and pagination:

```bash
for i in {1..20}; do
  curl -X POST http://localhost:8080/ui/products \
    -H "Content-Type: application/json" \
    -d "{
      \"sku\": \"PROD-$i\",
      \"name\": \"Product $i\",
      \"description\": \"Test product number $i\",
      \"price\": $((100 + i * 10)),
      \"quantityAvailable\": $((10 + i)),
      \"category\": \"Category$((i % 3 + 1))\"
    }"
done
```

### Pagination Testing
```bash
# Test pagination
curl "http://localhost:8080/ui/products?page=0&size=5"
curl "http://localhost:8080/ui/products?page=1&size=5"
```

## Monitoring and Logs

### Check Application Logs
Monitor the application logs to see:
- Cart total recalculations
- Payment auto-approvals
- Stock decrements
- Order/shipment status synchronization

### Workflow Validation
```bash
./gradlew validateWorkflowImplementations
```

This testing guide covers all major functionality and edge cases of the OMS system.
