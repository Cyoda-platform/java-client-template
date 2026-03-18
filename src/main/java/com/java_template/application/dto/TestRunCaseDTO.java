package com.java_template.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/**
 * Test Run Case DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRunCaseDTO {
    private UUID id;
    private UUID runId;
    private UUID caseId;
    private String status;
    private List<TestRunStepDTO> steps;
}

