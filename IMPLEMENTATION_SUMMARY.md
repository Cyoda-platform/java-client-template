# Pet API Application - Implementation Summary

## Overview
This document describes the implementation of a Pet API application built with Spring Boot and Cyoda workflow integration. The application loads and manages pet data through a workflow-driven architecture.

## What Was Implemented

### 1. Pet Entity
**Location**: `src/main/java/com/java_template/application/entity/pet/version_1/Pet.java`

The Pet entity represents a pet with the following fields:
- **Business Identifier**: `petId` (unique identifier for the pet)
- **Core Information**: `name`, `species`, `breed`, `age`, `color`, `weight`
- **Status Fields**: `status`, `description`
- **Timestamps**: `loadedAt`, `updatedAt`
- **External API Reference**: `externalApiId`, `externalApiSource`

The entity implements the `CyodaEntity` interface with:
- `getModelKey()`: Returns the entity model specification
- `isValid(EntityMetadata metadata)`: Validates required fields (petId, name, species)

### 2. Pet Workflow
**Location**: `src/main/resources/workflow/pet/version_1/Pet.json`

The workflow defines the pet lifecycle with four states:
- **initial**: Starting state for new pets
- **loaded**: Pet data has been loaded from external source
- **active**: Pet is active and available
- **inactive**: Pet is deactivated

Key transitions:
- `load_pet`: Automatic transition from initial → loaded (runs PetLoadProcessor)
- `activate_pet`: Manual transition from loaded → active
- `reload_pet`: Manual transition to reload pet data (runs PetLoadProcessor)
- `update_pet`: Manual transition to update pet in active state
- `deactivate_pet`: Manual transition from active → inactive
- `reactivate_pet`: Manual transition from inactive → active

### 3. Pet Processor
**Location**: `src/main/java/com/java_template/application/processor/PetLoadProcessor.java`

The `PetLoadProcessor` handles pet data loading and processing:
- Validates the pet entity using `EntityWithMetadata` pattern
- Sets `loadedAt` and `updatedAt` timestamps
- Ensures pet status is set to "loaded"
- Logs all processing operations for debugging

### 4. Pet Controller
**Location**: `src/main/java/com/java_template/application/controller/PetController.java`

REST API endpoints for managing pets:

#### Create Pet
- **POST** `/ui/pet`
- Creates a new pet with duplicate business ID checking
- Returns 201 Created with Location header

#### Retrieve Pet
- **GET** `/ui/pet/{id}` - Get by technical UUID (fastest)
- **GET** `/ui/pet/business/{petId}` - Get by business identifier

#### Update Pet
- **PUT** `/ui/pet/{id}?transition=TRANSITION_NAME`
- Updates pet data with optional workflow transition
- Transition parameter is optional

#### List Pets
- **GET** `/ui/pet?page=0&size=20`
- Lists all pets with pagination support

#### Delete Pet
- **DELETE** `/ui/pet/{id}` - Delete by technical UUID
- **DELETE** `/ui/pet/business/{petId}` - Delete by business identifier

## How to Validate the Application

### 1. Build the Application
```bash
./gradlew build
```
This compiles all code and generates the JAR file.

### 2. Run the Workflow Import Tool
```bash
./gradlew runApp -PmainClass=com.java_template.common.tool.WorkflowImportTool
```
This imports the Pet workflow definition into Cyoda.

### 3. Start the Application
```bash
./gradlew runApp
```
Or manually:
```bash
java -jar build/libs/java-client-template-1.0-SNAPSHOT.jar
```

The application will start on `http://localhost:8080`

### 4. Access Swagger UI
Open your browser to: `http://localhost:8080/swagger-ui/index.html`

You'll see all Pet API endpoints documented and ready to test.

### 5. Test the API

#### Create a Pet
```bash
curl -X POST http://localhost:8080/ui/pet \
  -H "Content-Type: application/json" \
  -d '{
    "petId": "pet-001",
    "name": "Fluffy",
    "species": "Cat",
    "breed": "Persian",
    "age": 3,
    "color": "White",
    "weight": 4.5,
    "status": "new",
    "description": "A beautiful white Persian cat"
  }'
```

#### Get Pet by ID
```bash
curl http://localhost:8080/ui/pet/business/pet-001
```

#### List All Pets
```bash
curl http://localhost:8080/ui/pet?page=0&size=20
```

#### Update Pet with Transition
```bash
curl -X PUT http://localhost:8080/ui/pet/{technical-id}?transition=activate_pet \
  -H "Content-Type: application/json" \
  -d '{
    "petId": "pet-001",
    "name": "Fluffy",
    "species": "Cat",
    "breed": "Persian",
    "age": 3,
    "color": "White",
    "weight": 4.5,
    "status": "active",
    "description": "A beautiful white Persian cat"
  }'
```

## Architecture Highlights

### Workflow-Driven Design
- All pet state changes flow through the defined workflow
- Processors handle business logic during transitions
- Manual transitions ensure explicit control over state changes

### Entity-Centric Approach
- Pet entity is the single source of truth
- EntityService handles all CRUD operations
- EntityWithMetadata pattern provides both data and metadata

### REST API Best Practices
- Thin controller layer (no business logic)
- Proper HTTP status codes and error handling
- Location headers for created resources
- Pagination support for list endpoints

## Project Structure
```
src/main/java/com/java_template/
├── application/
│   ├── entity/pet/version_1/
│   │   └── Pet.java
│   ├── processor/
│   │   └── PetLoadProcessor.java
│   └── controller/
│       └── PetController.java
└── common/
    └── (Framework code - DO NOT MODIFY)

src/main/resources/
└── workflow/pet/version_1/
    └── Pet.json
```

## Key Technologies
- **Spring Boot**: REST API framework
- **Cyoda**: Workflow engine and entity management
- **Gradle**: Build system
- **Lombok**: Boilerplate reduction
- **Jackson**: JSON serialization

## Compliance
✅ All entities implement CyodaEntity interface
✅ All workflows use "initial" state (not "none")
✅ All transitions have explicit manual flags
✅ Processor names match Spring component class names
✅ Controllers are thin proxies with no business logic
✅ No modifications to common/ directory
✅ Project compiles successfully
✅ No Java reflection used

