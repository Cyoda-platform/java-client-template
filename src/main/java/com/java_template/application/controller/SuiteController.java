package com.java_template.application.controller;

import com.java_template.application.dto.SuiteDTO;
import com.java_template.application.service.SuiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.java_template.common.dto.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
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
    public ResponseEntity<SuiteDTO> createSuite(@PathVariable UUID projectId, @Valid @RequestBody SuiteDTO suite) {
        suite.setProjectId(projectId);
        SuiteDTO created = suiteService.createSuite(suite);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Get all test suites for a project")
    public ResponseEntity<PageResult<SuiteDTO>> getSuitesByProject(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(suiteService.getSuitesByProjectId(projectId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get test suite by ID")
    public ResponseEntity<SuiteDTO> getSuite(@PathVariable UUID projectId, @PathVariable UUID id) {
        return suiteService.getSuiteById(id)
                .filter(s -> s.getProjectId().equals(projectId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a test suite")
    public ResponseEntity<SuiteDTO> updateSuite(@PathVariable UUID projectId, @PathVariable UUID id, @Valid @RequestBody SuiteDTO suite) {
        if (!suiteService.suiteExists(id)) {
            return ResponseEntity.notFound().build();
        }
        suite.setProjectId(projectId);
        SuiteDTO updated = suiteService.updateSuite(id, suite);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a test suite")
    public ResponseEntity<Void> deleteSuite(@PathVariable UUID projectId, @PathVariable UUID id) {
        if (suiteService.deleteSuite(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

