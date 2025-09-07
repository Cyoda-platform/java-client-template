# Entities Specification - Purrfect Pets API

## Overview
The Purrfect Pets API manages pets, their owners, and pet care orders. This specification defines the core entities and their relationships based on typical petstore functionality.

## Entity Definitions

### 1. Pet Entity

**Entity Name:** Pet  
**Description:** Represents a pet in the system with basic information and care tracking.

**Attributes:**
- `petId` (String) - Business identifier for the pet (e.g., "PET-001")
- `name` (String) - Pet's name
- `species` (String) - Type of animal (e.g., "Dog", "Cat", "Bird")
- `breed` (String) - Specific breed of the pet
- `age` (Integer) - Age in years
- `weight` (Double) - Weight in kilograms
- `color` (String) - Primary color of the pet
- `ownerId` (String) - Reference to the owner's business ID
- `healthNotes` (String) - General health information and notes
- `photoUrl` (String) - URL to pet's photo
- `registrationDate` (LocalDateTime) - When the pet was registered
- `lastCheckupDate` (LocalDateTime) - Date of last veterinary checkup

**Relationships:**
- Belongs to one Owner (many-to-one via ownerId)
- Can have multiple PetCareOrders (one-to-many)

**Entity State Management:**
- Entity state represents the pet's current status in the system
- States managed by workflow: REGISTERED, ACTIVE, INACTIVE, ARCHIVED
- State transitions are automatic based on business rules and manual actions

---

### 2. Owner Entity

**Entity Name:** Owner  
**Description:** Represents a pet owner with contact information and preferences.

**Attributes:**
- `ownerId` (String) - Business identifier for the owner (e.g., "OWNER-001")
- `firstName` (String) - Owner's first name
- `lastName` (String) - Owner's last name
- `email` (String) - Contact email address
- `phoneNumber` (String) - Primary phone number
- `address` (String) - Home address
- `city` (String) - City of residence
- `zipCode` (String) - Postal code
- `emergencyContact` (String) - Emergency contact information
- `preferredVet` (String) - Preferred veterinarian name
- `registrationDate` (LocalDateTime) - When the owner registered
- `totalPets` (Integer) - Count of pets owned

**Relationships:**
- Can own multiple Pets (one-to-many)
- Can place multiple PetCareOrders (one-to-many)

**Entity State Management:**
- Entity state represents the owner's account status
- States managed by workflow: PENDING, ACTIVE, SUSPENDED, CLOSED
- State transitions based on account verification and activity

---

### 3. PetCareOrder Entity

**Entity Name:** PetCareOrder  
**Description:** Represents an order for pet care services (grooming, boarding, veterinary care).

**Attributes:**
- `orderId` (String) - Business identifier for the order (e.g., "ORDER-001")
- `petId` (String) - Reference to the pet's business ID
- `ownerId` (String) - Reference to the owner's business ID
- `serviceType` (String) - Type of service (e.g., "GROOMING", "BOARDING", "VETERINARY")
- `serviceDescription` (String) - Detailed description of requested service
- `scheduledDate` (LocalDateTime) - When the service is scheduled
- `duration` (Integer) - Expected duration in hours
- `specialInstructions` (String) - Special care instructions
- `cost` (Double) - Total cost of the service
- `paymentMethod` (String) - Payment method used
- `veterinarianName` (String) - Assigned veterinarian (if applicable)
- `orderDate` (LocalDateTime) - When the order was placed
- `completionDate` (LocalDateTime) - When the service was completed
- `customerRating` (Integer) - Customer satisfaction rating (1-5)
- `notes` (String) - Additional notes from service provider

**Relationships:**
- Belongs to one Pet (many-to-one via petId)
- Belongs to one Owner (many-to-one via ownerId)

**Entity State Management:**
- Entity state represents the order's progress through the service lifecycle
- States managed by workflow: PENDING, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED
- State transitions based on service scheduling, execution, and completion

## Entity Relationships Summary

```
Owner (1) -----> (N) Pet
Owner (1) -----> (N) PetCareOrder
Pet (1) -----> (N) PetCareOrder
```

## Business Rules

1. **Pet Registration**: A pet must have a valid owner before registration
2. **Order Validation**: PetCareOrders must reference existing pets and owners
3. **State Consistency**: Entity states must follow defined workflow transitions
4. **Data Integrity**: Business IDs must be unique within their entity type
5. **Service Scheduling**: Orders cannot be scheduled for inactive pets
6. **Owner Verification**: Owners must be in ACTIVE state to place orders

## Notes

- All entities use business IDs (petId, ownerId, orderId) for external API interactions
- Technical UUIDs are managed internally by the system for performance
- Entity states are managed by the workflow engine and cannot be directly modified
- Timestamps are automatically managed by the system
- All monetary values are in the system's base currency
