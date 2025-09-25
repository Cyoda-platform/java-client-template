# Cart Controller API Specifications

## CartController

### POST /ui/cart
**Purpose**: Create new cart
**Transition**: CREATE_CART (automatic)

**Request Example**:
```json
{}
```

**Response Example**:
```json
{
  "cartId": "cart-123",
  "status": "NEW",
  "lines": [],
  "totalItems": 0,
  "grandTotal": 0.0,
  "createdAt": "2025-09-25T10:00:00Z"
}
```

### POST /ui/cart/{cartId}/lines
**Purpose**: Add item to cart
**Transition**: ADD_FIRST_ITEM or MODIFY_CART

**Request Example**:
```json
{
  "sku": "LAPTOP-001",
  "qty": 2,
  "transition": "ADD_FIRST_ITEM"
}
```

**Response Example**:
```json
{
  "cartId": "cart-123",
  "status": "ACTIVE",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop",
      "price": 1299.99,
      "qty": 2
    }
  ],
  "totalItems": 2,
  "grandTotal": 2599.98
}
```

### PATCH /ui/cart/{cartId}/lines
**Purpose**: Update cart line quantities
**Transition**: MODIFY_CART

**Request Example**:
```json
{
  "sku": "LAPTOP-001",
  "qty": 1,
  "transition": "MODIFY_CART"
}
```

### POST /ui/cart/{cartId}/open-checkout
**Purpose**: Start checkout process
**Transition**: START_CHECKOUT

**Request Example**:
```json
{
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com",
    "address": {
      "line1": "123 Main St",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "transition": "START_CHECKOUT"
}
```

### GET /ui/cart/{cartId}
**Purpose**: Get cart details
