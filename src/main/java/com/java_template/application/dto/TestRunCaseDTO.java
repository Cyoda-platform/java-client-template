package com.java_template.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test Run Case DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRunCaseDTO {
    private UUID id;
    private UUID testRunId;
    private UUID testCaseId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}

