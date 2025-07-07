# Workflow Architecture Refactor

## Overview

This document describes the refactoring of the workflow architecture to use proper interfaces, type-safe entity handling, and a clean factory pattern instead of reflection-based registration.

## Problems with the Original Architecture

1. **No Type Safety**: All workflow methods used `ObjectNode` directly, leading to potential runtime errors
2. **Reflection-Heavy**: The `WorkflowRegistrar` used reflection to discover and register workflow methods
3. **No Entity Abstraction**: Business logic was mixed with JSON handling
4. **Poor Maintainability**: Adding new workflows required following naming conventions and reflection patterns
5. **Jackson Errors**: Direct manipulation of `ObjectNode` led to common Jackson usage errors

## New Architecture Components

### 1. Core Interfaces

#### `WorkflowEntity` Interface
```java
public interface WorkflowEntity {
    ObjectNode toObjectNode();
    void fromObjectNode(ObjectNode objectNode);
    String getEntityType();
    boolean isValid();
}
```

#### `WorkflowHandler<T>` Interface
```java
public interface WorkflowHandler<T extends WorkflowEntity> {
    String getEntityType();
    T createEntity(Object data);
    CompletableFuture<T> processEntity(T entity, String methodName);
    String[] getAvailableMethods();
}
```

### 2. Entity Implementations

#### `PetEntity`
- Encapsulates pet data and business logic
- Provides methods like `normalizeStatus()`, `addLastModifiedTimestamp()`, `hasStatus()`
- Handles JSON conversion safely

#### `PetFetchRequestEntity`
- Encapsulates fetch request data and validation logic
- Provides methods like `validateRequest()`, `isFetchRequestValid()`
- Type-safe property access

### 3. Workflow Factory Pattern

#### `WorkflowFactory`
- **Constructor-based registration**: Automatically discovers and registers all `WorkflowHandler` beans
- **Type-safe method execution**: Provides compile-time safety for workflow processing
- **No reflection needed**: Clean bean-based discovery through Spring's dependency injection
- **Centralized workflow management**: Single point for all workflow operations

### 4. Updated Workflow Implementations

#### Pet Workflow
- Implements `WorkflowHandler<PetEntity>`
- Uses type-safe entity methods
- Maintains backward compatibility with legacy `ObjectNode` methods

#### PetFetchRequest Workflow
- Implements `WorkflowHandler<PetFetchRequestEntity>`
- Clean separation of concerns
- Proper error handling

## Benefits of the New Architecture

### 1. Type Safety
- Compile-time type checking
- No more `ObjectNode` casting errors
- Clear entity contracts

### 2. Better Maintainability
- Clear separation between data and business logic
- Easy to add new entity types
- Consistent patterns across all workflows

### 3. Improved Testing
- Easy to unit test individual components
- Mock-friendly interfaces
- Clear dependencies

### 4. Error Prevention
- Eliminates common Jackson usage errors
- Proper generic type handling
- Validation at entity level

### 5. Backward Compatibility
- Legacy `ObjectNode` methods still work
- Gradual migration path
- No breaking changes to existing workflows

## Usage Examples

### Creating and Processing Entities

```java
// Create entity from ObjectNode
PetEntity pet = new PetEntity(objectNode);

// Business logic methods
pet.normalizeStatus();
pet.addLastModifiedTimestamp();

// Validation
if (pet.isValid()) {
    // Process entity
}

// Convert back to ObjectNode
ObjectNode result = pet.toObjectNode();
```

### Using Workflow Factory

```java
// Factory automatically registers all WorkflowHandler beans in constructor
@Component
public class MyService {
    private final WorkflowFactory workflowFactory;

    public MyService(WorkflowFactory workflowFactory) {
        this.workflowFactory = workflowFactory; // Handlers already registered
    }

    public void processWorkflow() {
        // Process workflow directly
        CompletableFuture<ObjectNode> result = workflowFactory.processWorkflow(
            "pet", "normalizeStatus", objectNode);
    }
}
```

## Migration Guide

### For Existing Workflows

1. **Implement `WorkflowHandler<T>`**: Make your workflow class implement the interface
2. **Create Entity Class**: Create a corresponding entity class implementing `WorkflowEntity`
3. **Add Type-Safe Methods**: Implement workflow methods using the entity type
4. **Keep Legacy Methods**: Maintain `ObjectNode` methods for backward compatibility

### For New Workflows

1. **Create Entity Class**: Implement `WorkflowEntity` with your data structure
2. **Create Workflow Handler**: Implement `WorkflowHandler<YourEntity>` and annotate with `@Component`
3. **Automatic Registration**: Spring will inject the handler into `WorkflowFactory` constructor
4. **No Manual Registration**: The factory automatically discovers and registers all handlers

## File Structure

```
src/main/java/com/java_template/common/workflow/
├── WorkflowEntity.java                 # Base entity interface
├── WorkflowHandler.java               # Base workflow handler interface
├── WorkflowFactory.java               # Factory for workflow management (constructor-based)
├── WorkflowProcessor.java             # Updated processor using factory
├── entity/
│   ├── PetEntity.java                 # Pet entity implementation
│   └── PetFetchRequestEntity.java     # Fetch request entity implementation
└── ...

src/main/java/com/java_template/entity/
├── pet/
│   └── Workflow.java                  # Updated pet workflow
└── petfetchrequest/
    └── Workflow.java                  # Updated fetch request workflow
```

**Removed Files:**
- `WorkflowRegistrar.java` - No longer needed, replaced by constructor-based registration

## Testing

The new architecture includes comprehensive tests in `WorkflowArchitectureTest.java` that verify:
- Entity creation and conversion
- Workflow handler functionality
- Factory registration and method execution
- Business logic correctness
- Type safety

## Key Improvements in Final Architecture

### Constructor-Based Registration
- **WorkflowFactory** now uses constructor injection to automatically discover all `WorkflowHandler` beans
- **No manual registration** required - Spring handles dependency injection
- **Cleaner initialization** - everything is set up at application startup

### Simplified Components
- **Removed WorkflowRegistrar** - functionality moved to WorkflowFactory constructor
- **Updated WorkflowProcessor** - now simply delegates to WorkflowFactory
- **Single responsibility** - each component has a clear, focused purpose

### Spring Integration
```java
@Component
public class WorkflowFactory {
    // Constructor automatically receives all WorkflowHandler beans
    public WorkflowFactory(Map<String, WorkflowHandler> handlerBeans) {
        // Auto-registration happens here
    }
}
```

## Conclusion

This refactoring provides a solid foundation for workflow development with:
- **Type Safety**: Compile-time error detection
- **Clean Architecture**: Clear separation of concerns with constructor-based DI
- **Easy Maintenance**: Consistent patterns and automatic bean discovery
- **Backward Compatibility**: No breaking changes
- **Error Prevention**: Eliminates common Jackson usage mistakes
- **Spring Best Practices**: Proper dependency injection and component lifecycle

The new architecture makes it much easier to add new workflows and entities while maintaining code quality and preventing the Jackson JSON handling errors that were common in the original implementation.
