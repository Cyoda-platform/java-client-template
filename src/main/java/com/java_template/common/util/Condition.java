package com.java_template.common.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * ABOUTME: Data class representing search conditions with JSON path support
 * for building complex query criteria in entity search operations.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Condition {
    private String type;

    // For simple conditions
    private String jsonPath;

    // For lifecycle conditions
    private String field;

    // Map the expected 'operatorType' from the request to 'operation' for Cyoda API
    @JsonProperty("operation")
    private String operatorType;

    private Object value;

    public Condition(String type, String jsonPath, String field, String operatorType, Object value) {
        this.type = type;
        this.jsonPath = jsonPath;
        this.field = field;
        this.operatorType = operatorType;
        this.value = value;
    }

    // For simple conditions
    public static Condition of(String jsonPath, String operatorType, Object value) {
        return new Condition("simple", jsonPath, null, operatorType, value);
    }

    // For lifecycle conditions
    public static Condition lifecycle(String field, String operatorType, Object value) {
        return new Condition("lifecycle", null, field, operatorType, value);
    }
}
