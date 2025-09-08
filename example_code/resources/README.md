# Example Code Resources

This directory contains configuration templates and examples for the Java Client Template project.

## Directory Structure

### `workflow/`
Contains workflow configuration examples and templates:

- **`template_workflow.json`** - Complete workflow FSM example with states, transitions, processors, and criteria
- **`criterion_examples.json`** - Comprehensive criterion configuration examples (simple, group, function types)
- **`processor_examples.json`** - Processor configuration examples with execution modes and options

## Usage Guidelines

### For Developers
1. **Reference Before Implementation**: Always check these examples before creating new workflow configurations
2. **Copy and Modify**: Use these templates as starting points for your own workflow definitions
3. **Follow Patterns**: Maintain consistency with the established patterns shown in these examples

### For AI Agents
1. **Pattern Recognition**: Use these examples to understand the expected structure and format
2. **Template Usage**: Reference these templates when generating new workflow configurations
3. **Validation**: Compare generated configurations against these examples for correctness

## Configuration Best Practices

### Workflow Structure
- Use meaningful state and transition names
- Avoid cyclic FSM states
- Include descriptive metadata (version, name, desc)
- Set appropriate initial state

### Processor Configuration
- Choose appropriate execution modes based on requirements
- Set reasonable timeout values
- Use descriptive calculation node tags
- Configure retry policies for error handling

### Criterion Configuration
- Use jsonPath with `$.` prefix for custom entity fields
- Use no prefix for built-in meta-fields (state, previousTransition, etc.)
- Leverage group criteria for complex logical conditions
- Implement function criteria as CyodaCriterion components

## File Formats

All configuration files use JSON format with:
- Consistent indentation (2 spaces)
- Descriptive field names
- Comprehensive examples
- Documentation comments where applicable

## Integration

These configuration examples integrate with:
- `WorkflowImportTool` for importing workflow definitions
- `CyodaProcessor` implementations for processor execution
- `CyodaCriterion` implementations for criterion evaluation
- Entity workflow state management system
