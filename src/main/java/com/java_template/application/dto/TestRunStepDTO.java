package com.java_template.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test Run Step DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRunStepDTO {
    private UUID id;
    private UUID testRunCaseId;
    private UUID testStepId;
    private String status;
    private String actualResult;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}

