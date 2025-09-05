# Code Comments Rules

> **SCOPE**: Apply these rules anytime you write comments in code.

**Description**: Standards for writing meaningful, maintainable code comments that enhance code understanding and preserve important documentation.

## Comment Purpose and Focus

- **"Why" Over "What"**: Write comments when the "why" behind the code is not obvious
- **Intent and Reasoning**: Focus comments on intent, reasoning, or trade-offs rather than describing what the code does
- **Let Code Speak**: Allow the code itself to convey the "what" through clear naming and structure
- **Business Logic**: Comment complex business rules, algorithms, or domain-specific logic that may not be immediately clear

## File Documentation Headers

- **Mandatory Headers**: All code files must have at least one documentation header with a brief 2-line comment explaining what the file does
- **ABOUTME Format**: The comment must start with the string "ABOUTME: " to make it easy to grep for
- **Concise Description**: Keep file descriptions brief but informative about the file's primary purpose and responsibility
- **Example Format**:
  ```java
  /**
   * ABOUTME: This file contains the main workflow component that handles
   * state machine lifecycle and orchestrates processor execution.
   */
  ```

## Debug and Trace Logging as Comments

- **Logging as Documentation**: Where it makes sense, use debug or trace logging as a form of commenting on the "what"
- **Runtime Documentation**: Logging can serve as executable comments that document program flow and state changes
- **Debugging Aid**: Strategic logging helps future developers understand code execution paths and data transformations

## Comment Preservation and Maintenance

- **NEVER Remove Comments**: NEVER remove code comments unless you can prove that they are actively false
- **Important Documentation**: Comments are important documentation and should be preserved even if they seem redundant or unnecessary
- **Verification Required**: Only remove comments when you can definitively prove they contain incorrect information
- **Err on Preservation**: When in doubt, preserve existing comments rather than removing them

## Evergreen Comment Standards

- **No Temporal References**: Do not refer to temporal context about refactors or recent changes in comments
- **Describe Current State**: Comments should describe the code as it is, not how it evolved or was recently changed
- **Avoid Historical Context**: Remove references to "recently added", "temporary fix", "TODO: refactor later", etc.
- **Timeless Documentation**: Write comments that remain relevant regardless of when they are read

## Comment Quality Guidelines

- **Clear and Concise**: Write comments that are easy to understand and get to the point quickly
- **Accurate Information**: Ensure comments accurately reflect the current code behavior
- **Proper Grammar**: Use proper grammar and spelling in comments for professionalism
- **Consistent Style**: Maintain consistent commenting style throughout the codebase
