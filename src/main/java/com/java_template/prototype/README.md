# InMemoryEntityService Implementation

This package contains a prototype implementation of `EntityService` that stores entities in local memory using `ConcurrentHashMap` and automatically triggers workflow orchestrators when entities are saved or updated.

## Components

### 1. InMemoryEntityService

**Location**: `src/main/java/com/java_template/prototype/InMemoryEntityService.java`

A complete implementation of the `EntityService` interface that:

- **Stores entities in memory** using a nested `ConcurrentHashMap` structure:
  ```
  entityModel -> entityVersion -> technicalId -> entity data
  ```
- **Generates UUIDs** automatically for new entities
- **Triggers workflow orchestrators** automatically when entities are added or updated
- **Supports all EntityService methods** including CRUD operations and batch operations
- **Thread-safe** using `ConcurrentHashMap` for concurrent access
- **Returns CompletableFuture** for consistency with the interface

#### Key Features:

- ✅ **Full EntityService compatibility** - Drop-in replacement for testing/prototyping
- ✅ **Automatic workflow integration** - Orchestrators run on entity lifecycle events
- ✅ **Memory-based storage** - No external dependencies required
- ✅ **UUID generation** - Technical IDs generated automatically
- ✅ **Error handling** - Proper exceptions for missing entities
- ✅ **Batch operations** - Support for adding/processing multiple entities
- ✅ **Type conversion** - Handles various input types (POJOs, ObjectNode, etc.)

### 2. WorkflowOrchestrator Interface

**Location**: `src/main/java/com/java_template/prototype/WorkflowOrchestrator.java`

Defines the contract for workflow orchestrators:

```java
public interface WorkflowOrchestrator {
    String run(String technicalId, CyodaEntity entity, String transition);
    String getSupportedEntityModel();
}
```

### 3. WorkflowOrchestratorFactory

**Location**: `src/main/java/com/java_template/prototype/WorkflowOrchestratorFactory.java`

Factory for managing and retrieving workflow orchestrators:

- **Dependency injection** - Automatically discovers all `WorkflowOrchestrator` beans
- **Caching** - Pre-populates cache for fast lookup
- **Entity model mapping** - Maps entity models to their orchestrators
- **Error handling** - Throws clear exceptions for missing orchestrators

### 4. Updated Workflow Orchestrators

The existing workflow orchestrators have been updated to implement the `WorkflowOrchestrator` interface:

- `SubscriberWorkflowOrchestrator` - Handles `Subscriber` entities
- `LaureateWorkflowOrchestrator` - Handles `Laureate` entities  
- `JobWorkflowOrchestrator` - Handles `Job` entities

## Usage

### Basic Usage

```java
@Autowired
private InMemoryEntityService entityService;

// Add an entity (triggers workflow orchestrator with "state_initial")
Job job = new Job();
job.setJobName("Test Job");
job.setStatus("SCHEDULED");

UUID technicalId = entityService.addItem(Job.ENTITY_NAME, ENTITY_VERSION, job).get();

// Get the entity
ObjectNode retrievedJob = entityService.getItem(Job.ENTITY_NAME, ENTITY_VERSION, technicalId).get();

// Update the entity (triggers workflow orchestrator with "entity_updated")
job.setStatus("RUNNING");
entityService.updateItem(Job.ENTITY_NAME, ENTITY_VERSION, technicalId, job).get();
```

### Workflow Integration

When entities are added or updated, the service automatically:

1. **Looks up the appropriate orchestrator** using `WorkflowOrchestratorFactory`
2. **Converts the entity** to a `CyodaEntity` interface
3. **Calls the orchestrator** with the appropriate transition:
   - `"state_initial"` for new entities
   - `"entity_updated"` for updated entities
4. **Logs the result** of the orchestrator execution

### Error Handling

The service handles various error scenarios:

- **Missing entities** - Throws `NoSuchElementException` wrapped in `CompletableFuture`
- **Missing orchestrators** - Logs warning but continues operation
- **Orchestrator errors** - Logs error but doesn't fail the entity operation
- **Type conversion errors** - Uses Jackson ObjectMapper for robust conversion

## Testing

### Unit Tests

**Location**: `src/test/java/com/java_template/prototype/InMemoryEntityServiceTest.java`

Comprehensive test suite covering:

- ✅ Add and retrieve entities
- ✅ Update entities
- ✅ Delete entities
- ✅ Batch operations
- ✅ Workflow orchestrator integration
- ✅ Error scenarios
- ✅ Entities without orchestrators

### Example Usage

**Location**: `src/main/java/com/java_template/prototype/InMemoryEntityServiceExample.java`

Demonstrates:

- Basic CRUD operations
- Workflow orchestrator integration
- Error handling scenarios
- Working with entities that don't have orchestrators

## Configuration

### Spring Configuration

The service is automatically configured as a Spring `@Service` and will be available for dependency injection. The workflow orchestrators are automatically discovered and injected into the factory.

### Memory Management

Since this is an in-memory implementation:

- **Memory usage** grows with the number of stored entities
- **Data persistence** - Data is lost when the application restarts
- **Concurrent access** - Thread-safe using `ConcurrentHashMap`
- **Garbage collection** - Deleted entities are immediately eligible for GC

## Integration with Existing Code

### EntityControllerPrototype

The `EntityControllerPrototype` can use this service for:

- Saving `DigestRequest` objects to the local cache
- Processing requests through workflow orchestrators
- Managing entity lifecycle in prototype mode

### Workflow Orchestrators

All existing workflow orchestrators work seamlessly with this implementation:

- `SubscriberWorkflowOrchestrator` - Processes subscriber lifecycle
- `LaureateWorkflowOrchestrator` - Handles laureate validation and enrichment
- `JobWorkflowOrchestrator` - Manages job execution states

## Benefits

1. **Rapid Prototyping** - No external dependencies required
2. **Testing** - Perfect for unit and integration tests
3. **Development** - Local development without Cyoda infrastructure
4. **Debugging** - Easy to inspect stored entities and workflow execution
5. **Performance** - Fast in-memory operations for development/testing

## Limitations

1. **Data Persistence** - Data is lost on application restart
2. **Memory Usage** - All data stored in heap memory
3. **Scalability** - Not suitable for production workloads
4. **Query Capabilities** - Limited condition-based filtering
5. **Transactions** - No ACID transaction support

## Future Enhancements

Potential improvements for the implementation:

- **Persistence layer** - Optional file-based or database persistence
- **Query engine** - More sophisticated condition-based filtering
- **Event publishing** - Publish entity lifecycle events
- **Metrics** - Track entity operations and workflow executions
- **Configuration** - Configurable workflow trigger behavior
