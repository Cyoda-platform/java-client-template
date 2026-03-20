package com.java_template.application.controller;

import com.java_template.application.service.TestCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Provides DELETE /projects/{projectId}/cases/{caseId} without requiring suiteId,
 * as required by the UI specification.
 */
@RestController
@RequestMapping("/projects/{projectId}/cases")
@Tag(name = "Test Cases", description = "Direct test case access endpoints")
public class TestCaseDirectController {

    private final TestCaseService testCaseService;

    public TestCaseDirectController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @DeleteMapping("/{caseId}")
    @Operation(summary = "Soft-delete a test case by ID (no suiteId required)")
    public ResponseEntity<Void> deleteTestCase(
            @PathVariable UUID projectId,
            @PathVariable UUID caseId) {
        if (testCaseService.deleteTestCase(caseId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

