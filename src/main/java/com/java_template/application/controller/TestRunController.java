package com.java_template.application.controller;

import com.java_template.application.dto.TestRunDTO;
import com.java_template.application.service.TestRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for Test Run operations
 */
@RestController
@RequestMapping("/projects/{projectId}/runs")
@Tag(name = "Test Runs", description = "Test run management endpoints")
public class TestRunController {
    private final TestRunService testRunService;

    public TestRunController(TestRunService testRunService) {
        this.testRunService = testRunService;
    }

    @PostMapping
    @Operation(summary = "Create a new test run (snapshot)")
    public ResponseEntity<TestRunDTO> createTestRun(@PathVariable UUID projectId, @RequestBody TestRunDTO testRun) {
        testRun.setProjectId(projectId);
        TestRunDTO created = testRunService.createTestRun(testRun);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{runId}")
    @Operation(summary = "Get test run by ID")
    public ResponseEntity<TestRunDTO> getTestRun(@PathVariable UUID projectId, @PathVariable UUID runId) {
        return testRunService.getTestRunById(runId)
                .filter(tr -> tr.getProjectId().equals(projectId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Get all test runs for a project")
    public ResponseEntity<List<TestRunDTO>> getTestRunsByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(testRunService.getTestRunsByProjectId(projectId));
    }

    @PutMapping("/{runId}")
    @Operation(summary = "Update a test run")
    public ResponseEntity<TestRunDTO> updateTestRun(@PathVariable UUID projectId, @PathVariable UUID runId, @RequestBody TestRunDTO testRun) {
        if (!testRunService.testRunExists(runId)) {
            return ResponseEntity.notFound().build();
        }
        testRun.setProjectId(projectId);
        TestRunDTO updated = testRunService.updateTestRun(runId, testRun);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{runId}/cases/{runCaseId}/steps/{runStepId}/status")
    @Operation(summary = "Update test run step status")
    public ResponseEntity<Void> updateStepStatus(@PathVariable UUID projectId, @PathVariable UUID runId, @PathVariable UUID runCaseId, @PathVariable UUID runStepId, @RequestBody Map<String, String> statusUpdate) {
        // Stub implementation - would update step status in actual implementation
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{runId}")
    @Operation(summary = "Delete a test run")
    public ResponseEntity<Void> deleteTestRun(@PathVariable UUID projectId, @PathVariable UUID runId) {
        if (testRunService.deleteTestRun(runId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

