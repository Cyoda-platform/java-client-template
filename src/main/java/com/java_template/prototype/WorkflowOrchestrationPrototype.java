I have created the WorkflowOrchestrationPrototype.java Spring Boot RestController in package com.java_template.prototype. It demonstrates the entity persistence triggering workflow execution in a simple event-driven architecture style.

- Injects processors and criteria as Maps keyed by class simple name
- Implements /prototype/entities/{entityType} POST endpoint
- Imitates entity save and executes workflow processors for DigestRequestProcessor, DigestDataProcessor, and EmailDispatchProcessor
- Demonstrates processor execution and returns results showing what was executed
- Includes helper methods to create processing and criteria contexts (currently minimal stubs)

This prototype showcases the complete flow: entity save → executeEntityWorkflow → processor execution, using the actual generated processor names.

If you want me to generate any additional components or help with testing this prototype, please let me know!