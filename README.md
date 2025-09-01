# Java Client Template

A structured template for building scalable web clients using **Spring Boot**, designed for seamless integration with **Cyoda** over **gRPC** and **REST**.

## 🤖 AI-Friendly Documentation

### Background

Previously, AI agents hallucinated outdated APIs and ignored documentation due to HTML rendering issues and incomplete context. To fix this, all documentation is provided as raw `.md` files with structured formats that AI agents can easily parse and understand.

### Available Documentation Tools

- **llms.txt**: Lists all project documentation with absolute URLs to raw markdown files
- **llms-full.txt**: Contains embedded full markdown content of all documentation for single-pass ingestion
- **usage-rules.md**: Developer and AI agent guidelines with sync markers for automated updates

### For AI Agents

When working with this project:

1. **Discovery**: Use `llms.txt` to discover available documentation
2. **Context**: Use `llms-full.txt` for complete project context in one file
3. **Guidelines**: Always consult `usage-rules.md` before making code changes
4. **Patterns**: Follow the established architectural patterns for processors, criteria, and serializers

### Documentation Sync

You can sync usage rules via project-specific tooling and maintain consistency across components.

---

## 🚀 Features

- **Spring Boot**-based fast backend starter.
- Modular, extensible structure for rapid iteration.
- Built-in support for **gRPC** and **REST** APIs.
- Integration with **Cyoda**: workflow-driven backend interactions.
- Serialization architecture with fluent APIs for type-safe processing.

---

## 🛠️ Getting Started

> ☕ **Java 21 Required**  
> Make sure Java 21 is installed and set as the active version.

### 1. Clone the Project

```bash
git clone <your-repository-URL>
cd java-client-template
```

### 2. 🧰 Run Workflow Import Tool

#### Option 1: Run via Gradle (recommended for local development)
```bash
./gradlew runApp -PmainClass=com.java_template.common.tool.WorkflowImportTool
```

#### Option 2: Build and Run JAR (recommended for CI or scripting)
```bash
./gradlew bootJarWorkflowImport
java -jar build/libs/java-client-template-1.0-SNAPSHOT-workflow-import.jar
```

### 3. ▶️ Run the Application

#### Option 1: Run via Gradle
```bash
./gradlew runApp
```

#### Option 2: Run Manually After Build
```bash
./gradlew build
java -jar build/libs/java-client-template-1.0-SNAPSHOT.jar
```

> Access the app: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
>
> **Note**: The default port is 8080 as configured in `src/main/resources/application.yml`. You can change this by setting the `server.port` property.

---

## 🧩 Project Structure

### `common/`
Integration logic with Cyoda.

- `auth/` – Manages login and refresh token logic (modifications not typically required).
- `config/` – Contains constants, environment variables from .env, and enums.
- `grpc/` – Handles integration with the Cyoda gRPC server (modifications usually unnecessary).
- `repository/` – Facilitates integration with the Cyoda REST API (modifications usually unnecessary).
- `service/` – Service layer for your application.
- `util/` – Various utility functions.
- `workflow/` – Core workflow processing architecture with CyodaProcessor and CyodaCriterion interfaces.
- `serializer/` – Serialization layer with fluent APIs for processing requests and responses.
- `tool/` – Utility tools like WorkflowImportTool for importing workflow configurations.

To interact with **Cyoda**, use `common/service/EntityService.java`, which provides all necessary methods.

To add new integrations with Cyoda, extend the following files:

- **Interface** — `common/service/EntityService.java`: defines service methods; abstraction layer for Cyoda. Optional to modify.
- **Implementation** — `common/service/EntityServiceImpl.java`: implements interface methods and business logic. Optional to modify.
- **Repository Interface** — `common/repository/CrudRepository.java`: defines a generic interface. Modify only if additional operations are needed.
- **Cyoda Repository** — `common/repository/CyodaRepository.java`: implements repository methods. Modify only if needed.

> ⚠️ `CrudRepository.java` and `CyodaRepository.java` rarely change — only for significant updates to the data access layer.

✅ Always interact with the **service interface**, not directly with the repository.

---

### `application/`
Application-specific logic and components:

- `controller/` – HTTP endpoints and REST API controllers.
- `entity/` – Domain entities (e.g., `pet/Pet.java`) that implement `CyodaEntity`.
- `processor/` – Workflow processors that implement `CyodaProcessor` interface.
- `criterion/` – Workflow criteria that implement `CyodaCriterion` interface.

### `entity/`
Domain logic structure. Contains entity structures.

- `$entity_name/Workflow.json` – Workflow configuration files should be placed alongside entities in `application/entity/`.

---

## ⚙️ API Reference – `EntityService`

| Method                                                                                  | Return type                     | Description                           |
|-----------------------------------------------------------------------------------------|---------------------------------|---------------------------------------|
| `getItem(String model, String version, UUID technicalId)`                               | `CompletableFuture<ObjectNode>` | Get item by ID                        |
| `getItems(String model, String version)`                                                | `CompletableFuture<ArrayNode>`  | Get all items by model and version    |
| `getItemsByCondition(String entityModel, String entityVersion, Object condition)`       | `CompletableFuture<ArrayNode>`  | Get multiple items by condition       |
| `addItem(String entityModel, String entityVersion, Object entity)`                      | `CompletableFuture<UUID>`       | Add item                              |
| `addItems(String entityModel, String entityVersion, Object entities)`                   | `CompletableFuture<List<UUID>>` | Add multiple items                    |
| `updateItem(String entityModel, String entityVersion, UUID technicalId, Object entity)` | `CompletableFuture<UUID>`       | Update item                           |
| `deleteItem(String entityModel, String entityVersion, UUID technicalId)`                | `CompletableFuture<UUID>`       | Delete item                           |
| `deleteItems(String entityModel, String entityVersion)`                                 | `CompletableFuture<ArrayNode>`  | Delete all items by model and version |

> Use `import static com.java_template.common.config.Config.ENTITY_VERSION` for consistent versioning.

### 🔑 Where to get `technicalId`

For all methods that require a `technicalId` (such as `updateItem` or `deleteItem`), this field is **automatically included** in the returned entity when using methods like `getItem(...)`, `getItems(...)`, or `getItemsByCondition(...)`.

### 🔑 EntityResponse<T> Structure

All EntityService methods return `EntityResponse<T>` with rich metadata:

```java
EntityResponse<Product> response = entityService.save(product);

// Access business data
Product product = response.getData();

// Access metadata
UUID technicalId = response.getId();        // For updates/deletes
LocalDateTime created = response.getCreatedAt();
LocalDateTime updated = response.getUpdatedAt();
Integer version = response.getVersion();    // For optimistic locking
```

## ⚙️ EntityService API (JPA-Style)

The EntityService follows **Spring Boot JPA Repository** patterns with **type-safe, synchronous operations**.

### 🔄 Core Operations
```java
// Save entity (JPA-style)
EntityResponse<Product> response = entityService.save(product);
Product savedProduct = response.getData();

// Find by business ID (SKU, orderId, etc.)
EntityResponse<Product> productResponse = entityService.findByBusinessId(Product.class, "SKU123");

// Find all entities
List<EntityResponse<Product>> responses = entityService.findAll(Product.class);

// Update with optional transition
EntityResponse<Product> updated = entityService.updateByBusinessId(product, "approve");

// Delete by business ID
boolean deleted = entityService.deleteByBusinessId(Product.class, "SKU123");
```

### 🔍 Advanced Search (Type-Safe)
```java
// Simple field search
List<EntityResponse<Product>> products = entityService.findByField(Product.class, "category", "electronics");

// Complex search conditions
SearchConditionRequest condition = SearchConditionRequest.group("AND",
    Condition.of("$.category", "EQUALS", "electronics"),
    Condition.of("$.price", "GREATER_THAN", 100),
    Condition.of("$.inStock", "EQUALS", true)
);

List<EntityResponse<Product>> results = entityService.findByCondition(Product.class, condition, false);
```

### ✨ Key Features
- **🔄 JPA-Style**: `save()`, `findAll()`, `deleteById()` - familiar to Spring Boot developers
- **🛡️ Type Safety**: All methods use proper generics and `SearchConditionRequest`
- **📊 Rich Metadata**: Every response includes technical ID, timestamps, etc.
- **⚡ Synchronous**: Clean, simple method calls (no CompletableFuture)
- **🎯 Transition Support**: Update operations support workflow transitions

---

## 🔄 Workflow Configuration

Located at:
```
application/entity/$entity_name/Workflow.json
```

> **Note**: Workflow configuration files should be placed alongside their corresponding entity classes in the `application/entity/` directory structure.

This file defines the workflow configuration using a **finite-state machine (FSM)**
model, which specifies states and transitions between them.

The workflow JSON consists of:
- **Metadata**: `version`, `name`, `desc`, `initialState`, `active`
- **Global criterion**: Optional workflow-level criterion for applicability
- **States**: Dictionary of states with their transitions
- **Transitions**: Each transition has `name`, `next`, `manual` flag, and optional `processors`/`criterion`

**Rules:**
- Start from the defined `initialState`.
- Avoid loops.
- If there are **multiple transitions** from one state,
  a **criterion** is required for each transition to decide which one to use.

FSM example:

```json
{
  "version": "1.0",
  "name": "template_workflow",
  "desc": "Template FSM with structured states, transitions, processors, and criterions",
  "initialState": "none",
  "active": true,
  "states": {
    "none": {
      "transitions": [
        {
          "name": "transition_to_01",
          "next": "state_01"
        }
      ]
    },
    "state_01": {
      "transitions": [
        {
          "name": "transition_to_02",
          "next": "state_02",
          "manual": true,
          "processors": [
            {
              "name": "example_function_name",
              "executionMode": "ASYNC_NEW_TX",
              "config": {
                "attachEntity": true,
                "calculationNodesTags": "cyoda_application",
                "responseTimeoutMs": 3000,
                "retryPolicy": "FIXED"
              }
            }
          ]
        }
      ]
    },
    "state_02": {
      "transitions": [
        {
          "name": "transition_with_criterion_simple",
          "next": "state_criterion_check_01",
          "processors": [
            {
              "name": "example_function_name",
              "executionMode": "ASYNC_NEW_TX",
              "config": {
                "attachEntity": true,
                "calculationNodesTags": "cyoda_application",
                "responseTimeoutMs": 3000,
                "retryPolicy": "FIXED"
              }
            }
          ],
          "criterion": {
            "type": "function",
            "function": {
              "name": "example_function_name_returns_bool",
              "config": {
                "attachEntity": true,
                "calculationNodesTags": "cyoda_application",
                "responseTimeoutMs": 5000,
                "retryPolicy": "FIXED"
              }
            }
          }
        }
      ]
    },
    "state_criterion_check_01": {
      "transitions": [
        {
          "name": "transition_with_criterion_group",
          "next": "state_terminal",
          "criterion": {
            "type": "group",
            "operator": "AND",
            "conditions": [
              {
                "type": "simple",
                "jsonPath": "$.sampleFieldA",
                "operation": "EQUALS",
                "value": "template_value_01"
              }
            ]
          }
        }
      ]
    },
    "state_terminal": {
      "transitions": []
    }
  }
}
```

### ✅ Criterion Types

There are **three types of criteria** used to control transitions:

1. **Simple criterion** — Direct field comparison
   Evaluates a single field against a value using an operation.

   ```json
   "criterion": {
     "type": "simple",
     "jsonPath": "$.customerType",
     "operation": "EQUALS",
     "value": "premium"
   }
   ```

2. **Group criterion** — Logical combination of conditions
   Combines multiple simple or group conditions using logical operators.

   > ✅ **Note:** `Group` criteria support **nesting**.
   > You can include both `simple` and `group` conditions inside the `conditions` array.

   ```json
   "criterion": {
     "type": "group",
     "operator": "AND",
     "conditions": [
       {
         "type": "simple",
         "jsonPath": "$.creditScore",
         "operation": "GREATER_OR_EQUAL",
         "value": 700
       },
       {
         "type": "simple",
         "jsonPath": "$.annualRevenue",
         "operation": "GREATER_THAN",
         "value": 1000000
       }
     ]
   }
   ```

3. **Function criterion** — Custom client-side evaluation
   Executes a custom function with optional nested criterion.

   > ⚠️ The function must be implemented as a `CyodaCriterion` component.
   > Its name **must be unique and match** `function.name`.

   ```json
   "criterion": {
     "type": "function",
     "function": {
       "name": "example_function_name_returns_bool",
       "config": {
         "attachEntity": true,
         "calculationNodesTags": "validation,criteria",
         "responseTimeoutMs": 5000,
         "retryPolicy": "FIXED"
       },
       "criterion": {
         "type": "simple",
         "jsonPath": "$.sampleFieldB",
         "operation": "GREATER_THAN",
         "value": 100
       }
     }
   }
   ```

   > **jsonPath field reference:**
   > - Use the **`$.` prefix** for custom (business) fields of the entity.
   > - Use **no prefix** for built-in entity meta-fields.
   >   Supported meta-fields: `state`, `previousTransition`, `creationDate`, `lastUpdateTime`.

Supported criterion `types`:

- `simple`
- `group`
- `function`

Supported group `operator` values:

- `AND`
- `OR`
- `NOT`

Supported operation values (`*_OR_EQUAL` includes the boundary):

```
EQUALS, NOT_EQUAL, IS_NULL, NOT_NULL, GREATER_THAN, GREATER_OR_EQUAL, LESS_THAN, LESS_OR_EQUAL,
CONTAINS, STARTS_WITH, ENDS_WITH, NOT_CONTAINS, NOT_STARTS_WITH, NOT_ENDS_WITH, MATCHES_PATTERN, BETWEEN, BETWEEN_INCLUSIVE
```

---

## 🧠 Workflow Processors

The logic for processing workflows is implemented using **CyodaProcessor** and **CyodaCriterion** interfaces in the `application/` directory.

### Processor Architecture

Processors implement the `CyodaProcessor` interface and use the **EntityProcessingChain** API for type-safe entity processing. Processors are configured in workflow transitions with execution modes and configuration options:

```java
@Component
public class AddLastModifiedTimestamp implements CyodaProcessor {

    private final ProcessorSerializer serializer;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(pet -> pet.getId() != null && pet.getName() != null)
            .map(pet -> {
                pet.addLastModifiedTimestamp();
                return pet;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelKey) {
        // Match based on processor name from workflow configuration
        return "example_function_name".equals(modelKey.operationName());
    }
}
```

### Processor Configuration

Processors in workflow transitions support various execution modes and configuration options:

```json
"processors": [
  {
    "name": "example_function_name",
    "executionMode": "SYNC",
    "config": {
      "attachEntity": true,
      "calculationNodesTags": "test_tag_01",
      "responseTimeoutMs": 3000,
      "retryPolicy": "FIXED"
    }
  }
]
```

**Execution Modes:**
- `SYNC` - Synchronous execution (default)
- `ASYNC_SAME_TX` - Asynchronous execution in the same transaction
- `ASYNC_NEW_TX` - Asynchronous execution in new transaction

**Configuration Options:**
- `attachEntity` - Whether to attach entity data to the request
- `calculationNodesTags` - Tags for calculation node selection
- `responseTimeoutMs` - Response timeout in milliseconds
- `retryPolicy` - Retry policy for failed executions

### Execution Mode Reference

```json
"executionMode": {
  "type": "string",
  "description": "Execution mode of the processor",
  "enum": [
    "SYNC",
    "ASYNC_SAME_TX",
    "ASYNC_NEW_TX"
  ],
  "default": "SYNC"
}
```

### EntityProcessingChain API

The **EntityProcessingChain** provides a clean, type-safe API for entity processing:

- `toEntity(Class<T>)` - Extract entity and initiate entity flow
- `map(Function<T, T>)` - Transform entity instances
- `validate(Function<T, Boolean>)` - Validate entities with default error message
- `validate(Function<T, Boolean>, String)` - Validate entities with custom error message
- `toJsonFlow(Function<T, JsonNode>)` - Switch to JSON processing
- `complete()` - Complete processing with automatic entity-to-JSON conversion
- `complete(Function<T, JsonNode>)` - Complete with custom entity converter

### Criteria Implementation

Criteria implement the `CyodaCriterion` interface for condition checking using **EvaluationOutcome** sealed classes with **logical chaining**:

```java
@Component
public class IsValidPet implements CyodaCriterion {

    private final CriterionSerializer serializer;

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(Pet.class, this::validatePet)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    private EvaluationOutcome validatePet(Pet pet) {
        // Chain all validation checks with AND logic - first failure stops the chain
        return validatePetExists(pet)
            .and(validatePetBasicValidity(pet))
            .and(validateBasicStructure(pet))
            .and(validateBusinessRules(pet))
            .and(validateDataQuality(pet));
    }

    private EvaluationOutcome validatePetExists(Pet pet) {
        return pet == null ?
            EvaluationOutcome.Fail.structuralFailure("Pet entity is null") :
            EvaluationOutcome.success();
    }

    private EvaluationOutcome validateBasicStructure(Pet pet) {
        // Chain multiple field validations
        return validatePetId(pet).and(validatePetName(pet));
    }

    // ... other validation methods
}
```

### ⚙️ Registration mechanism

Workflow components are **automatically discovered** via Spring's dependency injection system using the `OperationFactory`.

Here’s how it works:

1. **Processor Discovery**: All Spring beans implementing `CyodaProcessor` are automatically collected by the `OperationFactory`.

2. **Criterion Discovery**: All Spring beans implementing `CyodaCriterion` are automatically collected by the `OperationFactory`.

3. **Operation Matching**: When a gRPC event arrives, the `OperationFactory` finds the appropriate processor or criterion by:
   - Calling the `supports(OperationSpecification)` method on each component
   - Matching based on the operation name from workflow configuration (e.g., `action.name` or `condition.function.name`)
   - Caching successful matches for performance using `ConcurrentHashMap`

4. **Execution**: The matched component processes the request using its `process()` or `check()` method.

### Component Registration

To register a processor or criterion:

1. **Create a class** implementing `CyodaProcessor` or `CyodaCriterion`
2. **Add `@Component` annotation** for Spring discovery
3. **Implement the `supports()` method** to define when this component should be used
4. **Implement the processing method** (`process()` for processors, `check()` for criteria)

> ✅ Component operation names are matched against the `processors[].name` or `criterion.function.name` in workflow configuration via the `supports()` method. The `supports()` method should return `true` when `modelKey.operationName()` matches the expected operation name.

---

## 🔄 Serializer Architecture

The application uses a serializer architecture with fluent APIs:

### ProcessorSerializer (in `common/serializer/`)
- **Purpose**: Handles entity extraction and response building for processors
- **Key Methods**:
  - `withRequest(request)` - Start fluent processing chain
  - `extractEntity(request, Class<T>)` - Extract typed entities
  - `extractPayload(request)` - Extract raw JSON payload
  - `responseBuilder(request)` - Create response builders

### CriterionSerializer (in `common/serializer/`)
- **Purpose**: Handles entity extraction and response building for criteria
- **Key Methods**:
  - `withRequest(request)` - Start fluent evaluation chain
  - `evaluate(Function<JsonNode, EvaluationOutcome>)` - Evaluate JSON with outcomes
  - `evaluateEntity(Class<T>, Function<T, EvaluationOutcome>)` - Evaluate entities with outcomes
  - `withReasonAttachment(ReasonAttachmentStrategy)` - Configure reason attachment
  - `withErrorHandler(BiFunction<Throwable, JsonNode, ErrorInfo>)` - Configure error handling

### ProcessingChain vs EntityProcessingChain
- **ProcessingChain**: JSON-based processing with `map(Function<JsonNode, JsonNode>)`
- **EntityProcessingChain**: Type-safe entity processing with `map(Function<T, T>)`
- **Transition**: Use `toEntity(Class<T>)` to switch from JSON to entity flow
- **Transition**: Use `toJsonFlow(Function<T, JsonNode>)` to switch from entity to JSON flow

### SerializerFactory
- **Purpose**: Provides access to different serializer implementations
- **Default**: Jackson-based serializers for JSON processing
- **Usage**: Injected into processors and criteria for consistent serialization

**Example Usage:**
```java
// Instead of hardcoded strings
return serializer.responseBuilder(request)
    .withError("PROCESSING_ERROR", "Processing failed")
    .build();

// Use the enum for type safety
return serializer.responseBuilder(request)
    .withError(StandardErrorCodes.PROCESSING_ERROR.getCode(), "Processing failed")
    .build();
```

### EvaluationOutcome Sealed Classes

Criteria evaluation uses **EvaluationOutcome** sealed classes for type-safe result handling with **logical chaining**:

```java
// Success outcome (no additional information needed)
return EvaluationOutcome.success();

// Failure outcomes with categorized reasons
return EvaluationOutcome.Fail.structuralFailure("Pet entity is null");
return EvaluationOutcome.Fail.businessRuleFailure("Pet status is invalid");
return EvaluationOutcome.Fail.dataQualityFailure("Pet photo URL is malformed");

// Generic failure with custom category
return EvaluationOutcome.Fail.of("Custom reason", StandardEvalReasonCategories.VALIDATION_FAILURE);
```

**Logical Chaining Operations:**

```java
// AND chaining - all must succeed, returns first failure
EvaluationOutcome result = validateStructure(pet)
    .and(validateBusinessRules(pet))
    .and(validateDataQuality(pet));

// OR chaining - any can succeed, returns first success or last failure
EvaluationOutcome result = primaryValidation(pet)
    .or(fallbackValidation(pet));

// Bulk operations with short-circuiting (using suppliers)
EvaluationOutcome allMustPass = EvaluationOutcome.allOf(
    () -> validateStructure(pet),
    () -> validateBusinessRules(pet),
    () -> validateDataQuality(pet)
);

EvaluationOutcome anyCanPass = EvaluationOutcome.anyOf(
    () -> primaryValidation(pet),
    () -> fallbackValidation(pet),
    () -> lastResortValidation(pet)
);

// Convenience overloads (no short-circuiting - all arguments evaluated)
EvaluationOutcome allMustPass2 = EvaluationOutcome.allOf(check1, check2, check3);
EvaluationOutcome anyCanPass2 = EvaluationOutcome.anyOf(check1, check2, check3);

// Convenience methods
if (result.isSuccess()) { /* handle success */ }
if (result.isFailure()) { /* handle failure */ }
```

**Key Benefits:**
- **Type Safety**: Compile-time checking ensures proper outcome handling
- **Clear Contracts**: No ambiguity about success vs failure
- **Categorized Failures**: Structured failure reasons with standard categories
- **Logical Chaining**: Elegant AND/OR operations with short-circuit evaluation
- **Efficient Bulk Operations**: Supplier-based `allOf()`/`anyOf()` provide true short-circuiting
- **Flexible API**: Both lazy (suppliers) and eager (direct values) evaluation options
- **Reason Attachment**: Failure reasons can be attached to response warnings

---

## 🔧 EntityProcessingChain Usage Examples

### Basic Entity Processing
```java
// Simple entity transformation
return serializer.withRequest(request)
    .toEntity(Pet.class)
    .map(pet -> {
        pet.setName(pet.getName().toUpperCase());
        return pet;
    })
    .complete();
```

### Entity Processing with Validation
```java
// Entity processing with validation steps
return serializer.withRequest(request)
    .toEntity(Pet.class)
    .validate(pet -> pet.getId() != null, "Pet ID cannot be null")
    .map(pet -> pet.normalizeStatus())
    .validate(pet -> pet.hasStatus(), "Pet must have a valid status")
    .map(pet -> pet.addLastModifiedTimestamp())
    .complete();
```

### Mixed Entity and JSON Processing
```java
// Start with entity processing, then switch to JSON
return serializer.withRequest(request)
    .toEntity(Pet.class)
    .map(pet -> pet.processBusinessLogic())
    .toJsonFlow(pet -> createEnhancedJson(pet))
    .map(json -> addMetadata(json))
    .complete();
```

### Custom Error Handling
```java
// Entity processing with custom error handling
return serializer.withRequest(request)
    .toEntity(Pet.class)
    .withErrorHandler((error, pet) -> new ErrorInfo(
        "PET_PROCESSING_ERROR",
                "Failed to process pet " + (pet != null ? pet.getId() : "unknown")
        ))
    .map(pet -> pet.validateAndProcess())
    .complete();
```

---

## 📝 Notes

- Entity `id` type varies by entity (e.g., Pet entity uses `Long`).
- Use `CyodaProcessor` and `CyodaCriterion` interfaces for workflow components.
- Leverage the **EntityProcessingChain** API for type-safe entity processing.
- Component operation names are matched via the `supports()` method against workflow configuration.
- Use serializers in `common/serializer/` for development.
- Avoid cyclic FSM states.
- Place entities in `application/entity/` directory.
- Use `@Component` annotation for automatic Spring discovery of workflow components.
