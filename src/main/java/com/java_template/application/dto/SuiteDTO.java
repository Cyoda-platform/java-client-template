package com.java_template.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test Suite DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuiteDTO {
    private UUID id;
    private UUID projectId;
    private String name;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

