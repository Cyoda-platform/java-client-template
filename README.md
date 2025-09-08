# Java Client Template

A structured template for building scalable web clients using **Spring Boot**, designed for seamless integration with **Cyoda** over **gRPC** and **REST**.

## 🚀 **Core Features**

This template provides a production-ready foundation with **performance-optimized EntityService** and **type-safe processing chains** for building scalable applications:

### **Key Features:**
- ✅ **Performance-Optimized EntityService**: Methods labeled FASTEST, MEDIUM, SLOW, SLOWEST for clear guidance
- ✅ **Type-Safe Processing**: EntityWithMetadata-based fluent API with compile-time safety
- ✅ **Unified Interface**: Consistent patterns between processors and controllers
- ✅ **Clean JSON Structure**: `{"entity": {...}, "metadata": {...}}`
- ✅ **Exception Propagation**: Proper error handling without swallowing exceptions

### **Quick Start:**

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
git clone https://github.com/Cyoda-platform/java-client-template.git
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
- `dto/` – Data transfer objects including EntityWithMetadata wrapper.
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
Application-specific logic and components (currently empty - create subdirectories as needed):

- `controller/` – HTTP endpoints and REST API controllers (create when needed).
- `entity/` – Domain entities that implement `CyodaEntity` (create when needed).
- `processor/` – Workflow processors that implement `CyodaProcessor` interface (create when needed).
- `criterion/` – Workflow criteria that implement `CyodaCriterion` interface (create when needed).

### Entity Structure
When creating entities, organize them as follows:

- `application/entity/$entity_name/` – Directory for each entity type
- `application/entity/$entity_name/EntityName.java` – Entity class implementing `CyodaEntity`
- `src/main/resources/workflow/$entity_name/version_$version/$entity_name.json` – Workflow configuration for the entity

---

### 💡 **Performance Tips**

- **Store Technical UUIDs**: Save `response.getMetadata().getId()` for future operations
- **Use Business IDs for APIs**: Expose user-friendly IDs (cartId, orderId) in REST endpoints
- **Cache Frequently Used Data**: Consider caching results from `findAll()` operations
- **Batch Operations**: Use `save(Collection<T>)` for multiple entities when possible
- **Prefer Technical IDs**: Use `getById()` over `findByBusinessId()` when you have the UUID
- **Limit Search Scope**: Use specific search conditions rather than `findAll()` when possible

### 🔄 **Unified Processor Interface**

## 🔄 Workflow Configuration

### Workflow File Location

For the WorkflowImportTool to find and import workflows, place them in:
```
src/main/resources/workflow/$entity_name/version_$version/$entity_name.json
```

Example structure:
```
src/main/resources/workflow/
├── Pet/
│   └── version_1/
│       └── Pet.json
└── Order/
    └── version_1/
        └── Order.json
```

> **Note**: The WorkflowImportTool scans `src/main/resources/workflow/` directory and expects version directories (e.g., `version_1`) to determine entity versions.

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

### 📄 **Workflow Configuration Examples**

Complete workflow configuration examples are available in `example_code/resources/workflow/` including:
- Basic FSM structure with states and transitions
- Processor configuration with execution modes
- Criterion configuration (simple, group, function types)
- Complex workflow patterns with multiple transitions

See `example_code/resources/workflow/` for complete workflow JSON examples and templates.

### ✅ Criterion Types

There are **three types of criteria** used to control transitions:

1. **Simple criterion** — Direct field comparison using jsonPath and operations
2. **Group criterion** — Logical combination of conditions with AND/OR/NOT operators (supports nesting)
3. **Function criterion** — Custom client-side evaluation via CyodaCriterion components

### 📄 **Criterion Configuration Examples**

Complete criterion configuration examples are available in `example_code/resources/workflow/` including:
- Simple criterion with jsonPath field references
- Group criterion with nested conditions and logical operators
- Function criterion with custom CyodaCriterion component integration
- Complex criterion patterns with multiple evaluation levels

See `example_code/resources/workflow/` for complete criterion JSON examples and templates.

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

### 🚨 **Critical Processor Limitations**

**IMPORTANT**: Processors have specific limitations on what they can and cannot do:

- ✅ **CAN**: Read current entity data using `context.entity()` or `entityWithMetadata.getEntity()`
- ✅ **CAN**: Access current entity metadata: `context.getEvent().getEntityId()` and state
- ✅ **CAN**: Get, update, delete OTHER entities using EntityService
- ❌ **CANNOT**: Update the current entity being processed
- ❌ **CANNOT**: Change the current entity's workflow state
- ❌ **CANNOT**: Use Java reflection - only entity getters/setters
- ❌ **CANNOT**: Modify code in `src/main/java/com/java_template/common` directory

### Processor Configuration

Processors in workflow transitions support various execution modes and configuration options:

**Execution Modes:**
- `SYNC` - Synchronous execution (default)
- `ASYNC_SAME_TX` - Asynchronous execution in the same transaction
- `ASYNC_NEW_TX` - Asynchronous execution in new transaction

**Configuration Options:**
- `attachEntity` - Whether to attach entity data to the request
- `calculationNodesTags` - Tags for calculation node selection
- `responseTimeoutMs` - Response timeout in milliseconds
- `retryPolicy` - Retry policy for failed executions

### 📄 **Processor Configuration Examples**

Complete processor configuration examples are available in `example_code/resources/workflow/` including:
- Execution mode configurations (SYNC, ASYNC_SAME_TX, ASYNC_NEW_TX)
- Configuration options for entity attachment and timeouts
- Retry policy and calculation node tag examples
- Complex processor chains and error handling patterns

See `example_code/resources/workflow/` for complete processor JSON examples and templates.
### Criteria Implementation

Criteria implement the `CyodaCriterion` interface for condition checking using **EvaluationOutcome** sealed classes with **logical chaining**:

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

## 📝 Notes

- Entity `id` type varies by entity (e.g., Pet entity uses `Long`).
- Use `CyodaProcessor` and `CyodaCriterion` interfaces for workflow components.
- Leverage the **EntityProcessingChain** API for unified, type-safe entity processing.
- Component operation names are matched via the `supports()` method against workflow configuration.
- Use serializers in `common/serializer/` for development.
- Avoid cyclic FSM states.
- Place entities in `application/entity/` directory.
- Use `@Component` annotation for automatic Spring discovery of workflow components.
