# Product Controller Requirements

## ProductController

### GET /ui/products
Search and filter products with pagination.

**Query Parameters:**
- search (optional): Free-text search on name/description
- category (optional): Filter by category
- minPrice (optional): Minimum price filter
- maxPrice (optional): Maximum price filter
- page (optional, default=0): Page number
- pageSize (optional, default=20): Page size

**Request Example:**
```
GET /ui/products?search=laptop&category=electronics&minPrice=500&maxPrice=2000&page=0&pageSize=10
```

**Response Example:**
```json
{
  "content": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "description": "High-performance gaming laptop",
      "price": 1299.99,
      "quantityAvailable": 15,
      "category": "electronics",
      "imageUrl": "https://example.com/laptop.jpg"
    }
  ],
  "page": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

### GET /ui/products/{sku}
Get full product details by SKU.

**Request Example:**
```
GET /ui/products/LAPTOP-001
```

**Response Example:**
```json
{
  "sku": "LAPTOP-001",
  "name": "Gaming Laptop Pro",
  "description": "High-performance gaming laptop",
  "price": 1299.99,
  "quantityAvailable": 15,
  "category": "electronics",
  "warehouseId": "WH-001",
  "attributes": {
    "brand": "TechCorp",
    "model": "GP-2024",
    "dimensions": { "l": 35, "w": 25, "h": 2.5, "unit": "cm" },
    "weight": { "value": 2.1, "unit": "kg" }
  },
  "localizations": {
    "defaultLocale": "en-GB",
    "content": [
      {
        "locale": "en-GB",
        "name": "Gaming Laptop Pro",
        "description": "High-performance gaming laptop"
      }
    ]
  },
  "media": [
    {
      "type": "image",
      "url": "https://example.com/laptop.jpg",
      "alt": "Gaming Laptop Pro",
      "tags": ["hero"]
    }
  ]
}
```
