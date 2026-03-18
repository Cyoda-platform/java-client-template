package com.java_template.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Attachment DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDTO {
    private UUID id;
    private UUID projectId;
    private String fileName;
    private String fileType;
    private long fileSize;
    private LocalDateTime uploadedAt;
}

