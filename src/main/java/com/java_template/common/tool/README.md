# Cyoda Workflow Validation Tools

This directory contains Java-based validation tools to ensure consistency between your workflow definitions, functional requirements, and Java implementations in your Cyoda application.

## 🔧 Available Tools

### 1. `WorkflowImplementationValidator`
**Purpose**: Validates that all processors/criteria referenced in workflow JSON files exist as actual Java classes.

**What it validates**:
- ✅ All processors referenced in workflows have corresponding Java classes in `application/processor/`
- ✅ All criteria referenced in workflows have corresponding Java classes in `application/criterion/`
- ✅ Handles complex nested criteria structures (AND/OR/NOT conditions, group criteria)
- ✅ Provides detailed reporting of missing implementations

### 2. `FunctionalRequirementsValidator`
**Purpose**: Validates that all processors/criteria mentioned in functional requirements are implemented in the corresponding workflow JSON files.

**What it validates**:
- ✅ All processors mentioned in requirements markdown are defined in workflows
- ✅ All criteria mentioned in requirements markdown are defined in workflows
- ✅ Uses regex patterns to extract processor/criteria names from markdown
- ✅ Handles entity name mapping (e.g., "hnitem" → "HNItem", "bulkupload" → "BulkUpload")

### 3. `WorkflowValidationSuite`
**Purpose**: Comprehensive validation suite that runs both validation tools and provides overall success/failure status.

**Features**:
- ✅ Runs both validators in sequence
- ✅ Provides comprehensive reporting
- ✅ Returns proper exit codes for CI/CD integration
- ✅ Offers individual validation methods for targeted testing

## 🚀 Usage

### Individual Validations

#### Validate All Discovered Files (Default Behavior)

```bash
# Validate all functional requirements are implemented in workflows
./gradlew validateFunctionalRequirements
```

```bash
# Validate all workflow implementations exist as Java classes
./gradlew validateWorkflowImplementations

```

#### Validate Specific Files (Parameterized)
```bash
# Validate a specific workflow file
./gradlew validateWorkflowImplementations -Pargs="src/main/resources/workflow/myentity/version_1/MyEntity.json"

# Validate specific requirement and workflow files
./gradlew validateFunctionalRequirements -Pargs="src/main/resources/functional_requirements/myentity/myentity_workflow.md src/main/resources/workflow/myentity/version_1/MyEntity.json"
```

### Generic File Path Patterns
The tools support generic file path patterns without hardcoded entity names:

**Workflow Implementation Validation:**
- Input: `src/main/resources/workflow/{entity}/version_1/{Entity}.json`
- Validates against: `src/main/java/com/java_template/application/processor/*.java` and `src/main/java/com/java_template/application/criterion/*.java`

**Functional Requirements Validation:**
- Input: `src/main/resources/functional_requirements/{entity}/{entity}_workflow.md` + `src/main/resources/workflow/{entity}/version_1/{Entity}.json`
- Cross-validates requirements documentation against workflow definitions

## 📊 Output Examples

### ✅ Success Output
```
🎉 ALL VALIDATIONS PASSED!
✅ Workflow implementations are complete
✅ Functional requirements are properly implemented

💡 Your Cyoda application is ready for deployment!
```

### ❌ Failure Output
```
❌ Missing processors: SomeProcessor, AnotherProcessor
❌ Missing criteria: SomeCriterion

💡 To fix missing implementations:
   1. Create the missing processor/criterion Java classes
   2. Ensure they implement CyodaProcessor/CyodaCriterion interfaces
   3. Add @Component annotation for Spring registration
```

## 🏗️ Architecture

### Spring Context Integration
All validation tools use Spring's `AnnotationConfigApplicationContext` for dependency injection:
- **ObjectMapper**: For JSON parsing and processing
- **Proper Lifecycle**: Context creation, bean registration, and cleanup
- **Consistent Logging**: Using SLF4J with proper log levels

### File Discovery Patterns
- **Workflow Files**: `src/main/resources/workflow/{entity}/version_1/{Entity}.json`
- **Processor Classes**: `src/main/java/com/java_template/application/processor/*.java`
- **Criterion Classes**: `src/main/java/com/java_template/application/criterion/*.java`
- **Requirements Files**: `src/main/resources/functional_requirements/{entity}/{entity}_workflow.md`

### Entity Name Discovery
The tools automatically discover entity names by:
1. **First Priority**: Reading actual workflow JSON file names (e.g., `HNItem.json` → `HNItem`)
2. **Fallback**: Converting directory names to PascalCase (e.g., `user_profile` → `UserProfile`)

This approach ensures compatibility with any naming convention without hardcoded mappings.

### JSON Processing
- **Nested Criteria Support**: Handles complex workflow structures with AND/OR/NOT conditions
- **Group Criteria**: Processes "conditions" arrays in group criteria
- **Function Criteria**: Extracts processor/criteria names from function definitions
- **Error Handling**: Graceful handling of malformed JSON with detailed error messages

## 🔍 Validation Logic

### Workflow Implementation Validation
1. **Discovery**: Scans workflow directory for JSON files
2. **Parsing**: Extracts processor/criteria names from workflow definitions
3. **Verification**: Checks for corresponding Java class files
4. **Reporting**: Provides detailed success/failure information

### Functional Requirements Validation
1. **Discovery**: Scans requirements directory for markdown files
2. **Extraction**: Uses regex patterns to find processor/criteria names in markdown
3. **Mapping**: Converts entity directory names to proper entity names
4. **Cross-Reference**: Compares requirements against workflow definitions
5. **Reporting**: Shows missing workflow implementations

## 🔧 Integration with Build System

### Gradle Tasks
The validation tools are integrated as Gradle tasks in `build.gradle` with support for parameterized execution:

```gradle
tasks.register('validateWorkflowImplementations', JavaExec) {
    group = 'validation'
    description = 'Validate that all processors/criteria in workflows exist as Java classes. Usage: -Pargs="path/to/workflow.json" (optional)'
    classpath = sourceSets.main.runtimeClasspath
    mainClass.set('com.java_template.common.tool.WorkflowImplementationValidator')
    if (project.hasProperty('args')) {
        args project.property('args').toString().split(' ')
    }
}

tasks.register('validateFunctionalRequirements', JavaExec) {
    group = 'validation'
    description = 'Validate that all processors/criteria in requirements are implemented in workflows. Usage: -Pargs="requirements.md workflow.json" (optional)'
    classpath = sourceSets.main.runtimeClasspath
    mainClass.set('com.java_template.common.tool.FunctionalRequirementsValidator')
    if (project.hasProperty('args')) {
        args project.property('args').toString().split(' ')
    }
}

tasks.register('validateWorkflows', JavaExec) {
    group = 'validation'
    description = 'Run complete workflow validation suite'
    classpath = sourceSets.main.runtimeClasspath
    mainClass.set('com.java_template.common.tool.WorkflowValidationSuite')
}
```

### CI/CD Integration
Add validation to your build pipeline:

```bash
# In your CI/CD script
./gradlew validateWorkflows
if [ $? -ne 0 ]; then
    echo "Workflow validation failed!"
    exit 1
fi

# Continue with build
./gradlew test
./gradlew build
```

## 🐛 Troubleshooting

### Common Issues

**1. "No workflow files found!"**
- Ensure workflow files exist in `src/main/resources/workflow/*/version_1/*.json`
- Check file naming convention matches entity names

**2. "No functional requirement files found!"**
- Ensure markdown files exist in `src/main/resources/functional_requirements/*/*_workflow.md`
- Check directory structure matches expected pattern

**3. "Missing implementations found!"**
- Create missing Java classes in appropriate directories
- Add `@Component` annotation to processor/criterion classes
- Ensure classes implement `CyodaProcessor`/`CyodaCriterion` interfaces

**4. "Missing workflow definitions found!"**
- Add missing processors/criteria to workflow JSON files
- Ensure naming consistency between requirements and workflows
- Verify workflow state transitions include all required components

## 🤝 Development Guidelines

### Adding New Entities
When adding new entities or workflows:

1. **Create the entity**: Add Java classes for processors/criteria
2. **Define workflows**: Create JSON workflow definitions
3. **Document requirements**: Update functional requirements markdown
4. **Validate**: Run `./gradlew validateWorkflows` to ensure consistency
5. **Test**: Run the full test suite to verify functionality

### Extending Validation Logic
To add new validation rules:

1. **Extend existing tools**: Add new validation methods to existing classes
2. **Create new tools**: Follow the same Spring context pattern
3. **Add Gradle tasks**: Register new tools as Gradle tasks
4. **Update documentation**: Document new validation capabilities

## 🎯 Benefits of Parameterized Approach

### Flexibility
- **No Hardcoded Names**: Works with any entity names, not just specific ones like "HNItem" or "BulkUpload"
- **Targeted Validation**: Validate specific files during development without running full suite
- **CI/CD Integration**: Validate only changed files in pull requests for faster feedback

### Scalability
- **Future-Proof**: Automatically works with new entities without code changes
- **Generic Patterns**: Uses file path patterns instead of hardcoded entity lists
- **Dynamic Discovery**: Automatically discovers entities and converts naming conventions

### Development Workflow
```bash
# During development - validate specific entity you're working on
./gradlew validateWorkflowImplementations -Pargs="src/main/resources/workflow/userprofile/version_1/UserProfile.json"

# Before commit - validate all entities
./gradlew validateWorkflows

# In CI/CD - validate specific changed files
./gradlew validateFunctionalRequirements -Pargs="$REQUIREMENTS_FILE $WORKFLOW_FILE"
```

## 📝 Notes

- **Case Sensitivity**: Processor/criteria names are case-sensitive
- **Nested Structures**: Tools handle complex nested criteria automatically
- **Entity Mapping**: Automatic PascalCase conversion of directory names to entity names
- **Error Reporting**: Detailed output for debugging validation issues
- **Spring Integration**: Proper dependency injection and lifecycle management
- **Backward Compatibility**: Default behavior validates all discovered files when no parameters provided
