package org.cyoda.cloud.api.event.processing;

import java.util.Map;

/**
 * Minimal compile-time stub for EntityCriteriaCalculationRequest used by criteria implementations.
 * Provides getId and getData() accessors expected by repository code.
 */
public class EntityCriteriaCalculationRequest {
    private String id;
    private Map<String, Object> data;

    public EntityCriteriaCalculationRequest() {}

    public EntityCriteriaCalculationRequest(String id, Map<String, Object> data) {
        this.id = id;
        this.data = data;
    }

    public String getId() { return id; }
    public Map<String, Object> getData() { return data; }
    public void setId(String id) { this.id = id; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
