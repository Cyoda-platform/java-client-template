# Usage Rules - Java Client Template

This file provides comprehensive guidelines for developers and AI agents working with the Java Client Template project. All patterns shown are current best practices.

## 🚀 **Core Principles**

- **Performance-Optimized**: Use EntityService methods with clear performance guidance
- **Type-Safe**: Work with EntityWithMetadata<T> throughout the application
- **Unified Interface**: Consistent patterns between processors and controllers
- **Clean Architecture**: Minimal dependencies, clear separation of concerns
- **No Deprecated Patterns**: Only current, recommended approaches

## 📦 **EntityService - Performance-Optimized API**

### ✅ **Correct EntityService Usage**
```java
// Create ModelSpec for entity operations
ModelSpec modelSpec = new ModelSpec().withName("MyEntity").withVersion(1);

// FASTEST - Use when you have technical UUID
EntityWithMetadata<MyEntity> myEntity = entityService.getById(uuid, modelSpec, MyEntity.class);

// MEDIUM SPEED - Use for user-facing identifiers
EntityWithMetadata<MyEntity> myEntity = entityService.findByBusinessId(modelSpec, "CART-123", "myEntityId", MyEntity.class);

// SLOW - Use sparingly for small datasets
List<EntityWithMetadata<MyOtherEntity>> myOtherEntitys = entityService.findAll(modelSpec, MyOtherEntity.class);

// SLOWEST - Use for complex queries
List<EntityWithMetadata<MyOtherEntity>> results = entityService.search(modelSpec, condition, MyOtherEntity.class);

// Mutations
EntityWithMetadata<MyEntity> saved = entityService.create(newMyEntity);
EntityWithMetadata<MyEntity> updated = entityService.update(uuid, myEntity, "CHECKOUT");
```

## 🏗️ **Entities**

- Always implement `CyodaEntity` interface for domain entities
- Use `Config.ENTITY_VERSION` constant instead of hardcoded version strings
- Place entity classes in `application/entity/` directory for automatic discovery
- Implement `getModelKey()` to return `OperationSpecification.Entity` with proper ModelSpec
- Override `isValid()` method to provide entity-specific validation logic
- Use static `ENTITY_NAME` constant for consistent entity naming
- Work with EntityWithMetadata<T> wrapper that includes entity + metadata

## ⚙️ **Processors (CyodaProcessor)**

### ✅ **Correct Processor Patterns**
```java
@Component
public class ExampleProcessor implements CyodaProcessor {
    private final ProcessorSerializer serializer;
    // Optional: Only inject EntityService if you need to interact with other entities
    private final EntityService entityService;

    // Constructor WITHOUT EntityService (for simple processors)
    public ExampleProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    // Constructor WITH EntityService (only if needed)
    public ExampleProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();

        // Unified EntityWithMetadata processing - work directly with EntityWithMetadata
        return serializer.withRequest(request)
            .toEntityWithMetadata(EntityName.class)  // Unified with controllers
            .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
            .map(this::processEntityWithMetadataLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelKey) {
        return "ExampleProcessor".equals(modelKey.operationName());
    }
}
```

### 🚨 **Critical Processor Limitations**
- ✅ **CAN**: Read current entity data using `context.entityResponse()` or `entityWithMetadata.getEntity()`
- ✅ **CAN**: Access current entity metadata: `context.getEvent().getEntityId()` and state
- ✅ **CAN**: Get, update, delete OTHER entities using EntityService
- ❌ **CANNOT**: Update the current entity being processed
- ❌ **CANNOT**: Change the current entity's workflow state
- ❌ **CANNOT**: Use Java reflection - only entity getters/setters
- ❌ **CANNOT**: Modify code in `src/main/java/com/java_template/common` directory

### ❌ **Forbidden Processor Patterns**
```java
// NEVER inject ObjectMapper in processors
public ProcessorName(SerializerFactory factory, ObjectMapper objectMapper) { ... }

// NEVER extract from payload manually
Map<String, Object> payloadMap = objectMapper.convertValue(request.getPayload().getData(), Map.class);

// NEVER try to update current entity in processor
EntityWithMetadata<EntityName> updated = entityService.update(currentEntityId, entity, "TRANSITION");
```

## 🔍 **Criteria (CyodaCriterion)**

### ✅ **Correct Criteria Patterns**
```java
@Component
public class ExampleCriterion implements CyodaCriterion {
    private final CriterionSerializer serializer;

    public ExampleCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse evaluate(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(EntityName.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EntityName> context) {
        EntityName entity = context.entityWithMetadata().entity();
        // Access metadata if needed: UUID id = context.entityWithMetadata().getId();

        return EvaluationOutcome.and(
            EvaluationOutcome.of(entity.getId() != null, "Entity ID must not be null"),
            EvaluationOutcome.of(entity.isValid(), "Entity must be valid")
        );
    }

    @Override
    public boolean supports(OperationSpecification modelKey) {
        return "ExampleCriterion".equals(modelKey.operationName());
    }
}
```

### 🎯 **Criteria Guidelines**
- Criteria MUST be pure functions - no side effects or payload modifications
- Return boolean evaluation results only - no entity modifications
- Use `EvaluationOutcome.and()` chaining for validation logic
- Use `withReasonAttachment(ReasonAttachmentStrategy.toWarnings())` for validation feedback

## 🔄 **Serializers**

### ✅ **Correct Serializer Usage**
```java
// In processor constructor
public ExampleProcessor(SerializerFactory serializerFactory) {
    this.serializer = serializerFactory.getDefaultProcessorSerializer();
}

// In criterion constructor
public ExampleCriterion(SerializerFactory serializerFactory) {
    this.serializer = serializerFactory.getDefaultCriterionSerializer();
}

// Processor fluent API patterns
return serializer.withRequest(request)
    .toEntityWithMetadata(EntityName.class)            // Unified interface
    .validate(validator, "Error message")
    .map(this::processLogic)
    .complete();

// Criterion fluent API pattern
return serializer.withRequest(request)
    .evaluateEntity(EntityName.class, this::validateEntity)
    .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
    .complete();
```

### 🎯 **Serializer Guidelines**
- Use `SerializerFactory` to get appropriate serializer instances
- Prefer `ProcessorSerializer` for processors and `CriterionSerializer` for criteria
- Jackson serializers are the default implementation
- Always validate requests before processing in serializer implementations

## 🎯 **Controllers**

### ✅ **Correct Controller Patterns**
```java
@RestController
@RequestMapping("/ui/entityname")
@CrossOrigin(origins = "*")
public class EntityNameController {
    private final EntityService entityService;

    public EntityNameController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<EntityName>> create(@RequestBody EntityName entity) {
        EntityWithMetadata<EntityName> response = entityService.create(entity);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EntityName>> getById(@PathVariable UUID id) {
        ModelSpec modelSpec = new ModelSpec().withName("EntityName").withVersion(1);
        EntityWithMetadata<EntityName> response = entityService.getById(id, modelSpec, EntityName.class);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/business/{businessId}")
    public ResponseEntity<EntityWithMetadata<EntityName>> getByBusinessId(@PathVariable String businessId) {
        ModelSpec modelSpec = new ModelSpec().withName("EntityName").withVersion(1);
        EntityWithMetadata<EntityName> response = entityService.findByBusinessId(
            modelSpec, businessId, "businessIdField", EntityName.class);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
    }
}
```

### ❌ **Forbidden Controller Patterns**
```java
// NEVER accept Map<String, Object> as request body
@PostMapping
public ResponseEntity<Object> create(@RequestBody Map<String, Object> request) { ... }

// NEVER return generic Object responses
public ResponseEntity<Object> someMethod() { ... }
```

### 🎯 **Controller Guidelines**
- Always return `ResponseEntity<EntityWithMetadata<T>>` for single entities
- Always return `ResponseEntity<List<EntityWithMetadata<T>>>` for multiple entities
- Use appropriate EntityService methods based on performance needs
- Handle errors with proper HTTP status codes (400, 404, 500)

## 📋 **EntityWithMetadata<T> Structure**

### ✅ **Working with EntityWithMetadata**
```java
ModelSpec modelSpec = new ModelSpec().withName("MyEntity").withVersion(1);
EntityWithMetadata<MyEntity> response = entityService.getById(myEntityId, modelSpec, MyEntity.class);

// Access the entity (Lombok @Data generates getEntity() automatically)
MyEntity myEntity = response.getEntity();

// Access metadata (includes technical UUID and workflow state)
UUID technicalId = response.getMetadata().getId();      // Use for subsequent operations
String workflowState = response.getMetadata().getState(); // Current workflow state
Date created = response.getMetadata().getCreationDate();
String lastTransition = response.getMetadata().getTransitionForLatestSave();
```

### 📄 **JSON Structure**
```json
{
  "entity": {
    "myEntityId": "CART-123",
    "items": [...],
    "total": 99.99
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "ACTIVE"
  }
}
```

## 🔄 **Workflow Configuration**

### ✅ **Workflow Best Practices**
- Place workflow JSON files in `src/main/resources/workflow/$entity_name/version_$version/` directory
- Use finite-state machine (FSM) model for workflow definitions
- Avoid cyclic FSM states in workflow configuration
- Component operation names must match `supports()` method implementations
- Use `WorkflowImportTool` for importing workflow configurations
- Workflow methods should be separate processor classes, not switch-based dispatch
- Check workflow documentation for valid transitions when updating entities

### 🎯 **Workflow Transitions**
```java
// In processors: Update OTHER entities with transitions
EntityWithMetadata<OtherEntity> updated = entityService.update(otherEntityId, otherEntity, "TRANSITION_NAME");

// Without transition: Entity loops back to same state
EntityWithMetadata<OtherEntity> updated = entityService.update(otherEntityId, otherEntity, null);
```

## ⚙️ **Configuration**

- Use `Config` class constants instead of hardcoded values
- Load environment variables via `Dotenv` in Config class
- Use `Config.ENTITY_VERSION` for entity versioning (default '1000')
- Configure GRPC settings via environment variables
- Use `Config.CYODA_HOST` and related constants for Cyoda integration
- SSL and authentication settings should be configurable via environment

## 🧪 **Testing**

- Use `PrototypeApplicationTest` for test-based prototype development
- Enable prototype mode via `-Dprototype.enabled=true` system property
- Test serializers using fluent API patterns
- Mock `EntityService` and `SerializerFactory` in unit tests
- Test both success and error scenarios for processors and criteria
- Add tests to `src/test/java/com/java_template/application` directory

## 🏗️ **Architecture Guidelines**

- Follow separation of concerns: entities, processors, criteria, services, controllers
- Use dependency injection via Spring constructor injection
- Implement interfaces rather than concrete classes for better testability
- Use sealed classes for type-safe operation specifications
- Prefer composition over inheritance in workflow components
- Use factory patterns for component selection and instantiation
- Work with EntityWithMetadata<T> for unified interface consistency

## 🌐 **gRPC Integration**

- Use `CyodaCalculationMemberClient` for gRPC communication with Cyoda
- Configure gRPC settings via `Config.GRPC_ADDRESS` and `Config.GRPC_SERVER_PORT`
- Use `Config.GRPC_PROCESSOR_TAG` for processor identification
- Handle gRPC streaming with proper error handling and connection management
- Use protobuf message types for type-safe gRPC communication
- Configure SSL/TLS settings via environment variables

## ⚠️ **Error Handling**

### ✅ **Correct Error Handling Patterns**
```java
// Use ErrorInfo with StandardErrorCodes
return serializer.responseBuilder(request)
    .withError(StandardErrorCodes.PROCESSING_ERROR.getCode(), "Processing failed")
    .build();

// Use ErrorInfo static methods for common errors
ErrorInfo error = ErrorInfo.processingError("Entity validation failed");
ErrorInfo error = ErrorInfo.validationError("Required field missing");
ErrorInfo error = ErrorInfo.evaluationError("Criteria evaluation failed");

// Use ErrorInfo.fromException for exception handling
try {
    // processing logic
} catch (Exception e) {
    ErrorInfo error = ErrorInfo.fromException(StandardErrorCodes.PROCESSING_ERROR.getCode(), e);
    return serializer.responseBuilder(request)
        .withError(error.code(), error.message())
        .build();
}
```

### 🎯 **Error Handling Guidelines**
- Use `ErrorInfo` class for structured error information
- Prefer `StandardErrorCodes` enum over hardcoded error strings
- Use `EvaluationOutcome.Fail` for criteria validation failures
- Log errors with appropriate context (entity ID, operation type, stack trace)
- Use `ResponseBuilder` for consistent error response formatting
- Never log and rethrow exceptions - choose one approach consistently

## ✅ **Validation**

- Use `@Valid` annotation for request body validation in controllers
- Implement `isValid()` method in entity classes for business validation
- Use `EvaluationOutcome` chaining for complex validation logic
- Prefer `validate()` method in EntityResponseProcessingChain for processor validation
- Use `ReasonAttachmentStrategy` to attach validation reasons to responses
- Validate requests in serializer implementations before processing
- Use `Condition` and `SearchConditionRequest` for query validation

## 🚫 **Critical Restrictions**

- **CANNOT** use Java reflection - only entity getters/setters
- **CANNOT** modify code in `src/main/java/com/java_template/common` directory
- **CANNOT** update current entity in processors - only OTHER entities
- **MUST** run compilation checks frequently
- **MUST** use performance-optimized EntityService methods
- **MUST** work with EntityWithMetadata<T> for consistency
