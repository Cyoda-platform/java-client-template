# Java Client Template

A **Gradle project** using **Spring Boot** with **Cyoda integration** for building scalable web clients with workflow-driven backend interactions.


## ⚠️ Getting Started

> ☕ **Java 21 Required**
> Make sure Java 21 is installed and set as the active version.

### 1. Clone the Project

```bash
git clone https://github.com/Cyoda-platform/java-client-template.git
cd java-client-template
```

### 2. ⚙️ Configure the Application

Configuration is managed via Spring Boot YAML files. For local development:

```bash
# Option 1: Create a local profile
# For example, create src/main/resources/application-local.yml with your settings
./gradlew runApp --args='--spring.profiles.active=local'

# Option 2: Use environment variables
export APP_CONFIG_CYODA_HOST=your-cyoda-host:8443
export APP_CONFIG_CYODA_CLIENT_ID=your-client-id
export APP_CONFIG_CYODA_CLIENT_SECRET=your-client-secret
```

### 3. 🧭 Run Workflow Import Tool

#### Option 1: Run via Gradle (recommended for local development)
```bash
./gradlew runApp -PmainClass=com.java_template.common.tool.WorkflowImportTool --args='--spring.profiles.active=local'
```

#### Option 2: Build and Run JAR (recommended for CI or scripting)
```bash
./gradlew bootJarWorkflowImport
java -jar build/libs/java-client-template-1.0-SNAPSHOT-workflow-import.jar --spring.profiles.active=local
```

### 4. ▶️ Run the Application

#### Option 1: Run via Gradle
```bash
./gradlew runApp --args='--spring.profiles.active=local'
```

#### Option 2: Run Manually After Build
```bash
./gradlew build
java -jar build/libs/java-client-template-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

> Access the app: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
>
> **Note**: The default port is 8080 as configured in `src/main/resources/application.yml`. You can change this by setting the `server.port` property.

---

## 🗂 Project Structure

This template follows a clear separation between **framework code** (that you don't modify) and **application code** (where you implement your business logic).

### `src/main/java/com/java_template/common/` - Framework Code (DO NOT MODIFY)

**Core Framework Components:**
- `auth/` – Authentication & token management for Cyoda integration
- `config/` – Configuration classes using Spring Boot's configuration management
- `dto/` – Data transfer objects including `EntityWithMetadata<T>` wrapper
- `grpc/` – gRPC client integration with Cyoda platform
- `repository/` – Data access layer for Cyoda REST API operations
- `service/` – `EntityService` interface and implementation for all Cyoda operations
- `serializer/` – Serialization framework with fluent APIs (`ProcessorSerializer`, `CriterionSerializer`)
- `tool/` – Utility tools like `WorkflowImportTool` for importing workflow configurations
- `util/` – Various utility functions and helpers
- `workflow/` – Core interfaces: `CyodaEntity`, `CyodaProcessor`, `CyodaCriterion`

> ⚠️ **IMPORTANT**: There is no need to modify anything in the `common/` directory. This is the framework code that provides all Cyoda integration.

### `src/main/java/com/java_template/application/` - Your Business Logic (CREATE AS NEEDED)

**Your Implementation Areas:**
- `controller/` – REST endpoints and HTTP API controllers
- `entity/` – Domain entities implementing `CyodaEntity` interface
- `processor/` – Workflow processors implementing `CyodaProcessor` interface
- `criterion/` – Workflow criteria implementing `CyodaCriterion` interface

## 🔑 Core Concepts

### What is a CyodaEntity?
Domain objects that represent your business data. Must implement `CyodaEntity` interface and be placed in `application/entity/` directory.

### What is a CyodaProcessor?
Workflow components that handle business logic and entity transformations. **Critical limitation**: Cannot update the current entity being processed via EntityService.

### What is a CyodaCriterion?
Pure functions that evaluate conditions without side effects. Must not modify entities or have side effects.

### EntityWithMetadata<T> Pattern
Unified wrapper that includes both entity data and technical metadata (UUID, state, etc.). Used consistently across controllers, processors, and criteria.

## 🔧 Workflow Configuration

Workflows are defined using **finite-state machine (FSM)** JSON files placed in:
```
src/main/resources/workflow/$entity_name/version_$version/$entity_name.json
```

### Workflow Schema Reference
The workflow configuration schema is defined in:
```
src/main/resources/schema/common/statemachine/conf/WorkflowConfiguration.json
```
This schema defines the structure for workflow definitions, including states, transitions, processors, and criteria.

### Key Concepts
- **States and Transitions**: Define the workflow flow
- **Processors**: Handle business logic during transitions
- **Criteria**: Evaluate conditions to determine transition paths
- **Automatic Discovery**: Components are found via Spring `@Component` annotation

## 📚 Documentation and Examples

### Code Examples
- **`src/test/java/com/example/application/`** - Complete implementation examples for all components
  - `controller/` - REST controller patterns
  - `entity/` - Entity class implementations
  - `processor/` - Workflow processor examples  
  - `criterion/` - Workflow criteria examples  
  - `patterns/` - Comprehensive patterns and anti-patterns guide

### Configuration Examples  
- **`llm_example/config/`** - Configuration templates and examples
  - `workflow/` - Workflow JSON configuration templates

### Documentation Files
- **`README.md`** - Complete project documentation (this file)
- **`CONTRIBUTING.md`** - Contributors guide and validation workflow
- **`usage-rules.md`** - Developer and AI agent guidelines
- **`.augment-guidelines`** - Project overview and development workflow
- **`llms.txt`** / **`llms-full.txt`** - AI-friendly documentation references

## 📝 Quick Reference

### Key Concepts
- **Framework Code** (`common/`) - Never modify, provides all Cyoda integration
- **Application Code** (`application/`) - Your business logic implementation area
- **EntityWithMetadata<T>** - Unified wrapper pattern for all entity operations
- **EntityService** - Single interface for all Cyoda data operations

### Implementation Checklist
- ✅ Entities implement `CyodaEntity` with `getModelKey()` and `isValid()`
- ✅ Processors implement `CyodaProcessor` with `process()` and `supports()`
- ✅ Criteria implement `CyodaCriterion` with `check()` and `supports()`
- ✅ Use `@Component` annotation for Spring discovery
- ✅ Place workflow JSON files in `src/main/resources/workflow/$entity_name/version_$version/`
- ✅ Always reference `llm_example/` for implementation patterns

### Critical Limitations
- ❌ Never modify anything in `common/` directory
- ❌ Processors cannot update the current entity being processed
- ❌ Criteria must be pure functions without side effects
- ❌ No Java reflection usage allowed

> 📚 **See `llm_example/` directory for complete implementation examples, patterns, and configuration templates**

## 🚀 Getting Started

1. **Review Examples**: Start by exploring `llm_example/code/` for implementation patterns
2. **Create Entities**: Implement `CyodaEntity` in `application/entity/`
3. **Add Processors**: Implement `CyodaProcessor` in `application/processor/`
4. **Add Criteria**: Implement `CyodaCriterion` in `application/criterion/`
5. **Configure Workflows**: Create JSON files in `src/main/resources/workflow/`
6. **Build Controllers**: Create REST endpoints in `application/controller/`

## Entity Search (items/search)

The application provides a `/items/search` endpoint for searching Hacker News items using the Cyoda entity search API.

### Configuration

Set the following environment variable to enable authorization:

```bash
export CYODA_API_TOKEN=your-api-token-here
```

Or add to `.env` file:

```bash
CYODA_API_TOKEN=your-api-token-here
```

### API Usage

#### Basic Search (POST)
```bash
curl -X POST "http://localhost:8080/api/items/search" \
  -H "Content-Type: application/json" \
  -d '{"q":"rust","type":"story","limit":50,"offset":0}'
```

#### Search with Type Filter (POST)
```bash
curl -X POST "http://localhost:8080/api/items/search" \
  -H "Content-Type: application/json" \
  -d '{"q":"rust","type":"story"}'
```

#### Search with Pagination (POST)
```bash
curl -X POST "http://localhost:8080/api/items/search" \
  -H "Content-Type: application/json" \
  -d '{"q":"rust","limit":50,"offset":10}'
```

#### Full Example with All Parameters (POST)
```bash
curl -X POST "http://localhost:8080/api/items/search" \
  -H "Content-Type: application/json" \
  -d '{"q":"rust","type":"story","limit":50,"offset":0}'
```

### Request Body

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `q` | string | Yes | - | Search query string |
| `type` | string | No | - | Entity type to filter results (e.g., "story", "comment") |
| `limit` | integer | No | 20 | Maximum number of results |
| `offset` | integer | No | 0 | Pagination offset |

***

Updated README with POST-based examples for /items/search.
