package com.java_template.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("description")
    private String action;
    private String expectedResult;
    private String status;
}

