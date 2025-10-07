# Actionable Step: Create Dashboard Controller and REST Endpoint

**Objective:** Implement the REST controller that exposes the dashboard summary endpoint following existing controller patterns.

**Prerequisites:** 
- Actionable Step 4 (Implement Caching Layer) must be completed

**Action Items:**
1. Create DashboardController class in `src/main/java/com/java_template/application/controller/DashboardController.java`:
   - Add @RestController annotation
   - Add @RequestMapping("/ui/dashboard") annotation
   - Add @CrossOrigin(origins = "*") annotation for CORS support
   - Add class-level JavaDoc explaining the controller's purpose
2. Add dependency injection:
   - Inject DashboardService via constructor injection
   - Add final modifier to service field
   - Use constructor-based injection (no @Autowired needed with single constructor)
3. Add SLF4J Logger instance:
   - Declare private static final Logger logger
   - Initialize with LoggerFactory.getLogger(DashboardController.class)
4. Implement GET /ui/dashboard/summary endpoint:
   - Add @GetMapping("/summary") annotation
   - Method signature: `public ResponseEntity<?> getDashboardSummary()`
   - Add JavaDoc with endpoint description, HTTP method, path, and response format
5. Implement endpoint logic:
   - Wrap service call in try-catch block
   - Call dashboardService.getDashboardSummary()
   - Return ResponseEntity.ok(summary) on success
   - Log successful request at INFO level with summary statistics
6. Implement error handling following existing patterns:
   - Catch Exception in catch block
   - Log error with logger.error() including exception details
   - Create ProblemDetail using ProblemDetail.forStatusAndDetail()
   - Use HttpStatus.INTERNAL_SERVER_ERROR for status
   - Format error message: "Failed to retrieve dashboard summary: {exception message}"
   - Return ResponseEntity.of(problemDetail).build()
7. Add additional error handling for service unavailability:
   - Check if dashboardService is null (defensive programming)
   - Return HttpStatus.SERVICE_UNAVAILABLE if service is unavailable
   - Log service unavailability at ERROR level
8. Add endpoint documentation in JavaDoc:
   - Document response structure with example JSON
   - Document possible HTTP status codes (200 OK, 500 Internal Server Error, 503 Service Unavailable)
   - Document that the endpoint uses caching with 5-minute TTL
   - Note that no authentication is required (per SecurityConfig)
9. Follow existing controller patterns:
   - Match error handling style from LoanController, PaymentController, PartyController
   - Use same ProblemDetail pattern for error responses
   - Use same logging patterns (INFO for success, ERROR for failures)
   - Follow same ResponseEntity usage patterns
10. Ensure CORS configuration:
    - Verify @CrossOrigin annotation allows frontend access
    - Match CORS settings from other controllers

**Acceptance Criteria:**
- DashboardController class is created with proper annotations (@RestController, @RequestMapping, @CrossOrigin)
- DashboardService is injected via constructor injection
- Logger is properly initialized
- GET /ui/dashboard/summary endpoint is implemented with @GetMapping annotation
- Endpoint returns ResponseEntity<DashboardSummaryDTO> on success with 200 OK status
- Error handling uses ProblemDetail pattern consistent with other controllers
- Errors return 500 Internal Server Error with descriptive messages
- Service unavailability returns 503 Service Unavailable
- Logging is implemented for both success and error cases
- JavaDoc documentation is complete with endpoint details and response format
- Code follows existing controller patterns from LoanController, PaymentController, etc.
- CORS is properly configured for frontend access

