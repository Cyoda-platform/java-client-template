# Purrfect Pets - Entity Requirements

## Overview
This document defines the entities for the Purrfect Pets API application based on the standard Petstore API specification. Each entity represents a core business object with specific attributes and relationships.

**Important Note**: Entity state/status fields are managed by the workflow system through `entity.meta.state` and are NOT included in the entity schema. The system automatically manages state transitions based on workflow definitions.

## Entities

### 1. Pet Entity

**Description**: Represents a pet available in the store with all its characteristics and metadata.

**Attributes**:
- `id` (Long) - Unique identifier for the pet (business ID)
- `name` (String, required) - Name of the pet
- `category` (Category) - Category the pet belongs to (relationship)
- `photoUrls` (List<String>, required) - Array of photo URLs for the pet
- `tags` (List<Tag>) - Array of tags associated with the pet (relationship)

**Relationships**:
- Many-to-One with Category (pet belongs to one category)
- Many-to-Many with Tag (pet can have multiple tags)
- One-to-Many with Order (pet can be in multiple orders)

**Business Rules**:
- Name and photoUrls are required fields
- Pet ID serves as the business identifier
- State is managed by workflow (available/pending/sold)

### 2. Category Entity

**Description**: Represents a category that pets can belong to (e.g., Dogs, Cats, Birds).

**Attributes**:
- `id` (Long) - Unique identifier for the category (business ID)
- `name` (String, required) - Name of the category

**Relationships**:
- One-to-Many with Pet (category can have multiple pets)

**Business Rules**:
- Category name must be unique
- Category ID serves as the business identifier

### 3. Tag Entity

**Description**: Represents tags that can be associated with pets for categorization and filtering.

**Attributes**:
- `id` (Long) - Unique identifier for the tag (business ID)
- `name` (String, required) - Name of the tag

**Relationships**:
- Many-to-Many with Pet (tag can be associated with multiple pets)

**Business Rules**:
- Tag name should be unique
- Tag ID serves as the business identifier

### 4. Order Entity

**Description**: Represents a purchase order for a pet placed by a user.

**Attributes**:
- `id` (Long) - Unique identifier for the order (business ID)
- `petId` (Long, required) - ID of the pet being ordered
- `userId` (Long, required) - ID of the user placing the order
- `quantity` (Integer, required) - Quantity of pets ordered (default: 1)
- `shipDate` (LocalDateTime) - Expected shipping date
- `complete` (Boolean) - Whether the order is complete (default: false)

**Relationships**:
- Many-to-One with Pet (order is for one pet)
- Many-to-One with User (order is placed by one user)

**Business Rules**:
- Order ID serves as the business identifier
- State is managed by workflow (placed/approved/delivered)
- Quantity must be positive
- Pet and User must exist when creating an order

### 5. User Entity

**Description**: Represents a user of the pet store system who can place orders.

**Attributes**:
- `id` (Long) - Unique identifier for the user (business ID)
- `username` (String, required) - Unique username for login
- `firstName` (String) - User's first name
- `lastName` (String) - User's last name
- `email` (String, required) - User's email address
- `password` (String, required) - User's password (encrypted)
- `phone` (String) - User's phone number
- `userStatus` (Integer) - User status code (default: 1 for active)

**Relationships**:
- One-to-Many with Order (user can place multiple orders)

**Business Rules**:
- Username and email must be unique
- User ID serves as the business identifier
- Password should be encrypted before storage
- Email format must be valid

### 6. Store Entity

**Description**: Represents store inventory and operational data.

**Attributes**:
- `id` (Long) - Unique identifier for the store (business ID)
- `name` (String, required) - Name of the store
- `address` (String) - Store address
- `phone` (String) - Store contact phone
- `email` (String) - Store contact email
- `operatingHours` (String) - Store operating hours

**Relationships**:
- One-to-Many with Pet (store can have multiple pets)
- One-to-Many with Order (store processes multiple orders)

**Business Rules**:
- Store name must be unique
- Store ID serves as the business identifier

## Entity Relationships Summary

```
User (1) -----> (M) Order (M) -----> (1) Pet
Pet (M) -----> (1) Category
Pet (M) -----> (M) Tag
Store (1) -----> (M) Pet
Store (1) -----> (M) Order
```

## Data Types Mapping

- `Long` - 64-bit integer for IDs
- `String` - Text fields
- `Integer` - 32-bit integer for quantities and status codes
- `Boolean` - True/false values
- `LocalDateTime` - Date and time fields
- `List<T>` - Collections of related entities

## Validation Rules

1. **Required Fields**: All fields marked as required must be provided
2. **Unique Constraints**: Username, email, category names should be unique
3. **Foreign Key Constraints**: Referenced entities must exist
4. **Data Format**: Email addresses must be valid format
5. **Positive Values**: Quantities and IDs must be positive numbers

## State Management

Entity states are managed by the workflow system:
- **Pet**: available → pending → sold
- **Order**: placed → approved → delivered
- **User**: active → inactive → suspended
- **Category**: active → inactive
- **Tag**: active → inactive
- **Store**: open → closed → maintenance

States are accessed via `entity.meta.state` and cannot be directly modified in the entity schema.
