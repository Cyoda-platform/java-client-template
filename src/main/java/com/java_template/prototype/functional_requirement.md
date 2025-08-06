# Functional Requirements

## Overview
This document specifies the functional requirements for processing HackerNewsItem entities through a simplified workflow.

## Functional Requirements

1. The system shall accept HackerNewsItem entities with mandatory fields `id` (Long) and `type` (String).
2. Upon receipt, the system shall initiate a workflow starting from the `created` state.
3. The system shall validate the presence of mandatory fields `id` and `type`.
4. If the fields are valid, the system shall assign a valid state to the entity and add an import timestamp.
5. If any mandatory field is missing or invalid, the system shall assign an invalid state.
6. The system shall persist the entity after state assignment.
7. The workflow shall complete after persistence with no further transitions.

## API Adjustments

- The POST endpoint shall accept HackerNewsItem entities.
- The response shall include only the technical id of the persisted entity.
- No additional transitions beyond those defined in the simplified workflow shall be allowed.
