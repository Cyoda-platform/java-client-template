package com.java_template.application.controller;

import com.java_template.application.dto.SuiteDTO;
import com.java_template.application.service.SuiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Test Suite operations
 */
@RestController
@RequestMapping("/projects/{projectId}/suites")
@Tag(name = "Test Suites", description = "Test suite management endpoints")
public class SuiteController {
    private final SuiteService suiteService;

    public SuiteController(SuiteService suiteService) {
        this.suiteService = suiteService;
    }

    @PostMapping
    @Operation(summary = "Create a new test suite")
    public ResponseEntity<SuiteDTO> createSuite(@PathVariable UUID projectId, @RequestBody SuiteDTO suite) {
        suite.setProjectId(projectId);
        SuiteDTO created = suiteService.createSuite(suite);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{suiteId}")
    @Operation(summary = "Get test suite by ID")
    public ResponseEntity<SuiteDTO> getSuite(@PathVariable UUID projectId, @PathVariable UUID suiteId) {
        return suiteService.getSuiteById(suiteId)
                .filter(s -> s.getProjectId().equals(projectId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Get all test suites for a project")
    public ResponseEntity<List<SuiteDTO>> getSuitesByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(suiteService.getSuitesByProjectId(projectId));
    }

    @PutMapping("/{suiteId}")
    @Operation(summary = "Update a test suite")
    public ResponseEntity<SuiteDTO> updateSuite(@PathVariable UUID projectId, @PathVariable UUID suiteId, @RequestBody SuiteDTO suite) {
        if (!suiteService.suiteExists(suiteId)) {
            return ResponseEntity.notFound().build();
        }
        suite.setProjectId(projectId);
        SuiteDTO updated = suiteService.updateSuite(suiteId, suite);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{suiteId}")
    @Operation(summary = "Delete a test suite")
    public ResponseEntity<Void> deleteSuite(@PathVariable UUID projectId, @PathVariable UUID suiteId) {
        if (suiteService.deleteSuite(suiteId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

