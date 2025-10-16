# Pet Entity Functional Requirements

## Overview
The Pet entity represents animals available for adoption or purchase in the pet store. Each pet has basic information, categorization, photos, tags, and availability status.

## Entity Structure

### Required Fields
- **petId** (String) - Business identifier for the pet (e.g., "PET001", "DOG123")
- **name** (String) - Pet's name (e.g., "Buddy", "Fluffy")
- **photoUrls** (List<String>) - URLs to pet photos (at least one required)

### Optional Fields
- **category** (PetCategory) - Pet category information
  - **categoryId** (String) - Category business identifier
  - **categoryName** (String) - Category name (e.g., "Dogs", "Cats", "Birds")
- **tags** (List<PetTag>) - Tags for classification and search
  - **tagId** (String) - Tag business identifier  
  - **tagName** (String) - Tag name (e.g., "friendly", "trained", "young")
- **description** (String) - Detailed description of the pet
- **breed** (String) - Pet breed information
- **age** (Integer) - Pet age in months
- **price** (Double) - Pet price
- **weight** (Double) - Pet weight in kg
- **color** (String) - Pet color
- **gender** (String) - Pet gender ("male", "female", "unknown")
- **vaccinated** (Boolean) - Vaccination status
- **neutered** (Boolean) - Neutering status
- **createdAt** (LocalDateTime) - Creation timestamp
- **updatedAt** (LocalDateTime) - Last update timestamp

## Business Rules

### Validation Rules
1. Pet ID must be unique across all pets
2. Pet name is required and cannot be empty
3. At least one photo URL must be provided
4. Price must be positive if specified
5. Age must be positive if specified
6. Weight must be positive if specified
7. Gender must be one of: "male", "female", "unknown"

### Status Management
Pet status is managed through workflow states:
- **available** - Pet is available for adoption/purchase
- **pending** - Pet adoption/purchase is in progress
- **sold** - Pet has been adopted/purchased

## Entity Relationships
- Pet can be referenced by Orders (petId field)
- Pet belongs to a Category (optional)
- Pet can have multiple Tags (optional)
