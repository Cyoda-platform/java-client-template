---
type: "always_apply"
---

# Coding Practices Rules

> **SCOPE**: Apply these rules anytime you write code.

Essential coding practices and constraints that govern how code changes should be made, emphasizing maintainability, incremental changes, and proper boundaries.

## Standards
- When catching exceptions, either log and handle the exception or throw it without logging. Never do both.
- 
## Version Control and Deployment

- **NEVER COMMIT CODE**: Do not commit code changes - the human handles all commits
- **Focus on Implementation**: Assume the human will review and commit your changes
- When moving files, use "git mv", so that these do not appear as deleted and created files.

## Code Philosophy and Approach

- **Simplicity Over Cleverness**: Prefer simple, clean, maintainable solutions over clever or complex ones
- **Readability First**: Prioritize readability and maintainability over conciseness or performance optimizations
- **Maintainable Solutions**: Choose solutions that future developers can easily understand and modify
- **Clear Intent**: Write code that clearly expresses its intent and purpose

## Change Management and Scope

- **Minimal Changes**: Make the smallest reasonable changes to achieve the desired outcome
- **No Reimplementation**: MUST ask permission before reimplementing features or systems from scratch instead of updating existing implementation
- **Task Boundaries**: NEVER make code changes that aren't directly related to the current assigned task
- **Unrelated Issues**: If you notice something that should be fixed but is unrelated to the current task, report it instead of fixing it immediately
- **Green Build** Maintain green builds and adjust tests when changes break existing test suites.

## Code Preservation and Safety

- **NEVER Throw Away Code**: When fixing bugs, compilation errors, or other issues, NEVER throw away the old implementation and rewrite without explicit permission
- **Explicit Permission Required**: If considering a complete rewrite, MUST STOP and get explicit permission first
- **Incremental Fixes**: Prefer incremental fixes and improvements over wholesale replacements
- **Preserve Working Code**: Maintain existing functionality while making necessary changes

## Style and Consistency

- **Match Surrounding Style**: When modifying code, match the style and formatting of surrounding code
- **Local Consistency**: Consistency within a file is more important than strict adherence to external standards
- **Existing Patterns**: Follow established patterns and conventions already present in the codebase
- **Contextual Adaptation**: Adapt to the existing code style rather than imposing external style guides

## Naming Conventions

- **Evergreen Names**: NEVER name things as 'improved', 'new', 'enhanced', etc.
- **Temporal Independence**: Code naming should be evergreen and not relate to changes that happened over time
- **Descriptive Names**: Use names that describe what the code does, not when it was created or modified
- **Avoid Version References**: Don't include version numbers, dates, or change references in names

## Error Handling and Pre-existing Issues

- **Pre-existing Errors**: If you experience compilation errors that were present before being given a task, stop and ask for guidance
- **Don't Fix Unrelated Issues**: Do not attempt to fix pre-existing problems unless explicitly asked
- **Scope Awareness**: Understand the difference between task-related issues and pre-existing problems
- **Seek Guidance**: When in doubt about whether an issue is related to your task, ask for clarification


