package com.java_template.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * Test Step DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestStepDTO {
    private UUID id;
    private UUID testCaseId;
    private Integer stepNumber;
    private String action;
    private String expectedResult;
    private String status;
}

