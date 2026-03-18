package com.java_template.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Test Run DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRunDTO {
    private UUID id;
    private UUID projectId;
    private String name;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private List<TestRunCaseDTO> cases;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

