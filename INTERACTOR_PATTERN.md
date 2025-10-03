# Interactor Pattern Implementation

## Overview
The application uses the Interactor pattern (also known as Use Case pattern) to separate business logic from HTTP concerns. Controllers are thin adapters that delegate all business logic to interactor classes and handle only HTTP request/response mapping.

## Architecture

### Controllers (HTTP Layer)
Controllers in `src/main/java/com/java_template/application/controller/` handle:
- HTTP request parsing and validation
- Delegating to interactors for business logic
- Mapping interactor results to HTTP responses (ResponseEntity)
- Exception handling and HTTP status code mapping
- OpenAPI documentation annotations

Controllers **do not** contain business logic, entity manipulation, or direct EntityService calls.

### Interactors (Business Logic Layer)
Interactors in `src/main/java/com/java_template/application/interactor/` handle:
- All business logic and validation
- Entity manipulation and state management
- EntityService interactions
- Search query construction
- Workflow transitions
- Business exceptions

## Implemented Interactors

All interactors follow a consistent pattern with standard CRUD operations, duplicate business key validation, and timestamp management. Each interactor defines inner exception classes for domain-specific error handling.

**Entity-Specific Features:**
- **LoanInteractor** - Includes advanced search and workflow transitions (approve, fund)
- **PaymentInteractor** - Includes loan-based search and advanced search capabilities
- **PaymentFileInteractor** - Manages file import timestamps (receivedAt)
- **AccrualInteractor** - Manages scheduling timestamps (scheduledAt)
- **SettlementQuoteInteractor** - Auto-sets quotedDate from asOfDate
- **PartyInteractor** - Standard CRUD operations
- **GLBatchInteractor** - Standard CRUD operations

## Exception Handling Pattern

Interactors throw domain-specific exceptions (`DuplicateEntityException`, `EntityNotFoundException`). Controllers map these to HTTP status codes (409 Conflict, 404 Not Found).

## Search Criteria Pattern

Interactors with advanced search use inner criteria classes. Controllers map request DTOs to criteria objects.

## Dependency Injection

Interactors are Spring `@Component` beans with constructor injection of `EntityService` and `ObjectMapper`. Controllers inject their corresponding interactor via constructor.

## Benefits

Separation of concerns, improved testability, reusability across interfaces, and adherence to clean architecture principles.



## Adding New Interactors

Create interactor as `@Component` with constructor-injected dependencies. Define inner exception and criteria classes as needed. Update controller to delegate all business logic to the interactor.

