# Actionable Step: Implement REST API Controllers and Endpoints

**Objective:** Create REST API controllers for Accrual and EODAccrualBatch entities following the API interaction model in section 7.

**Prerequisites:**
- Actionable Step 2 (Create New Accrual Domain Entity with Embedded Journal Entries) must be completed.
- Actionable Step 5 (Create EODAccrualBatch Domain Entity and Data Model) must be completed.
- Actionable Step 8 (Configure Accrual Workflow JSON) must be completed.
- Actionable Step 9 (Configure EODAccrualBatch Workflow JSON) must be completed.

**Action Items:**
1. Review API interaction model in section 7 of cyoda-eod-accrual-workflows.md
2. Review example controller implementations in `llm_example/code/application/controller/`
3. Create `TransitionRequest.java` DTO class with fields: name (String), comment (String)
4. Create `EngineOptions.java` DTO class with fields: simulate (boolean), maxSteps (int)
5. Create `AccrualController.java` in `src/main/java/com/java_template/application/controller/`
6. Add @RestController and @RequestMapping("/accruals") annotations
7. Inject EntityService dependency via constructor
8. Implement POST /accruals endpoint for creating accruals
9. Accept request body with accrual data, optional transitionRequest, and optional engineOptions
10. Use EntityService to create the accrual entity
11. If transitionRequest is provided, trigger the specified transition
12. Return created accrual with metadata (EntityWithMetadata<Accrual>)
13. Implement GET /accruals/{accrualId} endpoint for fetching accrual by ID
14. Use EntityService to retrieve accrual by accrualId
15. Return accrual with metadata
16. Implement PATCH /accruals/{accrualId} endpoint for updating accruals
17. Accept request body with partial accrual data, optional transitionRequest, and optional engineOptions
18. Use EntityService to update the accrual entity
19. If transitionRequest is provided, trigger the specified transition
20. Return updated accrual with metadata
21. Implement GET /accruals endpoint for querying accruals
22. Accept query parameters for filtering (loanId, asOfDate, state, runId)
23. Use EntityService search capabilities to find matching accruals
24. Return list of accruals with metadata
25. Create `EODAccrualBatchController.java` in `src/main/java/com/java_template/application/controller/`
26. Add @RestController and @RequestMapping("/eod-batches") annotations
27. Inject EntityService dependency via constructor
28. Implement POST /eod-batches endpoint for creating batches
29. Accept request body with batch data, optional transitionRequest, and optional engineOptions
30. Use EntityService to create the batch entity
31. If transitionRequest is provided (e.g., "START"), trigger the specified transition
32. Return created batch with metadata (EntityWithMetadata<EODAccrualBatch>)
33. Implement GET /eod-batches/{batchId} endpoint for fetching batch by ID
34. Use EntityService to retrieve batch by batchId
35. Return batch with metadata including current state and metrics
36. Implement PATCH /eod-batches/{batchId} endpoint for updating batches
37. Accept request body with partial batch data, optional transitionRequest, and optional engineOptions
38. Use EntityService to update the batch entity
39. If transitionRequest is provided, trigger the specified transition
40. Return updated batch with metadata
41. Implement GET /eod-batches endpoint for querying batches
42. Accept query parameters for filtering (asOfDate, mode, state)
43. Use EntityService search capabilities to find matching batches
44. Return list of batches with metadata
45. Add proper error handling for all endpoints (404 for not found, 400 for validation errors)
46. Add proper HTTP status codes (201 for created, 200 for success)
47. Add request/response logging
48. Create integration test class `AccrualControllerTest.java`
49. Write test for POST /accruals creating a new accrual
50. Write test for POST /accruals with transition request
51. Write test for GET /accruals/{accrualId}
52. Write test for PATCH /accruals/{accrualId}
53. Write test for GET /accruals with query parameters
54. Create integration test class `EODAccrualBatchControllerTest.java`
55. Write test for POST /eod-batches creating a new batch
56. Write test for POST /eod-batches with START transition (example from section 7.1)
57. Write test for GET /eod-batches/{batchId}
58. Write test for PATCH /eod-batches/{batchId}
59. Write test for GET /eod-batches with query parameters
60. Run `./gradlew test` to verify all controller tests pass
61. Run `./gradlew bootRun` to start application and manually test endpoints
62. Verify endpoints work as expected with sample requests

**Acceptance Criteria:**
- AccrualController exists with all CRUD endpoints
- EODAccrualBatchController exists with all CRUD endpoints
- POST endpoints support optional transitionRequest and engineOptions
- All endpoints use EntityService for entity operations
- Proper HTTP status codes are returned
- Error handling is implemented for common scenarios
- Integration tests exist and pass for all endpoints
- Application starts successfully and endpoints are accessible
- Example request from section 7.1 works correctly

