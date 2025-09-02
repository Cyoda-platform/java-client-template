# Entities Requirements

## Overview
The Purrfect Pets API manages pets, categories, tags, and orders in a pet store system. Each entity has specific attributes and relationships that support the complete pet store workflow.

## Entity Definitions

### 1. Pet Entity

**Description**: Represents a pet available in the store with complete information including status, category, and tags.

**Attributes**:
- `id` (Long): Unique identifier for the pet
- `name` (String): Pet's name (required)
- `category` (Category): Pet's category (embedded object)
- `photoUrls` (List<String>): List of photo URLs for the pet (required)
- `tags` (List<Tag>): List of tags associated with the pet
- `status` (String): Pet status - NOTE: This is semantically similar to entity state and will be managed by the workflow system. Use entity.meta.state instead.

**Relationships**:
- One-to-One with Category (embedded)
- One-to-Many with Tag (embedded list)
- One-to-Many with Order (referenced by petId)

**Business Rules**:
- Pet name is required and cannot be empty
- At least one photo URL must be provided
- Status transitions are managed by the workflow system
- Category is optional but recommended

### 2. Category Entity

**Description**: Represents a category that pets can belong to, providing classification for pets.

**Attributes**:
- `id` (Long): Unique identifier for the category
- `name` (String): Category name (required)

**Relationships**:
- One-to-Many with Pet (category can have multiple pets)

**Business Rules**:
- Category name must be unique
- Category name cannot be empty
- Categories can exist without pets

### 3. Tag Entity

**Description**: Represents tags that can be associated with pets for additional classification and filtering.

**Attributes**:
- `id` (Long): Unique identifier for the tag
- `name` (String): Tag name (required)

**Relationships**:
- Many-to-Many with Pet (tags can be associated with multiple pets, pets can have multiple tags)

**Business Rules**:
- Tag name must be unique
- Tag name cannot be empty
- Tags can exist without being associated with pets

### 4. Order Entity

**Description**: Represents an order for purchasing a pet from the store.

**Attributes**:
- `id` (Long): Unique identifier for the order
- `petId` (Long): ID of the pet being ordered (required)
- `quantity` (Integer): Quantity of pets ordered (default: 1)
- `shipDate` (LocalDateTime): Expected shipping date
- `status` (String): Order status - NOTE: This is semantically similar to entity state and will be managed by the workflow system. Use entity.meta.state instead.
- `complete` (Boolean): Whether the order is complete (default: false)

**Relationships**:
- Many-to-One with Pet (multiple orders can reference the same pet)

**Business Rules**:
- Pet ID must reference an existing pet
- Quantity must be positive integer
- Ship date cannot be in the past
- Status transitions are managed by the workflow system
- Complete flag is automatically managed by workflow

## Entity State Management

**Important Note**: The `status` fields in Pet and Order entities represent the business state of these entities. However, according to the system architecture:

- These status fields will NOT be included in the entity schema
- Entity state will be managed internally using `entity.meta.state`
- State transitions will be handled automatically by the workflow system
- Processors can read the current state using `entity.meta.state` but cannot modify it directly
- State changes occur through workflow transitions only

## Data Validation Rules

### Pet Validation
- Name: Required, non-empty string, max 100 characters
- PhotoUrls: Required, at least one valid URL
- Category: Optional, but if provided must be valid
- Tags: Optional, but if provided must be valid tag references

### Category Validation
- Name: Required, unique, non-empty string, max 50 characters

### Tag Validation
- Name: Required, unique, non-empty string, max 30 characters

### Order Validation
- PetId: Required, must reference existing pet
- Quantity: Required, positive integer, max 10
- ShipDate: Optional, if provided must be future date
- Complete: Boolean, defaults to false

## Entity Relationships Summary

```
Category (1) -----> (Many) Pet
Tag (Many) <-----> (Many) Pet
Pet (1) -----> (Many) Order
```

## Technical Implementation Notes

- All entities implement the CyodaEntity interface
- Entity IDs are auto-generated Long values
- Timestamps (createdAt, updatedAt) are managed automatically by the system
- Entity versioning is handled by the framework for optimistic locking
- All entities support JSON serialization/deserialization
