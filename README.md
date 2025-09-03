# Java Client Template

A structured template for building scalable web clients using **Spring Boot**, designed for seamless integration with **Cyoda** over **gRPC** and **REST**.

## 🚀 **Latest Improvements - EntityService Redesigned**

The EntityService has been completely redesigned with **performance-optimized methods** and **AI-friendly patterns** to eliminate confusion and improve developer experience:

### **Key Improvements:**
- ✅ **Clear Performance Guidance**: Methods labeled FASTEST, MEDIUM, SLOW, SLOWEST
- ✅ **Simplified API**: Removed confusing method overloads and redundant parameters
- ✅ **Consistent Naming**: EntityResponse uses `getEntity()` with Lombok @Data
- ✅ **Clean JSON Structure**: `{"entity": {...}, "metadata": {...}}`
- ✅ **Anti-Pattern Prevention**: Explicit guidance on what NOT to do

### **Quick Start:**
```java
// ✅ FASTEST - Use when you have technical UUID
EntityResponse<Cart> cart = entityService.getById(uuid, Cart.class);

// ✅ MEDIUM - Use for user-facing identifiers
EntityResponse<Cart> cart = entityService.findByBusinessId(Cart.class, "CART-123", "cartId");

// Access entity and metadata
Cart cartEntity = cart.getEntity();  // Lombok-generated getter
UUID technicalId = cart.getMetadata().getId();
String state = cart.getMetadata().getState();
```

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

## ⚙️ EntityService API - Simplified & Performance-Optimized

The EntityService has been redesigned with **clear performance guidance** and **simplified method signatures** to eliminate AI confusion and improve developer experience.

### 🚀 **Primary Methods (Use These)**

| Method | Performance | Use Case | Example |
|--------|-------------|----------|---------|
| `getById(UUID id, Class<T> clazz)` | **FASTEST** | When you have technical UUID | `entityService.getById(uuid, Cart.class)` |
| `findByBusinessId(Class<T> clazz, String businessId, String fieldName)` | **MEDIUM** | User-facing identifiers | `entityService.findByBusinessId(Cart.class, "CART-123", "cartId")` |
| `findAll(Class<T> clazz)` | **SLOW** | Get all entities (use sparingly) | `entityService.findAll(Product.class)` |
| `search(Class<T> clazz, SearchConditionRequest condition)` | **SLOWEST** | Complex queries | `entityService.search(Product.class, condition)` |

### 💾 **Mutation Methods**

| Method | Description | Example |
|--------|-------------|---------|
| `save(T entity)` | Create new entity | `entityService.save(newCart)` |
| `update(UUID id, T entity, String transition)` | Update by technical ID | `entityService.update(uuid, cart, "CHECKOUT")` |
| `updateByBusinessId(T entity, String fieldName, String transition)` | Update by business ID | `entityService.updateByBusinessId(cart, "cartId", null)` |
| `deleteById(UUID id)` | Delete by technical ID | `entityService.deleteById(uuid)` |
| `deleteByBusinessId(Class<T> clazz, String businessId, String fieldName)` | Delete by business ID | `entityService.deleteByBusinessId(Cart.class, "CART-123", "cartId")` |

### 🎯 **Method Selection Guide**

```java
// ✅ FASTEST - Use when you have the technical UUID (from previous EntityResponse)
EntityResponse<Cart> cart = entityService.getById(technicalId, Cart.class);

// ✅ MEDIUM SPEED - Use for user-facing identifiers (cartId, paymentId, orderId, etc.)
EntityResponse<Cart> cart = entityService.findByBusinessId(Cart.class, "CART-123", "cartId");

// ⚠️ SLOW - Use sparingly for small datasets
List<EntityResponse<Product>> allProducts = entityService.findAll(Product.class);

// ⚠️ SLOWEST - Use for complex filtering/search
SearchConditionRequest condition = SearchConditionRequest.group("AND",
    Condition.of("$.price", "GREATER_THAN", 100));
List<EntityResponse<Product>> expensiveProducts = entityService.search(Product.class, condition);
```

### 🔑 EntityResponse<T> Structure

All EntityService methods return `EntityResponse<T>` which contains both the entity and metadata:

```java
EntityResponse<Cart> response = entityService.getById(cartId, Cart.class);

// Access the entity (Lombok @Data generates getEntity() automatically)
Cart cart = response.getEntity();

// Access metadata (includes technical UUID and workflow state)
UUID technicalId = response.getMetadata().getId();      // Use for subsequent operations
String workflowState = response.getMetadata().getState(); // Current workflow state
LocalDateTime created = response.getMetadata().getCreatedAt();
LocalDateTime updated = response.getMetadata().getUpdatedAt();
```

**JSON Structure:**
```json
{
  "entity": {
    "cartId": "CART-123",
    "items": [...],
    "total": 99.99
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "ACTIVE"
  }
}
```

### 💡 **Performance Tips**

- **Store Technical UUIDs**: Save `response.getMetadata().getId()` for future operations
- **Use Business IDs for APIs**: Expose user-friendly IDs (cartId, orderId) in REST endpoints
- **Cache Frequently Used Data**: Consider caching results from `findAll()` operations
- **Batch Operations**: Use `saveAll()` for multiple entities when possible

### 🔄 **Unified Processor Interface**

Processors support both traditional entity processing and the new unified EntityResponse interface:

```java
// Traditional entity processing
return serializer.withRequest(request)
    .toEntity(EntityName.class)
    .validate(this::isValidEntity, "Invalid entity")
    .map(this::processEntityLogic)
    .complete();

// NEW: Unified EntityResponse processing (recommended for consistency)
return serializer.withRequest(request)
    .toEntityResponse(EntityName.class)  // Same interface as controllers
    .validate(this::isValidEntityResponse, "Invalid entity response")
    .map(this::processEntityResponseLogic)
    .complete();

private EntityResponse<EntityName> processEntityResponseLogic(ProcessorEntityResponseExecutionContext<EntityName> context) {
    EntityResponse<EntityName> entityResponse = context.entityResponse();
    EntityName entity = entityResponse.getEntity();

    // Direct access to metadata (unified with controllers)
    UUID currentEntityId = entityResponse.getMetadata().getId();
    String currentState = entityResponse.getMetadata().getState();

    // Process business logic
    return entityResponse; // Cannot modify current entity state
}
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

### Processor Architecture - Simplified & Clean

Processors implement the `CyodaProcessor` interface with **minimal dependencies** and follow the **"Entity as Payload"** principle:

```java
@Component
public class AddLastModifiedTimestamp implements CyodaProcessor {
    private final ProcessorSerializer serializer;
    // Optional: Only inject EntityService if you need to interact with other entities
    private final EntityService entityService;

    // Constructor WITHOUT EntityService (for simple processors)
    public AddLastModifiedTimestamp(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    // Constructor WITH EntityService (only if needed for other entity interactions)
    public AddLastModifiedTimestamp(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(pet -> pet.getId() != null && pet.getName() != null)
            .map(this::processBusinessLogic)
            .complete();
    }

    private Pet processBusinessLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();

        // CRITICAL: The entity already contains all the data you need
        // Never extract from request payload - use entity getters directly

        // If you need to interact with other entities, use EntityService:
        // EntityResponse<Owner> ownerResponse = entityService.getById(pet.getOwnerId(), Owner.class);
        // Owner owner = ownerResponse.getEntity();

        pet.addLastModifiedTimestamp();
        return pet;
    }

    @Override
    public boolean supports(OperationSpecification modelKey) {
        return "example_function_name".equals(modelKey.operationName());
    }
}
```

### 🚫 **Processor Anti-Patterns to Avoid**

```java
// ❌ DON'T: Extract from payload manually
Map<String, Object> payloadMap = objectMapper.convertValue(request.getPayload().getData(), Map.class);

// ❌ DON'T: Inject ObjectMapper in processors
public ProcessorName(SerializerFactory factory, ObjectMapper objectMapper) { ... }

// ❌ DON'T: Manual entity reconstruction
Pet pet = new Pet();
pet.setName((String) payloadMap.get("name"));

// ❌ DON'T: Try to update current entity in processor
EntityResponse<Pet> updated = entityService.update(currentEntityId, pet, "TRANSITION");

// ✅ DO: Use entity directly
Pet pet = context.entity();
String name = pet.getName();

// ✅ DO: Use EntityService for OTHER entities only
EntityResponse<Owner> ownerResponse = entityService.getById(ownerId, Owner.class);
Owner owner = ownerResponse.getEntity();
EntityResponse<Owner> updated = entityService.update(ownerId, owner, "TRANSITION");
```

### 🚨 **Critical Processor Limitations**

**IMPORTANT**: Processors have specific limitations on what they can and cannot do:

- ✅ **CAN**: Read current entity data using `context.entity()` or `entityResponse.getEntity()`
- ✅ **CAN**: Access current entity metadata: `context.getEvent().getEntityId()` and state
- ✅ **CAN**: Get, update, delete OTHER entities using EntityService
- ❌ **CANNOT**: Update the current entity being processed
- ❌ **CANNOT**: Change the current entity's workflow state
- ❌ **CANNOT**: Use Java reflection - only entity getters/setters
- ❌ **CANNOT**: Modify code in `src/main/java/com/java_template/common` directory

### 🚫 **Anti-Patterns to Avoid**

```java
// ❌ DON'T: Extract from payload manually
Map<String, Object> payloadMap = objectMapper.convertValue(request.getPayload().getData(), Map.class);

// ❌ DON'T: Inject ObjectMapper in processors
public AddLastModifiedTimestamp(SerializerFactory factory, ObjectMapper objectMapper) { ... }

// ❌ DON'T: Manual entity reconstruction
Pet pet = new Pet();
pet.setName((String) payloadMap.get("name"));

// ✅ DO: Use entity directly
Pet pet = context.entity();
String name = pet.getName();
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
