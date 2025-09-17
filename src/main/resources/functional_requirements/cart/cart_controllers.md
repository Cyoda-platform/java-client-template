# Cart Controller Requirements

## CartController

### POST /ui/cart
Create new cart or return existing cart.

**Request Example:**
```
POST /ui/cart
```

**Response Example:**
```json
{
  "cartId": "cart_01J8X9Y2Z3A4B5C6D7E8F9",
  "status": "NEW",
  "lines": [],
  "totalItems": 0,
  "grandTotal": 0,
  "createdAt": "2025-09-17T10:00:00Z",
  "updatedAt": "2025-09-17T10:00:00Z"
}
```

### POST /ui/cart/{cartId}/lines
Add or increment item in cart.

**Request Example:**
```
POST /ui/cart/cart_01J8X9Y2Z3A4B5C6D7E8F9/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Response Example:**
```json
{
  "cartId": "cart_01J8X9Y2Z3A4B5C6D7E8F9",
  "status": "ACTIVE",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "price": 1299.99,
      "qty": 1
    }
  ],
  "totalItems": 1,
  "grandTotal": 1299.99,
  "updatedAt": "2025-09-17T10:05:00Z"
}
```

### PATCH /ui/cart/{cartId}/lines
Update item quantity in cart.

**Request Example:**
```
PATCH /ui/cart/cart_01J8X9Y2Z3A4B5C6D7E8F9/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 2
}
```

### POST /ui/cart/{cartId}/open-checkout
Set cart to checkout state.

**Request Example:**
```
POST /ui/cart/cart_01J8X9Y2Z3A4B5C6D7E8F9/open-checkout
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
  "grandTotal": 2599.98
}
```

### GET /ui/cart/{cartId}
Get cart details.

**Request Example:**
```
GET /ui/cart/cart_01J8X9Y2Z3A4B5C6D7E8F9
```
