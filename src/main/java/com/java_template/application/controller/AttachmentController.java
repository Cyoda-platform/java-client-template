package com.java_template.application.controller;

import com.java_template.application.dto.AttachmentDTO;
import com.java_template.application.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

/**
 * REST controller for Attachment operations
 */
@RestController
@RequestMapping("/projects/{projectId}/message/upload")
@Tag(name = "Attachments", description = "Attachment upload endpoints")
public class AttachmentController {
    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping
    @Operation(summary = "Upload attachment (proxy to EdgeMessage client)")
    public ResponseEntity<AttachmentDTO> uploadAttachment(@PathVariable UUID projectId, @RequestParam("file") MultipartFile file) {
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
}

