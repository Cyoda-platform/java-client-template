package com.java_template.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bug Link DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BugLinkDTO {
    private UUID id;
    private UUID testRunStepId;
    private String bugUrl;
    private String bugId;
    private String description;
    private LocalDateTime createdAt;
}

