# Actionable Step: Remove Existing Accrual, GLBatch, and GLLine Entities and Related Components

**Objective:** Remove all existing Accrual, GLBatch, and GLLine entities, their workflow configurations, criteria, processors, controllers, and tests from the system to prepare for the new implementation.

**Prerequisites:** None.

**Action Items:**
1. Identify all files related to Accrual entity (entity class, processors, criteria, controllers, tests)
2. Identify all files related to GLBatch entity (entity class, processors, criteria, controllers, tests)
3. Identify all files related to GLLine entity (entity class, processors, criteria, controllers, tests)
4. Locate and identify workflow JSON configuration files for Accrual, GLBatch, and GLLine
5. Remove Accrual entity class from `src/main/java/com/java_template/application/entity/`
6. Remove GLBatch entity class from `src/main/java/com/java_template/application/entity/`
7. Remove GLLine entity class from `src/main/java/com/java_template/application/entity/`
8. Remove all Accrual-related processors from `src/main/java/com/java_template/application/processor/`
9. Remove all GLBatch-related processors from `src/main/java/com/java_template/application/processor/`
10. Remove all GLLine-related processors from `src/main/java/com/java_template/application/processor/`
11. Remove all Accrual-related criteria from `src/main/java/com/java_template/application/criterion/`
12. Remove all GLBatch-related criteria from `src/main/java/com/java_template/application/criterion/`
13. Remove all GLLine-related criteria from `src/main/java/com/java_template/application/criterion/`
14. Remove all Accrual-related controllers from `src/main/java/com/java_template/application/controller/`
15. Remove all GLBatch-related controllers from `src/main/java/com/java_template/application/controller/`
16. Remove all GLLine-related controllers from `src/main/java/com/java_template/application/controller/`
17. Remove workflow JSON files for Accrual from `src/main/resources/workflow/` or `application/resources/workflow/`
18. Remove workflow JSON files for GLBatch from `src/main/resources/workflow/` or `application/resources/workflow/`
19. Remove workflow JSON files for GLLine from `src/main/resources/workflow/` or `application/resources/workflow/`
20. Remove all test files related to Accrual from `src/test/java/`
21. Remove all test files related to GLBatch from `src/test/java/`
22. Remove all test files related to GLLine from `src/test/java/`
23. Search for any remaining references to Accrual, GLBatch, or GLLine in the codebase
24. Remove or update any remaining references found
25. Run `./gradlew build` to verify no compilation errors after removal
26. Verify that the application starts successfully after removal

**Acceptance Criteria:**
- All Accrual, GLBatch, and GLLine entity classes are removed
- All related processors, criteria, and controllers are removed
- All workflow JSON configurations are removed
- All related test files are removed
- No compilation errors exist after removal
- Application starts successfully
- No references to the old entities remain in the codebase

