package com.java_template.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Search Request DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequestDTO {
    private String query;
    private Map<String, Object> filters;
    private Integer pageNumber;
    private Integer pageSize;
}

