package com.java_template.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * Test Run Step DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRunStepDTO {
    private UUID id;
    private UUID runCaseId;
    private UUID stepId;
    private String status;
    private String actualResult;
}

