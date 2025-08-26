package com.java_template.common.util;

import java.util.List;

public class SearchConditionRequest {
    private String type;
    private String operator;
    private List<Condition> conditions;

    public SearchConditionRequest() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public static SearchConditionRequest group(String operator, Condition... conditions) {
        SearchConditionRequest req = new SearchConditionRequest();
        req.setType("group");
        req.setOperator(operator);
        req.setConditions(List.of(conditions));
        return req;
    }
}