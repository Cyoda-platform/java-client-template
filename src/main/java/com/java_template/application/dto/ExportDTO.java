package com.java_template.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Export DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportDTO {
    private UUID id;
    private String format;
    private String status;
    private LocalDateTime createdAt;
    private String downloadUrl;
}

