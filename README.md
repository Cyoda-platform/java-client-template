# Java Client Template

A structured template for building scalable web clients using **Spring Boot**, designed for seamless integration with **Cyoda** over **gRPC** and **REST**.

---

## 🚀 Features

- **Spring Boot**-based fast backend starter.
- Modular, extensible structure for rapid iteration.
- Built-in support for **gRPC** and **REST** APIs.
- Integration with **Cyoda**: workflow-driven backend interactions.
- Modern serialization architecture with fluent APIs for type-safe processing.

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
- `serializer/` – Modern serialization layer with fluent APIs for processing requests and responses.
- `tool/` – Utility tools like WorkflowImportTool for importing workflow configurations.

To interact with **Cyoda**, use `common/service/EntityService.java`, which provides all necessary methods.

To add new integrations with Cyoda, extend the following files:

- **Interface** — `common/service/EntityService.java`: defines service methods; abstraction layer for Cyoda. Optional to modify.
- **Implementation** — `common/service/EntityServiceImpl.java`: implements interface methods and business logic. Optional to modify.
- **Repository Interface** — `common/repository/CrudRepository.java`: defines a generic interface. Modify only if new operations are needed.
- **Cyoda Repository** — `common/repository/CyodaRepository.java`: implements repository methods. Modify only if needed.

> ⚠️ `CrudRepository.java` and `CyodaRepository.java` rarely change — only for significant updates to the data access layer.

✅ Always interact with the **service interface**, not directly with the repository.

---

### `application/`
Application-specific logic and components:

- `controller/` – HTTP endpoints and REST API controllers.
- `entity/` – Domain entities (e.g., `pet/Pet.java`) that implement `CyodaEntity`.
- `processor/` – Workflow processors that implement `CyodaProcessor` interface.
- `criteria/` – Workflow criteria that implement `CyodaCriterion` interface.
- `serializer_deprecated/` – Legacy serialization layer (deprecated, use `common/serializer/` instead).
- `cyoda_dto/` – Data transfer objects for Cyoda integration.

### `entity/` (deprecated structure)
Legacy domain logic structure. New entities should be placed in `application/entity/`.

- Contains legacy entity structures (currently mostly empty).
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

The `technicalId` represents the internal unique identifier of the entity instance and is required for update or delete operations.  
It is injected into the resulting `ObjectNode` during data retrieval:

```java
dataNode.put("technicalId", idNode.asText());
```

Make sure to preserve this field if the entity is passed to another operation.

## 📦 Example: getItemsByCondition

To use `getItemsByCondition`, pass a condition object constructed with the following utility classes:

> Please use the following classes to construct search conditions for entity queries:
> - `Condition` – `com.java_template.common.util.Condition`
> - `SearchConditionRequest` – `com.java_template.common.util.SearchConditionRequest`

To create a condition, wrap it into a `SearchConditionRequest` with one or multiple elements in the list.
Use logical operators `AND`, `OR`, or `NOT`:

```java
SearchConditionRequest.group("AND",
    Condition.of("$.fieldName1", "EQUALS", "value"),
    Condition.of("$.fieldName2", "GREATER_THAN", 10)
);
```

You can then pass the resulting `SearchConditionRequest` object as the `condition` parameter to `getItemsByCondition`.

```java
entityService.getItemsByCondition("exampleModel", ENTITY_VERSION, yourSearchCondition);
```

---

## 🔄 Workflow Configuration

Located at:
```
application/entity/$entity_name/Workflow.json
```

> **Note**: Workflow configuration files should be placed alongside their corresponding entity classes in the `application/entity/` directory structure.

This file defines the workflow configuration using a **finite-state machine (FSM)**  
model, which specifies states and transitions between them.

The FSM JSON should consist of an **ordered dictionary of states**.  
Each state has a dictionary of **transitions**.  
Each transition has a `next` attribute, which identifies the next state.  
Each transition may have an `action` or `condition`.
Ideally, there should be **one action or condition per transition**.

**Rules:**
- Always start from an initial state `'none'`.
- Avoid loops.
- If there are **multiple transitions** from one state,  
  a **condition** is required for each transition to decide which one to use.


FSM example:

```json
{
  "version": "1.0",
  "description": "Template FSM with structured states, transitions, actions, and conditions",
  "initial_state": "none",
  "workflow_name": "template_workflow",
  "states": {
    "none": {
      "transitions": {
        "transition_to_01": {
          "next": "state_01"
        }
      }
    },
    "state_01": {
      "transitions": {
        "transition_to_02": {
          "next": "state_02",
          "action": {
            "name": "exampleFunctionName"
          }
        }
      }
    },
    "state_02": {
      "transitions": {
        "transition_with_condition_simple": {
          "next": "state_condition_check_01",
          "condition": {
            "type": "function",
            "function": {
              "name": "exampleConditionName"
            }
          }
        }
      }
    },
    "state_condition_check_01": {
      "transitions": {
        "transition_with_condition_group": {
          "next": "state_terminal",
          "condition": {
            "type": "group",
            "name": "conditionName",
            "operator": "AND",
            "parameters": [
              {
                "jsonPath": "$.sampleField",
                "operatorType": "IEQUALS",
                "value": "templateValue",
                "type": "simple"
              }
            ]
          }
        }
      }
    }
  }
}
```

### ✅ Condition Types

There are **two types of conditions** used to control transitions:

1. **Function condition** — evaluated on the **client side**  
   Specify the function name in `condition.function.name`.

   > ⚠️ The method must be implemented inside `entity/$entity_name/Workflow.java`.  
   > Its name **must be unique and match** `condition.function.name`.



   ```json
   "condition": {
     "type": "function",
     "function": {
       "name": "exampleConditionName"
     }
   }
   ```

2. **Group (server-side) condition** — evaluated on the **server**  
   Defined using `type: "group"` with parameters.  
   Logic is evaluated by the Cyoda engine.

   > ✅ **Note:** `Group` (server-side) conditions support **nesting**.
   > You can include both `simple` and `group` conditions inside the `parameters` array.

   > **jsonPath field reference:**
   > - Use the **`$.` prefix** for custom (business) fields of the entity.
   > - Use **no prefix** for built-in entity meta-fields.  
       >   Supported meta-fields: `state`, `previousTransition`, `creationDate`, `lastUpdateTime`.

Example:

   ```json
   "condition": {
      "type": "group",
      "name": "conditionName",
      "operator": "AND",
      "parameters": [
         {
            "jsonPath": "$.sampleField1",
            "operatorType": "IEQUALS",
            "value": "templateValue",
            "type": "simple"
         },
         {
            "jsonPath": "$.sampleField2",
            "operatorType": "GREATER_THAN",
            "value": 1,
            "type": "simple"
         },
         {
            "jsonPath": "previousTransition",
            "operatorType": "IEQUALS",
            "value": "update",
            "type": "simple"
         }
      ]
   }
   ```

Supported condition `types`:

- `simple`
- `group`

Supported group `operator` values:

- `AND`
- `OR`
- `NOT`

Supported operatorType values (`I*` - ignore case):

```
EQUALS, NOT_EQUAL, IEQUALS, INOT_EQUAL, IS_NULL, NOT_NULL, GREATER_THAN, GREATER_OR_EQUAL, LESS_THAN, LESS_OR_EQUAL,
ICONTAINS, ISTARTS_WITH, IENDS_WITH, INOT_CONTAINS, INOT_STARTS_WITH, INOT_ENDS_WITH, MATCHES_PATTERN, BETWEEN, BETWEEN_INCLUSIVE
```

---

## 🧠 Workflow Processors

The logic for processing workflows is implemented using **CyodaProcessor** and **CyodaCriterion** interfaces in the `application/` directory.

### Modern Processor Architecture

Processors implement the `CyodaProcessor` interface and use the **EntityProcessingChain** API for type-safe entity processing:

```java
@Component
public class AddLastModifiedTimestamp implements CyodaProcessor {

    private final ProcessorSerializer serializer;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();

        // Modern fluent entity processing with validation
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
        return "AddLastModifiedTimestamp".equals(modelKey.operationName());
    }
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

Criteria implement the `CyodaCriterion` interface for condition checking using **EvaluationOutcome** sealed classes:

```java
@Component
public class IsValidPet implements CyodaCriterion {

    private final CriterionSerializer serializer;

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(Pet.class, pet -> {
                // Validation logic with explicit outcomes
                if (pet == null) {
                    return EvaluationOutcome.Fail.structuralFailure("Pet entity is null");
                }
                if (!pet.isValid()) {
                    return EvaluationOutcome.Fail.structuralFailure("Pet validation failed");
                }
                return EvaluationOutcome.success();
            })
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }
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

To register a new processor or criterion:

1. **Create a class** implementing `CyodaProcessor` or `CyodaCriterion`
2. **Add `@Component` annotation** for Spring discovery
3. **Implement the `supports()` method** to define when this component should be used
4. **Implement the processing method** (`process()` for processors, `check()` for criteria)

> ✅ Component operation names are matched against the `action.name` or `condition.function.name` in workflow configuration via the `supports()` method. The `supports()` method should return `true` when `modelKey.operationName()` matches the expected operation name.

---

## 🔄 Serializer Architecture

The application uses a modern serializer architecture with fluent APIs:

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

Criteria evaluation uses **EvaluationOutcome** sealed classes for type-safe result handling:

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

**Key Benefits:**
- **Type Safety**: Compile-time checking ensures proper outcome handling
- **Clear Contracts**: No ambiguity about success vs failure
- **Categorized Failures**: Structured failure reasons with standard categories
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
- Use modern serializers in `common/serializer/` for new development; avoid `application/serializer_deprecated/`.
- Avoid cyclic FSM states.
- Place new entities in `application/entity/` directory.
- Use `@Component` annotation for automatic Spring discovery of workflow components.
