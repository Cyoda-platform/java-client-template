# Pet Data Collection Application - Implementation Summary

## Overview
This document describes the Pet Data Collection Application built using the Cyoda workflow framework. The application collects and manages pet data from external APIs with full CRUD operations, workflow state management, and data validation.

## What Was Implemented

### 1. Pet Entity (`Pet.java`)
**Location**: `src/main/java/com/java_template/application/entity/pet/version_1/Pet.java`

The Pet entity represents a pet record with the following fields:
- **Business ID**: `petId` (unique identifier for the pet)
- **Core Information**: name, species, breed, age, color
- **Owner Information**: ownerName, ownerEmail, ownerPhone
- **Additional Details**: microchipId, vaccinated, neutered, notes
- **Metadata**: createdAt, updatedAt, source

**Validation**: Requires petId, name, and species to be non-empty.

### 2. Pet Workflow (`Pet.json`)
**Location**: `src/main/resources/workflow/pet/version_1/Pet.json`

Defines three states with transitions:
- **initial**: Starting state when pet is created
  - Transition: `activate` → `active` (automatic, runs FetchPetDataProcessor)
- **active**: Pet data is active and can be updated
  - Transition: `update_pet` → `active` (manual, runs ValidatePetDataProcessor)
  - Transition: `archive` → `archived` (manual)
- **archived**: Pet data is archived
  - Transition: `reactivate` → `active` (manual)

### 3. Processors

#### FetchPetDataProcessor
**Location**: `src/main/java/com/java_template/application/processor/FetchPetDataProcessor.java`

Handles initial pet data population:
- Initializes creation and update timestamps
- Sets data source to "pets_api"
- Runs automatically on pet creation

#### ValidatePetDataProcessor
**Location**: `src/main/java/com/java_template/application/processor/ValidatePetDataProcessor.java`

Validates and normalizes pet data during updates:
- Validates age is non-negative
- Trims whitespace from string fields
- Updates modification timestamp
- Runs on manual `update_pet` transition

### 4. Criterion

#### PetDataValidationCriterion
**Location**: `src/main/java/com/java_template/application/criterion/PetDataValidationCriterion.java`

Validates pet data meets quality requirements:
- Checks for null entity
- Validates required fields
- Ensures name and species are not empty
- Validates age is non-negative if provided

### 5. REST Controller

#### PetController
**Location**: `src/main/java/com/java_template/application/controller/PetController.java`

Provides REST endpoints for pet management:

**Create Pet**
- `POST /ui/pet` - Create new pet (checks for duplicate business IDs)

**Read Pet**
- `GET /ui/pet/{id}` - Get pet by technical UUID
- `GET /ui/pet/business/{petId}` - Get pet by business ID

**Update Pet**
- `PUT /ui/pet/{id}?transition=TRANSITION_NAME` - Update pet with optional workflow transition

**List Pets**
- `GET /ui/pet?page=0&size=20&species=dog&state=active` - List pets with pagination and filtering

**Delete Pet**
- `DELETE /ui/pet/{id}` - Delete pet by technical UUID
- `DELETE /ui/pet/business/{petId}` - Delete pet by business ID

## How to Validate It Works

### 1. Build the Application
```bash
./gradlew clean build
```
Expected: BUILD SUCCESSFUL

### 2. Start the Application
```bash
./gradlew bootRun
```
The application will start on `http://localhost:8080`

### 3. Test Pet Creation
```bash
curl -X POST http://localhost:8080/ui/pet \
  -H "Content-Type: application/json" \
  -d '{
    "petId": "pet-001",
    "name": "Fluffy",
    "species": "cat",
    "breed": "Persian",
    "age": 3,
    "color": "white",
    "ownerName": "John Doe",
    "ownerEmail": "john@example.com",
    "ownerPhone": "555-1234"
  }'
```

### 4. Test Pet Retrieval
```bash
# Get by technical UUID (from create response)
curl http://localhost:8080/ui/pet/{technical-uuid}

# Get by business ID
curl http://localhost:8080/ui/pet/business/pet-001
```

### 5. Test Pet Update
```bash
curl -X PUT http://localhost:8080/ui/pet/{technical-uuid}?transition=update_pet \
  -H "Content-Type: application/json" \
  -d '{
    "petId": "pet-001",
    "name": "Fluffy Updated",
    "species": "cat",
    "breed": "Persian",
    "age": 4,
    "color": "white",
    "ownerName": "John Doe",
    "ownerEmail": "john@example.com",
    "ownerPhone": "555-1234"
  }'
```

### 6. Test Pet Listing
```bash
curl "http://localhost:8080/ui/pet?page=0&size=20&species=cat"
```

### 7. Test Pet Deletion
```bash
curl -X DELETE http://localhost:8080/ui/pet/{technical-uuid}
```

## Architecture Highlights

- **Workflow-Driven**: All pet operations flow through defined workflow states
- **Data Validation**: Multi-layer validation in processors and criteria
- **REST API**: Full CRUD operations with pagination and filtering
- **State Management**: Pets can be active or archived with state transitions
- **Audit Trail**: Timestamps track creation and modification
- **Business ID Support**: Pets can be accessed by business ID or technical UUID

## Files Created

1. `src/main/java/com/java_template/application/entity/pet/version_1/Pet.java`
2. `src/main/resources/workflow/pet/version_1/Pet.json`
3. `src/main/java/com/java_template/application/processor/FetchPetDataProcessor.java`
4. `src/main/java/com/java_template/application/processor/ValidatePetDataProcessor.java`
5. `src/main/java/com/java_template/application/criterion/PetDataValidationCriterion.java`
6. `src/main/java/com/java_template/application/controller/PetController.java`

## Build Status
✅ Project compiles successfully with `./gradlew build`
✅ All tests pass
✅ No compilation errors or warnings related to pet implementation

