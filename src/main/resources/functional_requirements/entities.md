# Entities Requirements for Purrfect Pets API

## Overview
The Purrfect Pets API manages a pet store system with pets, categories, tags, and orders. Each entity has specific attributes and relationships that support the complete pet store workflow.

## Entity Definitions

### 1. Pet Entity
**Description**: Represents a pet available in the store with complete information including photos, status, and categorization.

**Attributes**:
- `id` (Long): Unique identifier for the pet
- `name` (String): Pet's name (required)
- `category` (Category): Pet's category (embedded object)
- `photoUrls` (List<String>): List of photo URLs for the pet
- `tags` (List<Tag>): List of tags associated with the pet
- `description` (String): Detailed description of the pet
- `price` (BigDecimal): Price of the pet
- `birthDate` (LocalDate): Pet's birth date
- `breed` (String): Pet's breed
- `color` (String): Pet's color
- `weight` (Double): Pet's weight in kg
- `vaccinated` (Boolean): Vaccination status
- `neutered` (Boolean): Neutering status
- `microchipped` (Boolean): Microchip status
- `specialNeeds` (String): Any special needs or medical conditions

**Entity State**: Pet workflow state (managed by system)
- Note: The user requirement doesn't specify a status field, so we use entity.meta.state for workflow management

**Relationships**:
- One-to-One with Category (embedded)
- One-to-Many with Tag (embedded list)
- Referenced by Order (via petId)

### 2. Category Entity
**Description**: Represents pet categories for classification and organization.

**Attributes**:
- `id` (Long): Unique identifier for the category
- `name` (String): Category name (e.g., "Dogs", "Cats", "Birds")
- `description` (String): Category description
- `imageUrl` (String): Category image URL
- `active` (Boolean): Whether category is active

**Entity State**: Category workflow state (managed by system)

**Relationships**:
- Referenced by Pet entities
- Can be embedded in Pet entity

### 3. Tag Entity
**Description**: Represents tags for flexible pet labeling and filtering.

**Attributes**:
- `id` (Long): Unique identifier for the tag
- `name` (String): Tag name (e.g., "friendly", "trained", "hypoallergenic")
- `color` (String): Display color for the tag
- `description` (String): Tag description
- `active` (Boolean): Whether tag is active

**Entity State**: Tag workflow state (managed by system)

**Relationships**:
- Referenced by Pet entities
- Can be embedded in Pet entity as a list

### 4. Order Entity
**Description**: Represents customer orders for pets with complete order information.

**Attributes**:
- `id` (Long): Unique identifier for the order
- `petId` (Long): Reference to the ordered pet
- `customerName` (String): Customer's full name
- `customerEmail` (String): Customer's email address
- `customerPhone` (String): Customer's phone number
- `customerAddress` (String): Customer's address
- `quantity` (Integer): Quantity ordered (typically 1 for pets)
- `orderDate` (LocalDateTime): When the order was placed
- `shipDate` (LocalDateTime): When the order was shipped
- `totalAmount` (BigDecimal): Total order amount
- `paymentMethod` (String): Payment method used
- `paymentStatus` (String): Payment status
- `shippingMethod` (String): Shipping method
- `trackingNumber` (String): Shipping tracking number
- `notes` (String): Additional order notes
- `complete` (Boolean): Whether the order is complete

**Entity State**: Order workflow state (managed by system)
- Note: The complete field represents business completion, while entity.meta.state manages workflow state

**Relationships**:
- References Pet entity via petId
- Independent entity with foreign key relationship

## Entity Relationships Summary

```
Pet (1) ←→ (1) Category [embedded]
Pet (1) ←→ (*) Tag [embedded list]
Order (*) → (1) Pet [foreign key reference]
```

## Business Rules

### Pet Entity Rules
- Pet name is required and must be non-empty
- Pet must have at least one photo URL
- Price must be positive if specified
- Birth date cannot be in the future
- Weight must be positive if specified

### Category Entity Rules
- Category name must be unique
- Active categories can be assigned to pets
- Inactive categories cannot be assigned to new pets

### Tag Entity Rules
- Tag name must be unique
- Active tags can be assigned to pets
- Inactive tags cannot be assigned to new pets

### Order Entity Rules
- Order must reference an existing pet
- Customer email must be valid format
- Quantity must be positive
- Total amount must be positive
- Order date is automatically set when created
- Ship date cannot be before order date

## Data Validation Requirements

### Pet Validation
- Name: Required, 1-100 characters
- PhotoUrls: At least one URL, valid URL format
- Price: Positive decimal, max 2 decimal places
- Email format validation for any email fields
- Phone format validation for any phone fields

### Category Validation
- Name: Required, unique, 1-50 characters
- Description: Max 500 characters

### Tag Validation
- Name: Required, unique, 1-30 characters
- Color: Valid hex color code format

### Order Validation
- Customer name: Required, 1-100 characters
- Customer email: Required, valid email format
- Customer phone: Valid phone format
- Quantity: Required, positive integer
- Total amount: Required, positive decimal

## Integration Notes
- All entities support full CRUD operations
- Entities are designed to work with the Cyoda workflow system
- Entity states are managed automatically by workflow transitions
- All entities include technical metadata (id, createdAt, updatedAt, version)
