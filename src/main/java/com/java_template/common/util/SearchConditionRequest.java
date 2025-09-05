package com.java_template.common.util;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * ABOUTME: Request wrapper for search conditions supporting grouped logical operations
 * with AND/OR operators for complex entity search queries.
 */
@Getter
@Setter
public class SearchConditionRequest {
    private String type;
    private String operator;
    private List<Condition> conditions;

    public static SearchConditionRequest group(String operator, Condition... conditions) {
        SearchConditionRequest req = new SearchConditionRequest();
        req.setType("group");
        req.setOperator(operator);
        req.setConditions(List.of(conditions));
        return req;
    }
}
