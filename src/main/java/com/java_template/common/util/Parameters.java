package com.java_template.common.util;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for job parameters to provide convenience accessors used by processors.
 * Extends HashMap so instanceof Map checks remain true.
 */
public class Parameters extends HashMap<String, Object> {
    public Parameters() {
        super();
    }

    @SuppressWarnings("unchecked")
    public List<String> getEndpoints() {
        Object v = this.get("endpoints");
        return v instanceof List ? (List<String>) v : null;
    }

    public ObjectNode getRawData() {
        Object v = this.get("rawData");
        return v instanceof ObjectNode ? (ObjectNode) v : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> getRecipients() {
        Object v = this.get("recipients");
        return v instanceof List ? (List<String>) v : null;
    }
}
