package com.java_template.application.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
public class SnapshotJobRequest {
    
    @NotBlank(message = "dateRangeStart is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "dateRangeStart must be in ISO date format (YYYY-MM-DD)")
    private String dateRangeStart; // ISO date, start of snapshot capture period
    
    @NotBlank(message = "dateRangeEnd is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "dateRangeEnd must be in ISO date format (YYYY-MM-DD)")
    private String dateRangeEnd; // ISO date, end of snapshot capture period

    public SnapshotJobRequest() {}
}
