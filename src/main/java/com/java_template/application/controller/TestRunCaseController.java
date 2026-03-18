package com.java_template.application.controller;

import com.java_template.application.dto.TestRunCaseDTO;
import com.java_template.application.service.TestRunCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Test Run Case operations
 */
@RestController
@RequestMapping("/projects/{projectId}/runs/{runId}/cases")
@Tag(name = "Test Run Cases", description = "Test run case management endpoints")
public class TestRunCaseController {
    private final TestRunCaseService testRunCaseService;

    public TestRunCaseController(TestRunCaseService testRunCaseService) {
        this.testRunCaseService = testRunCaseService;
    }

    @PostMapping
    @Operation(summary = "Create a new test run case")
    public ResponseEntity<TestRunCaseDTO> createTestRunCase(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @RequestBody TestRunCaseDTO testRunCase) {
        testRunCase.setTestRunId(runId);
        TestRunCaseDTO created = testRunCaseService.createTestRunCase(testRunCase);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Get all test run cases for a test run")
    public ResponseEntity<List<TestRunCaseDTO>> getTestRunCasesByRun(
            @PathVariable UUID projectId,
            @PathVariable UUID runId) {
        return ResponseEntity.ok(testRunCaseService.getTestRunCasesByTestRunId(runId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get test run case by ID")
    public ResponseEntity<TestRunCaseDTO> getTestRunCase(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @PathVariable UUID id) {
        return testRunCaseService.getTestRunCaseById(id)
                .filter(trc -> trc.getTestRunId().equals(runId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update test run case status")
    public ResponseEntity<TestRunCaseDTO> updateTestRunCaseStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @PathVariable UUID id,
            @RequestParam String status) {
        return testRunCaseService.updateTestRunCaseStatus(id, status)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

