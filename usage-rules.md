# Usage Rules

This file helps both developers and AI agents follow conventions in the Java Client Template project.

You can sync rules via:
```bash
# Project-specific tooling for rule synchronization
./gradlew syncUsageRules --all --link-to-folder src
```

This file can be updated via project-specific tooling and should be maintained alongside code changes.

<!-- entities-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for entities -->
## Entities

- Always implement `CyodaEntity` interface for domain entities
- Use `Config.ENTITY_VERSION` constant instead of hardcoded version strings
- Place entity classes in `application/entity/` directory for automatic discovery
- Implement `getModelKey()` to return `OperationSpecification.Entity` with proper ModelSpec
- Override `isValid()` method to provide entity-specific validation logic
- Use static `ENTITY_NAME` constant for consistent entity naming
- Entity `id` types vary by entity (e.g., Pet uses `Long`, Mail uses different types)
<!-- entities-end -->

<!-- processors-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for processors -->
## Processors (CyodaProcessor)

- Implement `CyodaProcessor` interface for workflow processing components
- Use `@Component` annotation for automatic Spring discovery
- Inject `SerializerFactory` in constructor and get specific serializer immediately
- Use `ProcessorSerializer.withRequest(request).toEntity(Class).map().complete()` pattern
- Implement `supports(OperationSpecification)` method for processor selection
- Processor names are matched via `supports()` method against workflow configuration
- Use fluent EntityProcessingChain API for type-safe entity processing
- Always handle exceptions properly - either log and handle or throw without logging, never both
<!-- processors-end -->

<!-- criteria-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for criteria -->
## Criteria (CyodaCriterion)

- Implement `CyodaCriterion` interface for workflow criteria checking
- Criteria MUST be pure functions - no side effects or payload modifications
- Use `CriterionSerializer.withRequest(request).evaluateEntity(Class, validator).complete()` pattern
- Implement `supports(OperationSpecification)` method for criteria selection
- Use `EvaluationOutcome.and()` chaining for validation logic
- Return boolean evaluation results only - no entity modifications
- Use `withReasonAttachment(ReasonAttachmentStrategy.toWarnings())` for validation feedback
<!-- criteria-end -->

<!-- serializers-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for serializers -->
## Serializers

- Use `SerializerFactory` to get appropriate serializer instances
- Prefer `ProcessorSerializer` for processors and `CriterionSerializer` for criteria
- Use fluent APIs: `withRequest().toEntity().map().complete()` for processors
- Use fluent APIs: `withRequest().evaluateEntity().complete()` for criteria
- Jackson serializers are the default implementation (`SerializerEnum.JACKSON`)
- Always validate requests before processing in serializer implementations
- Use `ResponseBuilder.forProcessor()` and `ResponseBuilder.forCriterion()` for responses
<!-- serializers-end -->

<!-- services-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for services -->
## Services

- Always interact with `EntityService` interface, not repository directly
- Use `CompletableFuture` return types for all async operations
- Pass entity model name, version, and technical ID for entity operations
- Use `Config.ENTITY_VERSION` for version parameter
- Prefer `getItemsByCondition()` over manual filtering
- Use `addItem()` for single entities, `addItems()` for bulk operations
- Handle `InterruptedException` and `ExecutionException` in service calls
<!-- services-end -->

<!-- controllers-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for controllers -->
## Controllers

- Use `@RestController` and `@RequestMapping` for REST endpoints
- Inject `EntityService` and `ObjectMapper` via constructor
- Use `@Valid` annotation for request body validation
- Return appropriate HTTP status codes (400 for validation, 404 for not found, 500 for errors)
- Log errors with context information (entity ID, operation type)
- Use `ResponseEntity<T>` for proper HTTP response handling
- Convert between entity objects and JSON using `ObjectMapper`
<!-- controllers-end -->

<!-- workflow-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for workflow -->
## Workflow Configuration

- Place `Workflow.json` files alongside entity classes in `application/entity/` directory
- Use finite-state machine (FSM) model for workflow definitions
- Avoid cyclic FSM states in workflow configuration
- Component operation names must match `supports()` method implementations
- Use `WorkflowImportTool` for importing workflow configurations
- Workflow methods should be separate processor classes, not switch-based dispatch
<!-- workflow-end -->

<!-- config-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for config -->
## Configuration

- Use `Config` class constants instead of hardcoded values
- Load environment variables via `Dotenv` in Config class
- Use `Config.ENTITY_VERSION` for entity versioning (default '1')
- Configure GRPC settings via environment variables
- Use `Config.CYODA_HOST` and related constants for Cyoda integration
- SSL and authentication settings should be configurable via environment
<!-- config-end -->

<!-- testing-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for testing -->
## Testing

- Use `PrototypeApplicationTest` for test-based prototype development
- Enable prototype mode via `-Dprototype.enabled=true` system property
- Test serializers using fluent API patterns
- Mock `EntityService` and `SerializerFactory` in unit tests
- Use `ObjectMapper` for JSON conversion in tests
- Test both success and error scenarios for processors and criteria
<!-- testing-end -->

<!-- architecture-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for architecture -->
## Architecture Guidelines

- Follow separation of concerns: entities, processors, criteria, services, controllers
- Use dependency injection via Spring constructor injection
- Implement interfaces rather than concrete classes for better testability
- Use sealed classes for type-safe operation specifications
- Prefer composition over inheritance in workflow components
- Use factory patterns for component selection and instantiation
- Handle async operations with CompletableFuture consistently
<!-- architecture-end -->

<!-- grpc-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for grpc -->
## gRPC Integration

- Use `CyodaCalculationMemberClient` for gRPC communication with Cyoda
- Configure gRPC settings via `Config.GRPC_ADDRESS` and `Config.GRPC_SERVER_PORT`
- Use `Config.GRPC_PROCESSOR_TAG` for processor identification
- Handle gRPC streaming with proper error handling and connection management
- Use protobuf message types for type-safe gRPC communication
- Configure SSL/TLS settings via environment variables
<!-- grpc-end -->

<!-- error-handling-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for error-handling -->
## Error Handling

- Use `ErrorInfo` class for structured error information
- Prefer `StandardErrorCodes` enum over hardcoded error strings
- Use `EvaluationOutcome.Fail` for criteria validation failures
- Log errors with appropriate context (entity ID, operation type, stack trace)
- Use `ResponseBuilder` for consistent error response formatting
- Handle `CompletableFuture` exceptions with proper error propagation
- Never log and rethrow exceptions - choose one approach consistently
<!-- error-handling-end -->

<!-- validation-start -->
<!-- SYNC_COMMENT: synchronize via project tooling for validation -->
## Validation

- Use `@Valid` annotation for request body validation in controllers
- Implement `isValid()` method in entity classes for business validation
- Use `EvaluationOutcome` chaining for complex validation logic
- Prefer `validate()` method in EntityProcessingChain for processor validation
- Use `ReasonAttachmentStrategy` to attach validation reasons to responses
- Validate requests in serializer implementations before processing
- Use `Condition` and `SearchConditionRequest` for query validation
<!-- validation-end -->
