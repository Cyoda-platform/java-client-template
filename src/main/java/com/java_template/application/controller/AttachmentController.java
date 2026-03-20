package com.java_template.application.controller;

import com.java_template.application.dto.AttachmentDTO;
import com.java_template.application.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.java_template.common.dto.PageResult;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for Attachment operations.
 * File content is stored in Cyoda EdgeMessage; metadata is returned via GET.
 */
@RestController
@RequestMapping("/projects/{projectId}/attachments")
@Tag(name = "Attachments", description = "Attachment management endpoints")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Upload attachment file to Cyoda EdgeMessage")
    public ResponseEntity<AttachmentDTO> uploadAttachment(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file) {
        try {
            AttachmentDTO uploaded = attachmentService.uploadAttachment(projectId, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(consumes = "application/json")
    @Operation(summary = "Create attachment metadata only (no file content)")
    public ResponseEntity<AttachmentDTO> createAttachmentMetadata(
            @PathVariable UUID projectId,
            @RequestBody AttachmentDTO attachment) {
        attachment.setProjectId(projectId);
        AttachmentDTO created = attachmentService.uploadAttachment(attachment);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Get all attachments for a project")
    public ResponseEntity<PageResult<AttachmentDTO>> getAttachmentsByProject(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(attachmentService.getAttachmentsByProjectId(projectId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get attachment metadata by ID")
    public ResponseEntity<AttachmentDTO> getAttachment(
            @PathVariable UUID projectId,
            @PathVariable UUID id) {
        return attachmentService.getAttachmentById(id)
                .filter(a -> a.getProjectId().equals(projectId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Download attachment file content from Cyoda EdgeMessage")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable UUID projectId,
            @PathVariable UUID id) {
        try {
            Optional<AttachmentDTO> meta = attachmentService.getAttachmentById(id)
                    .filter(a -> a.getProjectId().equals(projectId));
            if (meta.isEmpty()) return ResponseEntity.notFound().build();

            Optional<byte[]> content = attachmentService.getAttachmentContent(id);
            if (content.isEmpty()) return ResponseEntity.notFound().build();

            AttachmentDTO attachment = meta.get();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + attachment.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(
                            attachment.getFileType() != null ? attachment.getFileType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .body(content.get());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete attachment and its EdgeMessage")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable UUID projectId,
            @PathVariable UUID id) {
        if (attachmentService.deleteAttachment(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

