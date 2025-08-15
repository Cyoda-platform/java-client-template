package com.java_template.application.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "SearchRequest", description = "Simple search request with field, operator and value")
public class SearchRequest {
    @Schema(description = "JSON field to filter on (dot notation)")
    private String field;

    @Schema(description = "Operator for comparison (e.g. EQ, NE, LT, GT)")
    private String operator;

    @Schema(description = "Value to compare against")
    private String value;
}