package com.java_template.common.util;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

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

    // Convenience overload: default operator (AND) when only conditions are provided
    public static SearchConditionRequest group(Condition... conditions) {
        return group("AND", conditions);
    }
}
