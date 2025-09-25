# Product Controller API Specifications

## ProductController

### GET /ui/products
**Purpose**: List products with filtering and pagination
**Parameters**:
- search (optional): Free-text search on name/description
- category (optional): Filter by category
- minPrice (optional): Minimum price filter
- maxPrice (optional): Maximum price filter
- page (optional): Page number (default: 0)
- pageSize (optional): Page size (default: 20)

**Request Example**:
```
GET /ui/products?search=laptop&category=electronics&minPrice=500&maxPrice=2000&page=0&pageSize=10
```

**Response Example**:
```json
{
  "content": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop",
      "description": "High-performance gaming laptop",
      "price": 1299.99,
      "quantityAvailable": 15,
      "category": "electronics",
      "imageUrl": "https://example.com/laptop.jpg"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "pageSize": 10
}
```

### GET /ui/products/{sku}
**Purpose**: Get full product details
**Parameters**: sku (path parameter)

**Request Example**:
```
GET /ui/products/LAPTOP-001
```

**Response Example**: Returns complete Product schema with all attributes, localizations, media, etc.

### PATCH /ui/products/{sku}
**Purpose**: Update product stock
**Transition**: UPDATE_STOCK

**Request Example**:
```json
{
  "quantityAvailable": 20,
  "transition": "UPDATE_STOCK"
}
```
