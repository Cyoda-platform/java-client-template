# Purrfect Pets - Entity Requirements

## Overview
This document defines the entities for the Purrfect Pets API application based on the Petstore API specification. Each entity represents a core business object with specific attributes and relationships.

**Important Note**: Entity state/status fields are managed by the workflow system through `entity.meta.state` and are NOT included in the entity schema. The system automatically manages state transitions based on workflow definitions.

## Entities

### 1. Pet Entity

**Description**: Represents a pet available in the store.

**Attributes**:
- `id` (Long) - Unique identifier for the pet (primary key)
- `name` (String, required) - Name of the pet
- `category` (Category) - Category the pet belongs to (relationship)
- `photoUrls` (List<String>, required) - List of photo URLs for the pet
- `tags` (List<Tag>) - List of tags associated with the pet (relationship)

**Relationships**:
- Many-to-One with Category (pet belongs to one category)
- Many-to-Many with Tag (pet can have multiple tags)
- One-to-Many with Order (pet can be in multiple orders)

**Business Rules**:
- Name is required and cannot be empty
- At least one photo URL is required
- Pet state is managed by workflow (available/pending/sold)

### 2. Category Entity

**Description**: Represents a category of pets (e.g., Dogs, Cats, Birds).

**Attributes**:
- `id` (Long) - Unique identifier for the category (primary key)
- `name` (String, required) - Name of the category

**Relationships**:
- One-to-Many with Pet (category can have multiple pets)

**Business Rules**:
- Category name must be unique
- Category name cannot be empty

### 3. Tag Entity

**Description**: Represents tags that can be associated with pets for classification.

**Attributes**:
- `id` (Long) - Unique identifier for the tag (primary key)
- `name` (String, required) - Name of the tag

**Relationships**:
- Many-to-Many with Pet (tag can be associated with multiple pets)

**Business Rules**:
- Tag name must be unique
- Tag name cannot be empty

### 4. Order Entity

**Description**: Represents an order placed for a pet.

**Attributes**:
- `id` (Long) - Unique identifier for the order (primary key)
- `petId` (Long, required) - ID of the pet being ordered (foreign key)
- `quantity` (Integer) - Quantity of pets ordered (default: 1)
- `shipDate` (LocalDateTime) - Expected shipping date
- `complete` (Boolean) - Whether the order is complete (default: false)

**Relationships**:
- Many-to-One with Pet (order is for one pet)
- Many-to-One with User (order is placed by one user)

**Business Rules**:
- Pet ID is required
- Quantity must be positive
- Order state is managed by workflow (placed/approved/delivered)

### 5. User Entity

**Description**: Represents a user of the pet store system.

**Attributes**:
- `id` (Long) - Unique identifier for the user (primary key)
- `username` (String, required) - Unique username
- `firstName` (String) - User's first name
- `lastName` (String) - User's last name
- `email` (String, required) - User's email address
- `password` (String, required) - User's password (encrypted)
- `phone` (String) - User's phone number

**Relationships**:
- One-to-Many with Order (user can place multiple orders)

**Business Rules**:
- Username must be unique
- Email must be unique and valid format
- Password must meet security requirements
- User state is managed by workflow (active/inactive/suspended)

### 6. Store Entity

**Description**: Represents store inventory and configuration.

**Attributes**:
- `id` (Long) - Unique identifier for the store (primary key)
- `name` (String, required) - Store name
- `address` (String) - Store address
- `phone` (String) - Store phone number
- `email` (String) - Store email address

**Relationships**:
- One-to-Many with Pet (store can have multiple pets)
- One-to-Many with Order (store can have multiple orders)

**Business Rules**:
- Store name is required
- Email must be valid format if provided

## Entity Relationships Summary

```
User (1) -----> (M) Order (M) -----> (1) Pet
Pet (M) -----> (1) Category
Pet (M) -----> (M) Tag
Store (1) -----> (M) Pet
Store (1) -----> (M) Order
```

## Data Types Mapping

| Entity Field Type | Java Type | Database Type | Notes |
|------------------|-----------|---------------|-------|
| ID | Long | BIGINT | Primary key, auto-generated |
| String | String | VARCHAR | Required fields cannot be null/empty |
| Integer | Integer | INT | Positive values where applicable |
| Boolean | Boolean | BOOLEAN | Default values specified |
| DateTime | LocalDateTime | TIMESTAMP | ISO 8601 format |
| List<String> | List<String> | JSON/TEXT | Stored as JSON array |
| Relationship | Entity Reference | FOREIGN KEY | JPA annotations |

## Validation Rules

### Common Validations
- All ID fields are auto-generated and read-only
- Required fields cannot be null or empty
- Email fields must follow valid email format
- Phone numbers should follow international format

### Entity-Specific Validations
- **Pet**: Name length 1-100 characters, at least one photo URL
- **Category**: Name length 1-50 characters, unique
- **Tag**: Name length 1-30 characters, unique
- **Order**: Quantity > 0, valid pet ID reference
- **User**: Username 3-50 characters unique, password min 8 characters
- **Store**: Name length 1-100 characters

## State Management

Entity states are managed by the workflow system and accessed via `entity.meta.state`:

- **Pet**: available → pending → sold
- **Order**: placed → approved → delivered
- **User**: active → inactive → suspended
- **Category**: active → inactive
- **Tag**: active → inactive
- **Store**: active → inactive

States are not stored as entity attributes but managed by the Cyoda workflow engine.
