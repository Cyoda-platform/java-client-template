package com.java_template.application.controller;

import com.java_template.application.dto.ExportDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST controller for Export operations
 */
@RestController
@RequestMapping("/projects/{projectId}/export")
@Tag(name = "Export", description = "Export endpoints")
public class ExportController {

    @PostMapping
    @Operation(summary = "Export project data in specified format")
    public ResponseEntity<ExportDTO> exportProject(
            @PathVariable UUID projectId,
            @RequestParam String format) {
        // Validate format (JSON, CSV, XLSX, etc.)
        ExportDTO export = new ExportDTO();
        export.setId(UUID.randomUUID());
        export.setFormat(format);
        export.setStatus("PROCESSING");
        export.setCreatedAt(LocalDateTime.now());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(export);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get export status and download URL")
    public ResponseEntity<ExportDTO> getExportStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID id) {
        // Retrieve export status from service
        ExportDTO export = new ExportDTO();
        export.setId(id);
        export.setFormat("JSON");
        export.setStatus("COMPLETED");
        export.setCreatedAt(LocalDateTime.now());
        export.setDownloadUrl("/api/projects/" + projectId + "/export/" + id + "/download");
        
        return ResponseEntity.ok(export);
    }
}

