package com.java_template.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test Case DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseDTO {
    private UUID id;
    private UUID projectId;
    private UUID suiteId;
    @JsonProperty("title")
    private String name;
    private String description;
    private String status;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

