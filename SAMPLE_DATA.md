# Sample Data for OMS Testing

## Sample Products

### Product 1: Smartphone
```json
{
  "sku": "PHONE-001",
  "name": "Premium Smartphone",
  "description": "High-end smartphone with advanced features",
  "price": 899.99,
  "quantityAvailable": 50,
  "category": "electronics",
  "warehouseId": "WH-001",
  "attributes": {
    "brand": "TechCorp",
    "model": "Pro Max",
    "dimensions": {
      "l": 15.7,
      "w": 7.6,
      "h": 0.8,
      "unit": "cm"
    },
    "weight": {
      "value": 0.2,
      "unit": "kg"
    }
  },
  "localizations": {
    "defaultLocale": "en-US",
    "content": [
      {
        "locale": "en-US",
        "name": "Premium Smartphone",
        "description": "High-end smartphone with advanced features"
      }
    ]
  },
  "media": [
    {
      "type": "image",
      "url": "https://example.com/phone-001.jpg",
      "alt": "Premium Smartphone",
      "tags": ["hero"]
    }
  ]
}
```

### Product 2: Laptop
```json
{
  "sku": "LAPTOP-001",
  "name": "Business Laptop",
  "description": "Professional laptop for business use",
  "price": 1299.99,
  "quantityAvailable": 25,
  "category": "electronics",
  "warehouseId": "WH-001",
  "attributes": {
    "brand": "CompuTech",
    "model": "Business Pro",
    "dimensions": {
      "l": 35.0,
      "w": 24.0,
      "h": 2.0,
      "unit": "cm"
    },
    "weight": {
      "value": 1.8,
      "unit": "kg"
    }
  },
  "media": [
    {
      "type": "image",
      "url": "https://example.com/laptop-001.jpg",
      "alt": "Business Laptop",
      "tags": ["hero"]
    }
  ]
}
```

### Product 3: Headphones
```json
{
  "sku": "HEADPHONES-001",
  "name": "Wireless Headphones",
  "description": "Premium wireless headphones with noise cancellation",
  "price": 299.99,
  "quantityAvailable": 100,
  "category": "electronics",
  "warehouseId": "WH-001",
  "attributes": {
    "brand": "AudioTech",
    "model": "Silence Pro",
    "weight": {
      "value": 0.3,
      "unit": "kg"
    }
  },
  "media": [
    {
      "type": "image",
      "url": "https://example.com/headphones-001.jpg",
      "alt": "Wireless Headphones",
      "tags": ["hero"]
    }
  ]
}
```

## Sample Cart Operations

### 1. Create Cart
```bash
curl -X POST http://localhost:8080/ui/cart \
  -H "Content-Type: application/json"
```

### 2. Add Items to Cart
```bash
# Add smartphone
curl -X POST http://localhost:8080/ui/cart/{cartId}/lines \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "PHONE-001",
    "qty": 1
  }'

# Add headphones
curl -X POST http://localhost:8080/ui/cart/{cartId}/lines \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "HEADPHONES-001",
    "qty": 2
  }'
```

### 3. Open Checkout
```bash
curl -X POST http://localhost:8080/ui/cart/{cartId}/open-checkout \
  -H "Content-Type: application/json"
```

## Sample Checkout Process

### Set Guest Contact Information
```bash
curl -X POST http://localhost:8080/ui/checkout/{cartId} \
  -H "Content-Type: application/json" \
  -d '{
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
```

## Sample Payment Process

### Start Payment
```bash
curl -X POST http://localhost:8080/ui/payment/start \
  -H "Content-Type: application/json" \
  -d '{
    "cartId": "{cartId}"
  }'
```

### Poll Payment Status
```bash
curl -X GET http://localhost:8080/ui/payment/{paymentId} \
  -H "Content-Type: application/json"
```

## Sample Order Creation

### Create Order from Paid Cart
```bash
curl -X POST http://localhost:8080/ui/order/create \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "{paymentId}",
    "cartId": "{cartId}"
  }'
```

### Update Order Status
```bash
# Start picking
curl -X POST http://localhost:8080/ui/order/{orderId}/transition/start_picking \
  -H "Content-Type: application/json"

# Ready to send
curl -X POST http://localhost:8080/ui/order/{orderId}/transition/ready_to_send \
  -H "Content-Type: application/json"

# Mark sent
curl -X POST http://localhost:8080/ui/order/{orderId}/transition/mark_sent \
  -H "Content-Type: application/json"

# Mark delivered
curl -X POST http://localhost:8080/ui/order/{orderId}/transition/mark_delivered \
  -H "Content-Type: application/json"
```

## Sample Product Search Queries

### Search by Category
```bash
curl -X GET "http://localhost:8080/ui/products?category=electronics" \
  -H "Content-Type: application/json"
```

### Search by Text
```bash
curl -X GET "http://localhost:8080/ui/products?search=smartphone" \
  -H "Content-Type: application/json"
```

### Search by Price Range
```bash
curl -X GET "http://localhost:8080/ui/products?minPrice=200&maxPrice=1000" \
  -H "Content-Type: application/json"
```

### Combined Search
```bash
curl -X GET "http://localhost:8080/ui/products?category=electronics&search=wireless&minPrice=100&maxPrice=500" \
  -H "Content-Type: application/json"
```

## Testing Sequence

1. **Setup Products**: Create the sample products above
2. **Product Search**: Test various search combinations
3. **Cart Management**: Create cart, add items, modify quantities
4. **Checkout Process**: Open checkout, set guest contact
5. **Payment Flow**: Start payment, wait 3+ seconds, check status
6. **Order Creation**: Create order from paid cart
7. **Order Fulfillment**: Progress order through all states
8. **Inventory Verification**: Check product quantities were decremented

## Expected Behavior

- **Cart totals** automatically recalculated when items added/removed
- **Payment status** changes from INITIATED to PAID after ~3 seconds
- **Product inventory** decremented when order created
- **Shipment created** automatically when order created
- **Order and shipment status** synchronized during fulfillment
- **All transitions** properly logged and tracked

This sample data and testing sequence will help validate that all OMS functionality works as expected.
