# Purrfect Pets API - Entity Requirements

## Overview
This document defines the detailed requirements for all entities in the Purrfect Pets API application, based on the Petstore API specification. The application manages pets, orders, users, categories, and tags with their respective attributes and relationships.

## Entity Definitions

### 1. Pet Entity

**Description**: Represents a pet available in the store with all its characteristics and current status.

**Attributes**:
- `id` (Long): Unique identifier for the pet
- `name` (String): Name of the pet (required)
- `photoUrls` (List<String>): Array of photo URLs for the pet (required)
- `categoryId` (Long): Reference to the pet's category
- `tagIds` (List<Long>): List of tag IDs associated with the pet
- `createdDate` (LocalDateTime): When the pet was added to the system
- `lastModified` (LocalDateTime): Last modification timestamp

**Entity State**: The pet's status in the store workflow (managed by system):
- `available`: Pet is available for purchase
- `pending`: Pet is reserved/pending sale
- `sold`: Pet has been sold

**Relationships**:
- Many-to-One with Category (via categoryId)
- Many-to-Many with Tag (via tagIds)
- One-to-Many with Order (pets can be ordered)

**Business Rules**:
- Name and photoUrls are mandatory fields
- Pet must have at least one photo URL
- Pet can belong to zero or one category
- Pet can have multiple tags
- Pet status transitions are managed by the workflow system

### 2. Order Entity

**Description**: Represents a purchase order for a pet with shipping and payment details.

**Attributes**:
- `id` (Long): Unique identifier for the order
- `petId` (Long): Reference to the ordered pet (required)
- `quantity` (Integer): Number of pets ordered (default: 1)
- `shipDate` (LocalDateTime): Expected shipping date
- `complete` (Boolean): Whether the order is complete (default: false)
- `orderDate` (LocalDateTime): When the order was placed
- `customerId` (Long): Reference to the customer who placed the order

**Entity State**: The order's status in the fulfillment workflow (managed by system):
- `placed`: Order has been placed
- `approved`: Order has been approved for processing
- `delivered`: Order has been delivered

**Relationships**:
- Many-to-One with Pet (via petId)
- Many-to-One with User as customer (via customerId)

**Business Rules**:
- petId is mandatory
- quantity must be positive integer
- shipDate cannot be in the past
- Order can only be marked complete when status is 'delivered'
- Customer must be a valid user in the system

### 3. User Entity

**Description**: Represents a user of the system who can place orders and manage pets.

**Attributes**:
- `id` (Long): Unique identifier for the user
- `username` (String): Unique username for login (required)
- `firstName` (String): User's first name
- `lastName` (String): User's last name
- `email` (String): User's email address (required)
- `password` (String): Encrypted password (required)
- `phone` (String): User's phone number
- `userStatus` (Integer): User status code (0=inactive, 1=active, 2=suspended)
- `registrationDate` (LocalDateTime): When the user registered
- `lastLoginDate` (LocalDateTime): Last login timestamp

**Entity State**: The user's account status in the system (managed by system):
- `registered`: User has registered but not verified
- `active`: User account is active and verified
- `suspended`: User account is temporarily suspended
- `deactivated`: User account is permanently deactivated

**Relationships**:
- One-to-Many with Order (users can place multiple orders)

**Business Rules**:
- username must be unique across the system
- email must be unique and valid format
- password must meet security requirements (handled by processors)
- userStatus defaults to 0 (inactive) for new registrations
- Phone number format validation (if provided)

### 4. Category Entity

**Description**: Represents a category for organizing pets (e.g., Dogs, Cats, Birds).

**Attributes**:
- `id` (Long): Unique identifier for the category
- `name` (String): Category name (required)
- `description` (String): Category description
- `createdDate` (LocalDateTime): When the category was created
- `active` (Boolean): Whether the category is active (default: true)

**Entity State**: The category's lifecycle status (managed by system):
- `active`: Category is active and can be used
- `inactive`: Category is inactive but preserved for historical data

**Relationships**:
- One-to-Many with Pet (category can have multiple pets)

**Business Rules**:
- Category name must be unique
- Category name cannot be empty or null
- Inactive categories cannot be assigned to new pets
- Categories with existing pets cannot be deleted, only deactivated

### 5. Tag Entity

**Description**: Represents tags for labeling and filtering pets (e.g., "friendly", "trained", "vaccinated").

**Attributes**:
- `id` (Long): Unique identifier for the tag
- `name` (String): Tag name (required)
- `color` (String): Display color for the tag (hex code)
- `createdDate` (LocalDateTime): When the tag was created
- `active` (Boolean): Whether the tag is active (default: true)

**Entity State**: The tag's lifecycle status (managed by system):
- `active`: Tag is active and can be used
- `inactive`: Tag is inactive but preserved for historical data

**Relationships**:
- Many-to-Many with Pet (tags can be applied to multiple pets, pets can have multiple tags)

**Business Rules**:
- Tag name must be unique
- Tag name cannot be empty or null
- Color must be valid hex code format (if provided)
- Inactive tags cannot be assigned to pets
- Tags cannot be deleted if they are associated with existing pets

## Entity Relationships Summary

```
User (1) -----> (M) Order (M) -----> (1) Pet
Pet (M) -----> (1) Category
Pet (M) -----> (M) Tag
```

## Notes

- All entities include system-managed fields like `technicalId` (UUID) for internal operations
- Entity states are managed by the workflow system and cannot be directly modified via API
- All timestamp fields use LocalDateTime for consistency
- Foreign key relationships are maintained through ID references
- Soft delete pattern is used where applicable (active/inactive flags)
