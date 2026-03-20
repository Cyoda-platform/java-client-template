package com.java_template.application.controller;

import com.java_template.application.dto.TestRunStepDTO;
import com.java_template.application.service.TestRunStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Test Run Step operations
 */
@RestController
@RequestMapping("/projects/{projectId}/runs/{runId}/cases/{caseId}/steps")
@Tag(name = "Test Run Steps", description = "Test run step management endpoints")
public class TestRunStepController {
    private final TestRunStepService testRunStepService;

    public TestRunStepController(TestRunStepService testRunStepService) {
        this.testRunStepService = testRunStepService;
    }

    @PostMapping
    @Operation(summary = "Create a new test run step")
    public ResponseEntity<TestRunStepDTO> createTestRunStep(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @PathVariable UUID caseId,
            @RequestBody TestRunStepDTO testRunStep) {
        testRunStep.setTestRunCaseId(caseId);
        TestRunStepDTO created = testRunStepService.createTestRunStep(testRunStep);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Get all test run steps for a test run case")
    public ResponseEntity<List<TestRunStepDTO>> getTestRunStepsByCase(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @PathVariable UUID caseId) {
        return ResponseEntity.ok(testRunStepService.getTestRunStepsByTestRunCaseId(caseId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get test run step by ID")
    public ResponseEntity<TestRunStepDTO> getTestRunStep(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @PathVariable UUID caseId,
            @PathVariable UUID id) {
        return testRunStepService.getTestRunStepById(id)
                .filter(trs -> trs.getTestRunCaseId().equals(caseId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update test run step")
    public ResponseEntity<TestRunStepDTO> updateTestRunStep(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @PathVariable UUID caseId,
            @PathVariable UUID id,
            @RequestBody TestRunStepDTO testRunStep) {
        return testRunStepService.updateTestRunStepStatus(id, testRunStep.getStatus())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update test run step status")
    public ResponseEntity<TestRunStepDTO> updateTestRunStepStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @PathVariable UUID caseId,
            @PathVariable UUID id,
            @RequestParam String status) {
        return testRunStepService.updateTestRunStepStatus(id, status)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/link-bug")
    @Operation(summary = "Link a bug to a test run step")
    public ResponseEntity<TestRunStepDTO> linkBug(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @PathVariable UUID caseId,
            @PathVariable UUID id,
            @RequestParam String bugUrl) {
        return testRunStepService.linkBug(id, bugUrl)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

