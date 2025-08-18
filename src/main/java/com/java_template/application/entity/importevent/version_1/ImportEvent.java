package com.java_template.application.entity.importevent.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.util.ArrayList;
import java.util.List;

public class ImportEvent implements CyodaEntity {
    private String eventId;
    private String itemTechnicalId;
    private Long itemId;
    private String jobTechnicalId;
    private String timestamp;
    private String status;
    private List<String> errors = new ArrayList<>();
    private Object metadata; // freeform

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getItemTechnicalId() {
        return itemTechnicalId;
    }

    public void setItemTechnicalId(String itemTechnicalId) {
        this.itemTechnicalId = itemTechnicalId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getJobTechnicalId() {
        return jobTechnicalId;
    }

    public void setJobTechnicalId(String jobTechnicalId) {
        this.jobTechnicalId = jobTechnicalId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public Object getMetadata() {
        return metadata;
    }

    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }

    @Override
    public OperationSpecification getModelKey() {
        // Provide a simple model key so this entity satisfies CyodaEntity bounds
        return new OperationSpecification.Entity(new ModelSpec(), "import-event.v1");
    }

    @Override
    public boolean isValid() {
        // Basic validation logic
        return eventId != null;
    }
}
