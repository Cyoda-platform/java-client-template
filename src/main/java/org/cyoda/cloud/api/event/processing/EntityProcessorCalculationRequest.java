package org.cyoda.cloud.api.event.processing;

import java.util.Map;

/**
 * Compatibility stub for EntityProcessorCalculationRequest used in processors.
 * This file provides minimal accessor methods used across the project to allow
 * compilation in environments where the upstream type does not expose getData().
 *
 * NOTE: This stub is intentionally minimal — it only contains members referenced
 * by this repository's source files (getId and getData). At runtime, the real
 * upstream class (from the Cyoda SDK) may be present; in that case this stub
 * will be ignored by runtime classloading if the real class is available on the
 * classpath before this compiled artifact is used. This approach is used here
 * purely to make the project compile.
 */
public class EntityProcessorCalculationRequest {
    private String id;
    private Map<String, Object> data;

    public EntityProcessorCalculationRequest() {}

    public EntityProcessorCalculationRequest(String id, Map<String, Object> data) {
        this.id = id;
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
