# Error Handling in Controllers

## Overview

Controllers now extract and return clean error messages from Cyoda backend exceptions when returning 400 (Bad Request) responses. This provides meaningful error information to API clients without exposing internal stacktraces.

## Implementation

### CyodaExceptionUtil

A utility class (`com.java_template.common.util.CyodaExceptionUtil`) handles extraction of clean error messages from Cyoda backend exceptions.

**Key Methods:**
- `extractErrorMessage(Throwable exception)` - Extracts the meaningful error message from nested exceptions
- `extractErrorCode(Throwable exception)` - Extracts the error code if present (e.g., "PROCESSING_ERROR")
- `formatErrorResponse(Throwable exception)` - Formats error with code and message

### Exception Structure

Cyoda backend exceptions typically follow this nested structure:

```
CompletionException
  └─ StatusRuntimeException
       └─ Message: "CANCELLED: Transaction ... was cancelled: Sync process[...] failed: 
           Fail with error code [PROCESSING_ERROR] with message 'External error: <actual message>'"
```

The utility extracts just the `<actual message>` portion.

### Example Error Message

**Before (with stacktrace):**
```
java.util.concurrent.CompletionException: io.grpc.StatusRuntimeException: CANCELLED: Transaction c54997d0-a014-11f0-8025-ae468cd3ed18 was cancelled: Sync process[61feaf8a-a00f-11f0-8025-ae468cd3ed18] for TreeNodeEntity[39b9c394-2d51-11b2-8cc0-7a8b886ecb2e] failed: Fail with error code [PROCESSING_ERROR] with message 'External error: Invalid day count basis. Must be ACT/365F, ACT/360, or ACT/365L. Got: 30/360'
Transaction State: success=false secondPhaseDone=true cancelled=true rolledBack=false versionCheckFailed=false
	at com.java_template.common.repository.CyodaRepository.requestAndGetOrThrow(CyodaRepository.java:325) ~[main/:na]
	at com.java_template.common.repository.CyodaRepository.lambda$sendAndGet$9(CyodaRepository.java:280) ~[main/:na]
	...
```

**After (clean message):**
```
Invalid day count basis. Must be ACT/365F, ACT/360, or ACT/365L. Got: 30/360
```

## Controller Pattern

All controllers now follow this pattern for error handling:

```java
@GetMapping("/{id}")
public ResponseEntity<?> getEntityById(@PathVariable UUID id) {
    try {
        EntityWithMetadata<Entity> response = interactor.getEntityById(id);
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        logger.error("Error getting entity by ID: {}", id, e);
        String errorMessage = CyodaExceptionUtil.extractErrorMessage(e);
        return ResponseEntity.badRequest().body(errorMessage);
    }
}
```

**Key Points:**
- Return type is `ResponseEntity<?>` to allow returning either the entity or an error string
- Full exception is still logged for debugging
- Only the clean error message is returned to the client
- HTTP 400 status code indicates a bad request

## Affected Controllers

All controllers in `com.java_template.application.controller` package:
- `LoanController`
- `PaymentController`
- `PartyController`
- `AccrualController`
- `GLBatchController`
- `PaymentFileController`
- `SettlementQuoteController`

## Testing

The `CyodaExceptionUtilTest` class provides test coverage for various exception patterns:
- External error messages
- Messages without "External error:" prefix
- StatusRuntimeException with description
- Plain exceptions
- Null exceptions
- Error code extraction
- Formatted error responses

Run tests with:
```bash
./gradlew test --tests CyodaExceptionUtilTest
```

## Benefits

1. **Better API Experience** - Clients receive actionable error messages instead of Java stacktraces
2. **Security** - Internal implementation details are not exposed to clients
3. **Debugging** - Full exceptions are still logged for troubleshooting
4. **Consistency** - All controllers handle errors the same way
5. **Maintainability** - Centralized error extraction logic in one utility class

