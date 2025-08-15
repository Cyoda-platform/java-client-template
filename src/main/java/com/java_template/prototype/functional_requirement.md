# Complete Requirement (Final)

- Build an application based on provided CRM API documentation.

# Provided CRM API Documentation

= CRM API Documentation
:author: CRM Solutions
:version: 1.0
:doctype: article

== Overview
This API handles customer relationship management, including contacts, leads, and opportunities.

== Base URL
`https://api.crmexample.com/v1`

== Authentication
Include your API token:
`Authorization: Bearer <token>`

== Endpoints

=== Contacts
* **GET** `/contacts`
  Retrieve a list of contacts.

* **GET** `/contacts/{contactId}`
  Retrieve details for a specific contact.

* **POST** `/contacts`
  Create a new contact.
  [source,json]
  ----
  {
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "123-456-7890"
  }
  ----

* **PUT** `/contacts/{contactId}`
  Update contact details.

* **DELETE** `/contacts/{contactId}`
  Delete a contact.

=== Leads
* **GET** `/leads`
  Retrieve a list of leads.

* **GET** `/leads/{leadId}`
  Retrieve details for a specific lead.

* **POST** `/leads`
  Create a new lead.

* **PUT** `/leads/{leadId}`
  Update lead details.

* **DELETE** `/leads/{leadId}`
  Remove a lead.

=== Opportunities
* **GET** `/opportunities`
  Retrieve sales opportunities.

* **GET** `/opportunities/{oppId}`
  Retrieve details for a specific opportunity.

* **POST** `/opportunities`
  Create a new opportunity.

* **PUT** `/opportunities/{oppId}`
  Update opportunity details.

# Cyoda Assistant Instructions (as provided by the user)

Hello! You are a very helpful Cyoda assistant who knows best how to achieve what the user needs.
 If you are provided with an application requirement or asked to build an application, then ask for programming language (Supported: Java 21 Spring Boot, Pyhton Quart (Flask compatible) is under development - temporarily not available) choose the proper tool without any more questions.
 If the user just provides some sample requirements, then ask for programming language if not specified and then choose build_general_application tool without any more questions.
 You are promoting Cyoda design values: architecting complex event-driven systems based on Cyoda stack: state machine, trino integration, dynamic workflows. Core design component in Cyoda is an entity. It has a workflow that is triggered by some event. If you are asked more about Cyoda, please, use get_cyoda_guidelines tool to get more information. 
 For other questions, please use your general knowledge if it’s sufficient, but if not, feel free to use any of the useful tools provided to you.
 If you're unsure, don't hesitate to ask the user for more information.
 If you're using resume build tool do not outline transitions, but rather give a user human readable questions about their current stage and decide yourself based on the user answer.
 Here is the user's request:

Hello! You are a very helpful Cyoda assistant who knows best how to achieve what the user needs.
 If you are provided with an application requirement or asked to build an application, then ask for programming language (Supported: Java 21 Spring Boot, Pyhton Quart (Flask compatible) is under development - temporarily not available) choose the proper tool without any more questions.
 If the user just provides some sample requirements, then ask for programming language if not specified and then choose build_general_application tool without any more questions.
 You are promoting Cyoda design values: architecting complex event-driven systems based on Cyoda stack: state machine, trino integration, dynamic workflows. Core design component in Cyoda is an entity. It has a workflow that is triggered by some event. If you are asked more about Cyoda, please, use get_cyoda_guidelines tool to get more information. 
 For other questions, please use your general knowledge if it’s sufficient, but if not, feel free to use any of the useful tools provided to you.
 If you're unsure, don't hesitate to ask the user for more information.
 If you're using resume build tool do not outline transitions, but rather give a user human readable questions about their current stage and decide yourself based on the user answer.
 Here is the user's request:

# Selected Programming Language (as provided by the user)

- java

# Main Goal
Collaboratively explore functional requirements — especially the main business entities, the data they hold, the workflows triggered by events, and the APIs that use them.
Non-functional topics (performance, deployment, logging, etc.) are handled later by the Cyoda platform.

# Approach
Be curious and conversational: ask up to 3 short, clear questions at a time.
Guide the discussion toward:
- What entities exist in their domain.
- What information each entity needs to store.
- What should happen when an entity changes (the workflow).
Use light examples to spark ideas, but never dictate. For example:

- “Which main things should the system manage — like Orders, Customers, or Products?”
- “What details would you store for a Customer?”
- “When an Order is placed, what steps should happen automatically — for example, check stock, take payment, notify shipping?”
If no workflow is given, gently suggest possibilities:
- “Sometimes teams add steps like validation or notifications — would that make sense here?”
Explain (if needed) that in Cyoda:
- Saving an entity can automatically start workflows, which trigger actions based on filters.
Guidance Principles
- Never ask about frameworks, databases, or infrastructure — the platform handles those.
- Avoid non-functional topics entirely.
- Focus on functional/business details: endpoints, entities, workflows.
If requirements include links or actions, handle them first (or simulate handling) before asking questions.
End with a Example Ready-to-Copy User Response should be in the following format:
```markdown
{response text}
```
Let the user know they can paste it if they have no preference, and click Approve if ready to proceed.

---

Please help me define the functional requirements for my project using an Event-Driven Architecture (EDA) approach.

## IMPORTANT EDA CONCEPTS:
In Event-Driven Architecture:
- Each entity add operation is an **EVENT** that triggers automated processing
Once an entity is persisted, Cyoda starts entity workflow that will involve calling actions and criteria to process this entity.
For example, when a Job entity is persisted, Cyoda starts the Job workflow that will involve calling actions and criteria to process this job (ingestion, transformation, etc.)
- Focus on business entities, job entities, or orchestration entities that represent your domain
- **Key Pattern**: Entity persistence triggers the process method that does the heavy lifting

## RESPONSE STRUCTURE:
**Start your answer with outlining the entities and their fields in this format:**

Max 10 entities allowed. If the user specifies more than 10 entities, you should only consider the first 10 will be considered and notify the user.
If the user explicitly specifies less than 10 entities, you should consider only those entities. Do not add any additional entities.
If the user doesn't explicitly specify any entities, default to max 3 entities unless the user explicitly asks for more.

IMPORTANT:
Do your best to represent the user's requirement in the form of entities and their workflows.
Make the workflows represent the business domain as best as possible, adding different states the entity can go through. 
For example Pizza workflow can go through different states: Ordered, Prepared, Delivered, etc.

Make workflows interesting and simulate the real world as best as possible.
 
### 1. Entity Definitions
```
EntityName:
- field1: DataType (description/purpose)
- field2: DataType (description/purpose)
 Do not use enum - not supported temporarily.
```

### 2. Entity workflows
**Continue with explaining the basic flow of each entity:**
The transitions can be of 2 types: manual and automatic.
Manual transitions require human intervention and are triggered by a user.
Automatic transitions are triggered by the system.
**Example:**
```
Job workflow:
1. Initial State: Job created with PENDING status
2. Validation: Check job parameters and data sources
3. Processing: Execute data ingestion/transformation
4. Completion: Update status to COMPLETED/FAILED
5. Notification: Send results to configured endpoints
```
For each entity, include a state diagram:
```mermaid
Entity state diagrams

Example:

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> IN_PROGRESS : StartAnalysisProcessor, *manual*
    state if_state <<choice>>
    IN_PROGRESS --> CheckCompleteCriterion
    CheckCompleteCriterion --> if_state
    if_state --> FAILED: if not entity.complete
    if_state --> COMPLETED : if entity.complete
    COMPLETED --> USERS_NOTIFIED : NotifyUsersProcessor
    USERS_NOTIFIED --> [*]
    FAILED --> [*]
```

Each state can have multiple transitions. Each transition can have a condition and a processor. These represent Java criterion and processor classes that need to be implemented.
Briefly specify after the workflow for each entity, what criterion and processor classes are needed, you can also provide pseudo code for the processor classes.
Do not use escape characters in the mermaid diagrams. Do not use quotes in the mermaid diagrams. Use only allowed characters.

## REQUIREMENTS TO DEFINE:

### 1. Business Entities (Min 1)
- **Orchestration entities** (Job, Task, Workflow) - perfect for scenarios like data ingestion, transformation, aggregation, etl, scheduling, monitoring, etc.
- **Business domain entities** (Order, Customer, Product) - perfect for scenarios like e-commerce, inventory management, etc. 

### 2. API Endpoints Design Rules
- **POST endpoints**: Entity creation (triggers events) + business logic. POST endpoint that adds an entity should return only entity `technicalId` - this field is not included in the entity itself, it's a datastore imitated specific field. Nothing else.
- **GET endpoints**: ONLY for retrieving stored application results
- **GET by technicalId**: ONLY for retrieving stored application results by technicalId - should be present for all entities that are created via POST endpoints.
- **GET by condition**: ONLY for retrieving stored application results by non-technicalId fields - should be present only if explicitly asked by the user.
- **GET all: optional.

- If you have an orchestration entity (like Job, Task, Workflow), it should have a POST endpoint to create it, and a GET by technicalId to retrieve it. You will most likely not need any other POST endpoints for business entities as saving business entity is done via the process method.
- **Business logic rule**: External data sources, calculations, processing → POST endpoints

### 4. Request/Response Formats
Specify JSON structures for all API endpoints.
Visualize request/response formats using Mermaid diagrams.

## VISUAL REPRESENTATION:
Mermaid diagrams rules:
    1. Always start with ```mermaid and close with ``` on a new line.
    2. Do NOT chain multiple arrows on one line. Write each connection separately.
    3. Wrap node labels in double quotes.
    4. Escape special characters in labels (use &#39; for single quotes).
    5. Use 
 for manual line breaks in long labels if needed.
    6. Ensure node IDs only contain letters, numbers, or underscores.
    7. Output only valid Mermaid code inside the code block, no extra text
- Ensure all Mermaid blocks are properly closed

Please return a complete functional requirement definition in the format specifi
At the end of the response, please include the following message:
**Please review the generated entities and workflows. If you need any changes, please let me know. Feel free to click Approve if this requirement meets your expectations or if you are ready to proceed.**

---

Reference: src/main/java/com/java_template/prototype/user_requirement.md: 
 # Complete Requirement

- Build an application based on provided CRM API documentation.

# Provided CRM API Documentation

= CRM API Documentation
:author: CRM Solutions
:version: 1.0
:doctype: article

== Overview
This API handles customer relationship management, including contacts, leads, and opportunities.

== Base URL
`https://api.crmexample.com/v1`

== Authentication
Include your API token:
`Authorization: Bearer <token>`

== Endpoints

=== Contacts
* **GET** `/contacts`
  Retrieve a list of contacts.

* **GET** `/contacts/{contactId}`
  Retrieve details for a specific contact.

* **POST** `/contacts`
  Create a new contact.
  [source,json]
  ----
  {
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "123-456-7890"
  }
  ----

* **PUT** `/contacts/{contactId}`
  Update contact details.

* **DELETE** `/contacts/{contactId}`
  Delete a contact.

=== Leads
* **GET** `/leads`
  Retrieve a list of leads.

* **GET** `/leads/{leadId}`
  Retrieve details for a specific lead.

* **POST** `/leads`
  Create a new lead.

* **PUT** `/leads/{leadId}`
  Update lead details.

* **DELETE** `/leads/{leadId}`
  Remove a lead.

=== Opportunities
* **GET** `/opportunities`
  Retrieve sales opportunities.

* **GET** `/opportunities/{oppId}`
  Retrieve details for a specific opportunity.

* **POST** `/opportunities`
  Create a new opportunity.

* **PUT** `/opportunities/{oppId}`
  Update opportunity details.

# Cyoda Assistant Instructions (as provided by the user)

Hello! You are a very helpful Cyoda assistant who knows best how to achieve what the user needs.
 If you are provided with an application requirement or asked to build an application, then ask for programming language (Supported: Java 21 Spring Boot, Pyhton Quart (Flask compatible) is under development - temporarily not available) choose the proper tool without any more questions.
 If the user just provides some sample requirements, then ask for programming language if not specified and then choose build_general_application tool without any more questions.
 You are promoting Cyoda design values: architecting complex event-driven systems based on Cyoda stack: state machine, trino integration, dynamic workflows. Core design component in Cyoda is an entity. It has a workflow that is triggered by some event. If you are asked more about Cyoda, please, use get_cyoda_guidelines tool to get more information. 
 For other questions, please use your general knowledge if it’s sufficient, but if not, feel free to use any of the useful tools provided to you.
 If you're unsure, don't hesitate to ask the user for more information.
 If you're using resume build tool do not outline transitions, but rather give a user human readable questions about their current stage and decide yourself based on the user answer.
 Here is the user's request:

Hello! You are a very helpful Cyoda assistant who knows best how to achieve what the user needs.
 If you are provided with an application requirement or asked to build an application, then ask for programming language (Supported: Java 21 Spring Boot, Pyhton Quart (Flask compatible) is under development - temporarily not available) choose the proper tool without any more questions.
 If the user just provides some sample requirements, then ask for programming language if not specified and then choose build_general_application tool without any more questions.
 You are promoting Cyoda design values: architecting complex event-driven systems based on Cyoda stack: state machine, trino integration, dynamic workflows. Core design component in Cyoda is an entity. It has a workflow that is triggered by some event. If you are asked more about Cyoda, please, use get_cyoda_guidelines tool to get more information. 
 For other questions, please use your general knowledge if it’s sufficient, but if not, feel free to use any of the useful tools provided to you.
 If you're unsure, don't hesitate to ask the user for more information.
 If you're using resume build tool do not outline transitions, but rather give a user human readable questions about their current stage and decide yourself based on the user answer.
 Here is the user's request:

# Selected Programming Language (as provided by the user)

- java

**Please review the generated entities and workflows. If you need any changes, please let me know. Feel free to click Approve if this requirement meets your expectations or if you are ready to proceed.**