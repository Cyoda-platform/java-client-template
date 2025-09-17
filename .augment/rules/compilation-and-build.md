# Compilation and Build Lifecycle Rules

**Scope**: When performing compilation, testing, or other build lifecycle actions

## Required Checks Before Build Operations

### 1. Build System Detection
**MUST** determine the build system:
- **Maven**: Look for `pom.xml` in root directory
- **Gradle**: Look for `build.gradle`, `build.gradle.kts`, or `gradlew` in root directory

### 2. Project Structure Analysis
**MUST** determine if the project is single-module or multi-module:

**Maven Multi-Module**: Root `pom.xml` contains `<modules>` section
**Gradle Multi-Module**: Contains `settings.gradle` with `include` statements

## Critical Rule for Multi-Module Maven Projects

**MUST** use the `-am` (also-make) switch when targeting specific modules:

```bash
# Test specific module with dependencies
# Run this once, in case the module needs to be rebuilt
mvn install -pl module-name -am -DskipTests
# Run tests.
mvn test -Dtest=TestClass#method -pl module-name


# Compile specific module with dependencies
mvn compile -pl module-name -am
```

**Key Points**:
- The `-am` switch ensures dependencies are built first, preventing compilation failures
- Test execution automatically handles compilation, so separate compilation steps are usually unnecessary
- Use `-Dsurefire.failIfNoSpecifiedTests=false` when running specific tests to avoid failures in modules that don't contain the test

## Anti-Patterns to Avoid

- **NEVER** assume build system without checking
- **NEVER** run Maven commands on specific modules without `-am` in multi-module projects
- **NEVER** mix Maven and Gradle commands in the same project
- **NEVER** run separate compilation steps when test execution will handle it automatically
