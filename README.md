# Java Client Template

A structured template for building scalable web clients using **Spring Boot**, designed for seamless integration with **Cyoda** over **gRPC** and **REST**.

---

## 🚀 Features

- **Spring Boot**-based fast backend starter.
- Modular, extensible structure for rapid iteration.
- Built-in support for **gRPC** and **REST** APIs.
- Integration with **Cyoda**: workflow-driven backend interactions.

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
- `workflow/` – Registrar and dispatcher for `Workflow.java` methods.

To interact with **Cyoda**, use `common/service/EntityService.java`, which provides all necessary methods.

To add new integrations with Cyoda, extend the following files:

- **Interface** — `common/service/EntityService.java`: defines service methods; abstraction layer for Cyoda. Optional to modify.
- **Implementation** — `common/service/EntityServiceImpl.java`: implements interface methods and business logic. Optional to modify.
- **Repository Interface** — `common/repository/CrudRepository.java`: defines a generic interface. Modify only if new operations are needed.
- **Cyoda Repository** — `common/repository/CyodaRepository.java`: implements repository methods. Modify only if needed.

> ⚠️ `CrudRepository.java` and `CyodaRepository.java` rarely change — only for significant updates to the data access layer.

✅ Always interact with the **service interface**, not directly with the repository.

---

### `entity/`
Domain logic:

- `functional_requirements.md` – Describes the application’s functional requirements.
- `$entity_name/Workflow.java` – FSM event dispatcher.
- `$entity_name/Workflow.json` – Workflow configuration.

### `controller/`
Handles HTTP endpoints. Based on `Controller.java`.

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
entity/$entity_name/Workflow.json
```

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

The logic for processing workflows is implemented in `entity/$entity_name/Workflow.java`.


Action / processor example:

```java
public CompletableFuture<ObjectNode> processTemplateEntity(ObjectNode entity) {
  fetchedData = fetchDataForTemplateEntity(entity);
  entity.put("fetchedData", fetchedData);
  return CompletableFuture.completedFuture(entity);
}
```

Function condition example:

```java
public CompletableFuture<ObjectNode> isTemplateValid(ObjectNode entity) {
  boolean isValid = checkCondition(entity);
  entity.put("success", isValid);
  return CompletableFuture.completedFuture(entity);
}
```

### ⚙️ Registration mechanism

Workflow methods are **discovered and registered lazily** at runtime via the `WorkflowRegistrar` in `common/workflow`,  
triggered by the first call to `processEvent(...)` in the `WorkflowProcessor`.

Here’s how it works:

1. When the first gRPC event (`EntityProcessorCalculationRequest` for `action.name` or `EntityCriteriaCalculationRequest` for `condition.function.name`) arrives,  
   the `WorkflowProcessor` initializes and delegates registration to `WorkflowRegistrar`.

2. `WorkflowRegistrar` scans all Spring beans whose class name ends with `workflow`.

3. It collects all declared methods with the following signature:
   ```java
   Function<ObjectNode, CompletableFuture<ObjectNode>>
   ```

4. Each method is registered in the `WorkflowProcessor` under its method name (e.g. `processTemplateEntity`, `isTemplateValid`).

5. Upon subsequent gRPC events, the processor invokes the corresponding method by name, passing the `ObjectNode` entity as input.

> ✅ The method name must **exactly match** the value of `action.name` or `condition.function.name` used in the workflow configuration.

---

## 📝 Notes

- Entity `id` is a **UUID**.
- Keep workflow method names unique and aligned with JSON actions and function conditions.
- Avoid cyclic FSM states.
