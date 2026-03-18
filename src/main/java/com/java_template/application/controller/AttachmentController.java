package com.java_template.application.controller;

import com.java_template.application.dto.AttachmentDTO;
import com.java_template.application.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Attachment operations
 */
@RestController
@RequestMapping("/api/projects/{projectId}/attachments")
@Tag(name = "Attachments", description = "Attachment management endpoints")
public class AttachmentController {
    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping
    @Operation(summary = "Upload attachment")
    public ResponseEntity<AttachmentDTO> uploadAttachment(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file) {
        try {
            AttachmentDTO attachment = new AttachmentDTO();
            attachment.setProjectId(projectId);
            attachment.setFileName(file.getOriginalFilename());
            attachment.setFileType(file.getContentType());
            attachment.setFileSize(file.getSize());

            AttachmentDTO uploaded = attachmentService.uploadAttachment(attachment);
            return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    @Operation(summary = "Get all attachments for a project")
    public ResponseEntity<List<AttachmentDTO>> getAttachmentsByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(attachmentService.getAttachmentsByProjectId(projectId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get attachment by ID")
    public ResponseEntity<AttachmentDTO> getAttachment(
            @PathVariable UUID projectId,
            @PathVariable UUID id) {
        return attachmentService.getAttachmentById(id)
                .filter(a -> a.getProjectId().equals(projectId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an attachment")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable UUID projectId,
            @PathVariable UUID id) {
        if (attachmentService.deleteAttachment(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

