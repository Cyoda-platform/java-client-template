# Metadata API Guide

This guide explains how to use the enhanced EntityService API that returns both business data and Cyoda metadata.

## Overview

The EntityService now provides two sets of methods:

1. **Simple Methods** (backward compatible) - return only business entities
2. **Enhanced Methods** - return both business data and Cyoda metadata wrapped in response objects

## Design Patterns Used

### 1. Wrapper/Envelope Pattern
- `EntityResponse<T>` - wraps a single entity with its metadata
- `EntityListResponse<T>` - wraps a collection of entities with their metadata

### 2. Builder Pattern
- `EntityResponse.builder()` - for easy construction of response objects

### 3. Factory Pattern
- `EntityResponse.fromDataPayload()` - creates response from Cyoda DataPayload
- `EntityListResponse.fromDataPayloads()` - creates list response from multiple DataPayloads

## API Methods

### Simple Methods (Backward Compatible)
```java
// Returns only the business entity (synchronous)
Product create(Product product)
Product findById(Class<Product> entityClass, String businessId)
List<Product> findAll(Class<Product> entityClass)
Product update(Product product)
boolean delete(Class<Product> entityClass, String businessId)
List<Product> findByField(Class<Product> entityClass, String fieldName, String value)
```

### Enhanced Methods (With Metadata)
```java
// Returns EntityResponse<T> with both data and metadata (synchronous)
EntityResponse<Product> createWithMetadata(Product product)
EntityResponse<Product> findByIdWithMetadata(Class<Product> entityClass, String businessId)
EntityListResponse<Product> findAllWithMetadata(Class<Product> entityClass)
EntityResponse<Product> updateWithMetadata(Product product)
EntityListResponse<Product> findByFieldWithMetadata(Class<Product> entityClass, String fieldName, String value)
```

## Response Objects

### EntityResponse<T>
Contains both business data and Cyoda metadata:

```java
public class EntityResponse<T> {
    // Access business data
    T getData()
    
    // Access full metadata
    EntityMetadata getMetadata()
    
    // Convenience methods for common metadata fields
    UUID getId()                    // Technical entity ID
    ModelSpec getModelKey()         // Model specification
    String getState()              // Entity state
    Date getCreationDate()         // When entity was created
    String getTransitionForLatestSave()  // Latest transition
}
```

### EntityListResponse<T>
Contains a collection of entities with metadata:

```java
public class EntityListResponse<T> {
    List<EntityResponse<T>> getItems()  // List of wrapped entities
    int getTotalCount()                  // Total number of items
    List<T> getData()                   // Just business data (for backward compatibility)
}
```

## Controller Examples

### Simple Endpoints (Backward Compatible)
```java
@PostMapping
public ResponseEntity<Product> createProduct(@RequestBody Product product) {
    try {
        Product createdProduct = entityService.create(product);
        return ResponseEntity.ok(createdProduct);
    } catch (Exception e) {
        logger.error("Failed to create product", e);
        return ResponseEntity.internalServerError().build();
    }
}

@GetMapping("/{sku}")
public ResponseEntity<Product> getProduct(@PathVariable String sku) {
    try {
        Product product = entityService.findById(Product.class, sku);
        return product != null ?
            ResponseEntity.ok(product) :
            ResponseEntity.notFound().build();
    } catch (Exception e) {
        logger.error("Failed to retrieve product", e);
        return ResponseEntity.internalServerError().build();
    }
}
```

### Enhanced Endpoints (With Metadata)
```java
@PostMapping("/with-metadata")
public ResponseEntity<EntityResponse<Product>> createProductWithMetadata(@RequestBody Product product) {
    try {
        EntityResponse<Product> entityResponse = entityService.createWithMetadata(product);
        return ResponseEntity.ok(entityResponse);
    } catch (Exception e) {
        logger.error("Failed to create product", e);
        return ResponseEntity.internalServerError().build();
    }
}

@GetMapping("/{sku}/with-metadata")
public ResponseEntity<EntityResponse<Product>> getProductWithMetadata(@PathVariable String sku) {
    try {
        EntityResponse<Product> entityResponse = entityService.findByIdWithMetadata(Product.class, sku);
        return entityResponse != null ?
            ResponseEntity.ok(entityResponse) :
            ResponseEntity.notFound().build();
    } catch (Exception e) {
        logger.error("Failed to retrieve product", e);
        return ResponseEntity.internalServerError().build();
    }
}
```

## JSON Response Examples

### Simple Response
```json
{
  "sku": "LAPTOP-001",
  "name": "Gaming Laptop",
  "price": 1299.99,
  "category": "Electronics"
}
```

### Enhanced Response with Metadata
```json
{
  "data": {
    "sku": "LAPTOP-001",
    "name": "Gaming Laptop",
    "price": 1299.99,
    "category": "Electronics"
  },
  "meta": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "modelKey": {
      "name": "Product",
      "version": 1
    },
    "state": "ACTIVE",
    "creationDate": "2024-01-15T10:30:00Z",
    "transitionForLatestSave": "CREATE"
  }
}
```

### List Response with Metadata
```json
{
  "items": [
    {
      "data": { "sku": "LAPTOP-001", "name": "Gaming Laptop" },
      "meta": { "id": "550e8400-e29b-41d4-a716-446655440000", "state": "ACTIVE" }
    }
  ],
  "totalCount": 1
}
```

## Usage Recommendations

1. **Use Simple Methods** when you only need business data and want backward compatibility
2. **Use Enhanced Methods** when you need:
   - Technical entity IDs for updates/deletes
   - Entity state information
   - Creation/modification timestamps
   - Model versioning information
   - Workflow transition details

3. **Client-side Usage**:
   ```javascript
   // Access business data
   const product = response.data;
   
   // Access metadata
   const entityId = response.meta.id;
   const state = response.meta.state;
   const createdAt = response.meta.creationDate;
   
   // Or use convenience methods (if using Java client)
   const entityId = response.getId();
   const state = response.getState();
   ```

## Benefits

- **Clean Separation**: Business logic vs technical metadata
- **Backward Compatibility**: Existing code continues to work
- **Type Safety**: Strong typing for both data and metadata
- **Flexibility**: Choose the right level of detail for each use case
- **Performance**: Only fetch metadata when needed
