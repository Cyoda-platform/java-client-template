# Checkout Controller Requirements

## CheckoutController

### POST /ui/checkout/{cartId}
Attach guest contact information to cart for anonymous checkout.

**Request Example:**
```
POST /ui/checkout/cart_01J8X9Y2Z3A4B5C6D7E8F9
Content-Type: application/json

{
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+1234567890",
    "address": {
      "line1": "123 Main St",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  }
}
```

**Response Example:**
```json
{
  "cartId": "cart_01J8X9Y2Z3A4B5C6D7E8F9",
  "status": "CHECKING_OUT",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "price": 1299.99,
      "qty": 2
    }
  ],
  "totalItems": 2,
  "grandTotal": 2599.98,
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+1234567890",
    "address": {
      "line1": "123 Main St",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "updatedAt": "2025-09-17T10:08:00Z"
}
```
