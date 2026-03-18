package com.java_template.application.controller;

import com.java_template.application.dto.TestStepDTO;
import com.java_template.application.service.TestStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Test Step operations
 */
@RestController
@RequestMapping("/projects/{projectId}/suites/{suiteId}/cases/{caseId}/steps")
@Tag(name = "Test Steps", description = "Test step management endpoints")
public class TestStepController {
    private final TestStepService testStepService;

    public TestStepController(TestStepService testStepService) {
        this.testStepService = testStepService;
    }

    @PostMapping
    @Operation(summary = "Create a new test step")
    public ResponseEntity<TestStepDTO> createTestStep(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            @PathVariable UUID caseId,
            @RequestBody TestStepDTO testStep) {
        testStep.setTestCaseId(caseId);
        TestStepDTO created = testStepService.createTestStep(testStep);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Get all test steps for a test case")
    public ResponseEntity<List<TestStepDTO>> getTestStepsByCase(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            @PathVariable UUID caseId) {
        return ResponseEntity.ok(testStepService.getTestStepsByTestCaseId(caseId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get test step by ID")
    public ResponseEntity<TestStepDTO> getTestStep(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            @PathVariable UUID caseId,
            @PathVariable UUID id) {
        return testStepService.getTestStepById(id)
                .filter(ts -> ts.getTestCaseId().equals(caseId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a test step")
    public ResponseEntity<TestStepDTO> updateTestStep(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            @PathVariable UUID caseId,
            @PathVariable UUID id,
            @RequestBody TestStepDTO testStep) {
        testStep.setTestCaseId(caseId);
        TestStepDTO updated = testStepService.updateTestStep(id, testStep);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a test step")
    public ResponseEntity<Void> deleteTestStep(
            @PathVariable UUID projectId,
            @PathVariable UUID suiteId,
            @PathVariable UUID caseId,
            @PathVariable UUID id) {
        if (testStepService.deleteTestStep(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

