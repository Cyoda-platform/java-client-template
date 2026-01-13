# Contributing to Java Client Template

Welcome to the Java Client Template project! This guide will help you contribute effectively to the codebase and maintain the quality of our examples and documentation.

## 🎯 Project Philosophy

This template follows a **"Static Knowledge"** approach where all examples and documentation should be:
- **Compilable**: Examples must be valid Java code that compiles successfully
- **Current**: Examples should reflect the actual codebase state
- **Testable**: Contributors can validate changes by compiling examples
- **Practical**: Examples should be ready-to-use templates

## 📁 Project Structure

```
java-client-template/
├── src/main/java/com/java_template/
│   ├── common/                          # Framework code - DO NOT MODIFY
│   └── application/                     # Your business logic (usually empty in template)
      ├── controller/                    # REST endpoints (usually empty in template)
      ├── entity/                        # Domain entities (usually empty in template)
      ├── processor/                     # Workflow processors (usually empty in template)
      └── criterion/                     # Workflow criteria (usually empty in template)
│   └── resources/                       # Configuration files (usually empty in template)
│       └── workflow/                    # Workflow configurations (usually empty in template)
├── src/test/java/com/example/           # Compilable examples and templates
│   ├── application/                     # Example implementations (.java.txt files)
│   │   ├── controller/                  # REST controller examples
│   │   ├── entity/                      # Entity implementation examples
│   │   ├── processor/                   # Processor implementation examples
│   │   └── criterion/                   # Criterion implementation examples
│   ├── test/resources/example/config/workflow/                 # Workflow configuration examples
└── CONTRIBUTING.md                      # This file
```

## 🔄 Example Validation Workflow
If you'd like to contribute to the examples, please follow the workflow below. 


1. Add relevant examples to `src/test/java/com/example/` directory
2. Add relevant configurations to `src/test/resources/example/config/` directory
3. Write unit tests for your examples in `src/test/java/com/example/tests` directory
4. Update doc files:
- `llms.txt` - AI-friendly documentation references
- `llms-full.txt` - AI-friendly documentation references with line breaks
- `README.md` - Project documentation
- `usage-rules.md` - Developer and AI agent guidelines
- `.augment-guidelines` - Project overview and development workflow

4. Please, submit a pull request with your changes.

You are most welcome to submit a pull request even if you are not sure if your changes are correct.
We will review your changes and provide feedback.

## 🛠️ Contributing Guidelines


### 1. Adding New Examples

To add a new example component:

1. **Create the file** in the appropriate `llm_example/code/application/` subdirectory
2. **Use `.java.txt` extension** (e.g., `MyNewProcessor.java.txt`)
3. **Follow existing patterns** from other examples
4. **Include comprehensive documentation** in comments
5. **Add to patterns guide** if introducing new concepts

### 3. Updating Framework Code

When making changes to `src/main/java/com/java_template/common/`:

1. **Update examples** to reflect any API changes
3. **Update documentation** if interfaces change
4. **Test with real application code** if possible

### 4. Documentation Updates

When updating documentation:

1. **Keep README.md concise** - detailed info goes in `llm_example/`
2. **Update all references** to directory structures
3. **Ensure consistency** across all documentation files
4. **Validate examples** still match documentation

## ✅ Quality Checklist

Before submitting changes, ensure:

### Code Quality
- [ ] All examples compile successfully
- [ ] Proper package declarations match directory structure
- [ ] All necessary imports are included
- [ ] `@Component` annotations are present on processors and criteria
- [ ] Code follows established patterns from existing examples

### Documentation Quality
- [ ] Critical limitations are clearly documented

## 🚀 Development Workflow

### For Contributors

1. **Fork** the repository
2. **Create a feature branch** (`git checkout -b feature/my-improvement`)
3. **Make your changes** to examples or documentation
5. **Fix any issues** identified by validation
6. **Commit changes** with descriptive messages
7. **Push to your fork** and create a pull request

### For Maintainers

1. **Review pull requests** for code quality and consistency
2. **Run validation script** on all changes
3. **Test examples** in real scenarios when possible
4. **Ensure documentation** is updated appropriately
5. **Merge** only after validation passes

## 🔧 Troubleshooting

### Common Issues

**Compilation Errors:**
- Check package declarations match directory structure
- Ensure all imports are present and correct
- Verify `@Component` annotations on workflow components

**Missing Dependencies:**
- Run `./gradlew build` to generate required classes
- Check that all framework dependencies are available

**File Not Found:**
- Ensure `.java.txt` files are in correct directories
- Verify directory structure matches package names

### Getting Help

1. **Check existing examples** for similar patterns
2. **Review patterns guide** in `llm_example/code/patterns/`
4. **Create an issue** if you find bugs or inconsistencies

## 📋 Example Contribution Checklist

When contributing a new example:

- [ ] File placed in correct `llm_example/` subdirectory
- [ ] File named with `.java.txt` extension
- [ ] Package declaration matches directory structure
- [ ] All imports included and correct
- [ ] Comprehensive comments explaining patterns
- [ ] Both positive and negative examples where applicable
- [ ] `@Component` annotation present (for processors/criteria)
- [ ] Documentation updated if needed
- [ ] Patterns guide updated for new concepts

## 🎉 Thank You!

Your contributions help make this template better for everyone. By following these guidelines, you ensure that examples remain current, compilable, and useful for all developers using this template.