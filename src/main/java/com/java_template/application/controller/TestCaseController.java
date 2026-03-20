package com.java_template.application.controller;

import com.java_template.application.dto.TestCaseDTO;
import com.java_template.application.service.TestCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.java_template.common.dto.PageResult;
import java.util.UUID;

/**
 * REST controller for Test Case operations
 */
@RestController
@RequestMapping("/projects/{projectId}/suites/{suiteId}/cases")
@Tag(name = "Test Cases", description = "Test case management endpoints")
public class TestCaseController {
    private final TestCaseService testCaseService;

    public TestCaseController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @PostMapping
    @Operation(summary = "Create a new test case")
    public ResponseEntity<TestCaseDTO> createTestCase(@PathVariable UUID projectId, @PathVariable UUID suiteId, @Valid @RequestBody TestCaseDTO testCase) {
        testCase.setSuiteId(suiteId);
        TestCaseDTO created = testCaseService.createTestCase(testCase);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Get all test cases for a suite")
    public ResponseEntity<PageResult<TestCaseDTO>> getTestCasesBySuite(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(testCaseService.getTestCasesBySuiteId(suiteId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get test case by ID")
    public ResponseEntity<TestCaseDTO> getTestCase(@PathVariable UUID projectId, @PathVariable UUID suiteId, @PathVariable UUID id) {
        return testCaseService.getTestCaseById(id)
                .filter(tc -> tc.getSuiteId().equals(suiteId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a test case")
    public ResponseEntity<TestCaseDTO> updateTestCase(@PathVariable UUID projectId, @PathVariable UUID suiteId, @PathVariable UUID id, @Valid @RequestBody TestCaseDTO testCase) {
        if (!testCaseService.testCaseExists(id)) {
            return ResponseEntity.notFound().build();
        }
        testCase.setSuiteId(suiteId);
        TestCaseDTO updated = testCaseService.updateTestCase(id, testCase);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete a test case")
    public ResponseEntity<Void> deleteTestCase(@PathVariable UUID projectId, @PathVariable UUID suiteId, @PathVariable UUID id) {
        if (testCaseService.softDeleteTestCase(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

