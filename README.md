# Java Client Template

A **Gradle project** using **Spring Boot** with **Cyoda integration** for building scalable web clients with workflow-driven backend interactions.

## 🏗️ Project Structure

This template follows a clear separation between **framework code** (that you don't modify) and **application code** (where you implement your business logic).

### `src/main/java/com/java_template/common/` - Framework Code (DO NOT MODIFY)

**Core Framework Components:**
- `auth/` – Authentication & token management for Cyoda integration
- `config/` – Configuration classes, constants, and environment variable handling  
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

## 🔄 Workflow Configuration

Workflows are defined using **finite-state machine (FSM)** JSON files placed in:
```
src/main/resources/workflow/$entity_name/version_$version/$entity_name.json
```

### Key Concepts
- **States and Transitions**: Define the workflow flow
- **Processors**: Handle business logic during transitions  
- **Criteria**: Evaluate conditions to determine transition paths
- **Automatic Discovery**: Components are found via Spring `@Component` annotation

## 📚 Documentation and Examples

### Code Examples
- **`llm_example/code/application/`** - Complete implementation examples for all components
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

## 🔧 Development Workflow

1. Review `llm_example/` directory for patterns before implementing new features
2. Follow established architectural patterns for processors, criteria, and serializers
3. Use `usage-rules.md` for detailed implementation guidelines
4. Run `./gradlew build` to generate required classes before development

**For Contributors:**

- See `CONTRIBUTING.md` for detailed guidelines

## Package Management

Always use appropriate package managers for dependency management:

1. **Use package managers** for all dependency operations instead of manually editing configuration files
2. **Exception**: Only edit package files directly for complex configurations that cannot be accomplished through package manager commands
3. **Generated Classes**: Ensure `build/generated-sources/js2p/org/cyoda/cloud/api/event` classes are available via `./gradlew build`
4. **Communication**: Use generated classes for all Cyoda integration
