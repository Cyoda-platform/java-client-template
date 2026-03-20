package com.java_template.application.controller;

import com.java_template.application.dto.AttachmentDTO;
import com.java_template.application.dto.TestRunStepDTO;
import com.java_template.application.service.AttachmentService;
import com.java_template.application.service.TestRunStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.java_template.common.dto.PageResult;
import java.util.UUID;

/**
 * REST controller for Test Run Step operations
 */
@RestController
@RequestMapping("/projects/{projectId}/runs/{runId}/cases/{caseId}/steps")
@Tag(name = "Test Run Steps", description = "Test run step management endpoints")
public class TestRunStepController {
    private final TestRunStepService testRunStepService;
    private final AttachmentService attachmentService;

    public TestRunStepController(TestRunStepService testRunStepService, AttachmentService attachmentService) {
        this.testRunStepService = testRunStepService;
        this.attachmentService = attachmentService;
    }

    @PostMapping
    @Operation(summary = "Create a new test run step")
    public ResponseEntity<TestRunStepDTO> createTestRunStep(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @PathVariable UUID caseId,
            @Valid @RequestBody TestRunStepDTO testRunStep) {
        testRunStep.setTestRunCaseId(caseId);
        TestRunStepDTO created = testRunStepService.createTestRunStep(testRunStep);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Get all test run steps for a test run case")
    public ResponseEntity<PageResult<TestRunStepDTO>> getTestRunStepsByCase(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @PathVariable UUID caseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(testRunStepService.getTestRunStepsByTestRunCaseId(caseId, page, size));
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

    @PostMapping(value = "/{id}/evidence", consumes = "multipart/form-data")
    @Operation(summary = "Attach evidence (screenshot/log) to a test run step")
    public ResponseEntity<AttachmentDTO> uploadEvidence(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @PathVariable UUID caseId,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        try {
            AttachmentDTO uploaded = attachmentService.uploadAttachment(projectId, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

