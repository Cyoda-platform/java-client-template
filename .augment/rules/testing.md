# Testing Rules

> **SCOPE**: Apply these rules anytime you write or launch tests.

Comprehensive testing standards and practices that must be followed for all code changes, bug fixes, and feature implementations.

## Naming Tests
We distinguish between two test types:

### Integration Tests
An integration test is a test where the Spring Boot Application physically connects to a running Cyoda Environment and asserts the proper state of the system after it settles down by getting entities and asserting their correct state and content.

Integration Test classes must end with `IT`

### Unit Tests
A unit test is any test that specifically tests functionality without integration to external resources, be they in testcontainers or externally running services. They can run standalone without any dependecies on external resources or services.

Unit Test classes must end with `Test`

## Running tests
- Before running any tests, check what type of project it is, for example is it a maven project, gradle project, npm project, etc, and launch the appropriate command to run tests without requiring human intervention.
- It must be possible to easily run all unit tests separately from integration tests.

### Running Specific Tests
When running specific tests, use the correct command for the project structure. For example, for Maven projects, consider using the following command:

```bash
# For multi-module Maven projects (use -am for dependencies)
mvn test -Dtest=TestClass#method -pl module-name -am -Dsurefire.failIfNoSpecifiedTests=false

# For single-module projects
mvn test -Dtest=TestClass#method
```

**Key Points**:
- Use `-am` flag for multi-module Maven projects to ensure dependencies are built
- Use `-Dsurefire.failIfNoSpecifiedTests=false` to prevent failures when other modules don't contain the test
- The test execution automatically handles compilation, so separate compilation steps are usually unnecessary
- For tests that generate artifacts: generated output is the source of truth, not manually created content

## Test-Driven Development (TDD)

- **Red-Green-Refactor Cycle**: Follow the TDD cycle for all new features and improvements:
    1. **Red**: Write a failing test that defines the desired function or improvement
    2. **Green**: Write minimal code to make the test pass
    3. **Refactor**: Improve code design while keeping tests green
    4. **Repeat**: Continue the cycle for each new feature or bugfix

- **Test-First Approach**: Write tests before writing implementation code
- **Minimal Implementation**: Only write enough code to make the failing test pass
- **Continuous Refactoring**: Refactor code continuously while ensuring tests still pass

## Bug Fixing and Reproduction

- **Reproducer Tests Required**: When fixing bugs, write a test that reproduces the bug first
- **Bug Fix Validation**: The reproducer test must succeed when the bug is fixed
- **Pre-Fix Testing**: Bug fixing should be preceded by a reproducer test if one does not yet exist
- **Regression Prevention**: Ensure bug fix tests prevent future regressions

## Test Coverage and Quality Standards

- **Mandatory Coverage**: Tests MUST cover all functionality being implemented
- **No Exceptions Policy**: Under no circumstances should any test type be marked as "not applicable"
- **Required Test Types**: Every project MUST have:
    - Unit tests
    - Integration tests
    - End-to-end tests
- **Authorization Override**: Only skip tests if human explicitly states: "I AUTHORIZE YOU TO SKIP WRITING TESTS THIS TIME"

## Test Design Principles

- **Robustness**: Design tests for robustness and avoid writing fragile tests
- **Clarity**: Tests should be clear and easy to understand
- **Quality Standards**: Apply the same quality standards to test code as production code
- **Avoid Code Bloat**: Keep test code clean and avoid unnecessary complexity
- **Maintainability**: Write tests that are easy to maintain and update

## Test Output and Validation

- **Pristine Output Required**: Test output MUST be pristine to pass - no ignored warnings or errors
- **Critical Information**: NEVER ignore system output or test logs - they often contain CRITICAL information
- **Error Testing**: If logs are supposed to contain errors, capture and test them explicitly
- **Output Validation**: Validate all expected outputs, including error conditions and edge cases

## Test Execution and Monitoring

- **Confirmation Testing**: Always run tests to confirm they fail as expected before implementation
- **Success Validation**: Run tests after implementation to confirm success
- **Continuous Monitoring**: Monitor test results and address any failures immediately
- **Log Analysis**: Analyze test logs and system messages for insights and potential issues
