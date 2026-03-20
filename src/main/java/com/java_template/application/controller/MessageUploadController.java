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
 * Handles file uploads at POST /projects/{projectId}/message/upload,
 * as required by the UI specification (§6 API table).
 */
@RestController
@RequestMapping("/projects/{projectId}/message")
@Tag(name = "Attachments", description = "File upload via EdgeMessage")
public class MessageUploadController {

    private final AttachmentService attachmentService;

    public MessageUploadController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Operation(summary = "Upload a file to the project (EdgeMessage)")
    public ResponseEntity<AttachmentDTO> upload(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file) {
        try {
            AttachmentDTO uploaded = attachmentService.uploadAttachment(projectId, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

